package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CertSha256Request
import com.v2ray.ang.dto.CertSha256Result
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object CertificateFingerprintManager {
    private const val TIMEOUT_MS = 5000L

    fun fetchForManualFill(profile: ProfileItem): String? {
        val request = buildRequest(profile) ?: return null
        // alpn can be "h2,http/1.1" so need to split by parts
        val alpn = profile.alpn.orEmpty().split(",").map { it.trim() }
        val useQuic = profile.configType == EConfigType.HYSTERIA2 ||
            (profile.configType == EConfigType.MASQUE && ("h2" !in alpn || "h3" in alpn))

        if (CoreNativeManager.isNativeCoreAvailable()) {
            val result = fetchNative(useQuic, request)
            val sha = result
                ?.takeIf { it.error.isBlank() }
                ?.sha256
                ?.takeIf { it.isNotBlank() }
            if (sha != null) return sha
        }

        if (useQuic) {
            LogUtil.w(AppConfig.TAG, "QUIC certificate fingerprint fetching requires native libv2ray core")
            return null
        }

        return fetchTlsViaJavaSsl(request)
    }

    private fun buildRequest(profile: ProfileItem): CertSha256Request? {
        if (!isFetchable(profile)) return null

        val server = profile.server?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = profile.serverPort?.toIntOrNull()?.takeIf { it > 0 } ?: AppConfig.DEFAULT_PORT

        return CertSha256Request(
            address = resolveDialAddress(server),
            port = port,
            serverName = inferServerName(profile),
            timeoutMs = TIMEOUT_MS,
        )
    }

    private fun isFetchable(profile: ProfileItem): Boolean {
        return profile.configType == EConfigType.HYSTERIA2 || profile.security == AppConfig.TLS
    }

    private fun fetchNative(
        useQuic: Boolean,
        request: CertSha256Request,
    ): CertSha256Result? {
        val type = if (useQuic) "quic" else "tls"
        return try {
            val raw = CoreNativeManager.fetchCertSha256(useQuic, JsonUtil.toJson(request))
                ?: return null
            JsonUtil.fromJsonSafe(raw, CertSha256Result::class.java)
        } catch (e: UnsatisfiedLinkError) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 API missing in libv2ray", e)
            null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 failed", e)
            null
        }
    }

    private fun fetchTlsViaJavaSsl(request: CertSha256Request): String? {
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket() as SSLSocket).use { socket ->
                val timeout = request.timeoutMs.toInt().coerceIn(1000, 15000)
                socket.soTimeout = timeout
                val sni = request.serverName?.takeIf { it.isNotBlank() }
                if (sni != null) {
                    val params = SSLParameters().apply {
                        serverNames = listOf(SNIHostName(sni))
                    }
                    socket.sslParameters = params
                }
                socket.connect(InetSocketAddress(request.address, request.port), timeout)
                socket.startHandshake()
                val leaf = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: return null
                val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                digest.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Fetch tls cert SHA-256 failed", e)
            null
        }
    }

    private fun resolveDialAddress(server: String): String {
        if (Utils.isPureIpAddress(server) || !Utils.isDomainName(server)) return server

        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6, false)
        return HttpUtil.resolveHostToIP(server, preferIpv6)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: server
    }

    private fun inferServerName(profile: ProfileItem): String? {
        val sni = profile.sni?.takeIf { it.isNotBlank() }
        return sni?.takeUnless { Utils.isPureIpAddress(it) }
    }
}
