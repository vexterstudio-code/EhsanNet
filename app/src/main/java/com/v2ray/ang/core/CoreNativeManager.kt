package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import go.Seq
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean
import libv2ray.CoreCallbackHandler as NativeCoreCallbackHandler
import libv2ray.CoreController as NativeCoreController
import libv2ray.ProcessFinder as NativeProcessFinder

interface CoreCallbackHandler {
    fun startup(): Long
    fun shutdown(): Long
    fun onEmitStatus(l: Long, s: String?): Long
}

interface ProcessFinder {
    fun findProcessByConnection(
        network: String,
        srcIP: String,
        srcPort: Long,
        destIP: String,
        destPort: Long,
    ): Long
}

interface CoreController {
    val isRunning: Boolean
    fun startLoop(configContent: String, tunFd: Int)
    fun stopLoop()
    fun registerProcessFinder(finder: ProcessFinder?)
    fun queryAllOutboundTrafficStats(): String
    fun measureDelay(testUrl: String): Long
}

/**
 * V2Ray/Xray Native Library Manager.
 *
 * Binds directly to `libv2ray.Libv2ray` bundled in `app/libs/libv2ray.aar`.
 */
object CoreNativeManager {
    private val initialized = AtomicBoolean(false)

    fun isNativeCoreAvailable(): Boolean = runCatching {
        Libv2ray.touch()
        true
    }.getOrDefault(false)

    fun initCoreEnv(context: Context?) {
        if (initialized.compareAndSet(false, true)) {
            try {
                context?.let { SettingsManager.initAssets(it, it.assets) }
                Seq.setContext(context?.applicationContext)
                val assetPath = Utils.userAssetPath(context)
                val deviceId = Utils.getDeviceIdForXUDPBaseKey()
                Libv2ray.initCoreEnv(assetPath, deviceId)
                LogUtil.i(AppConfig.TAG, "V2Ray core environment initialized successfully")
            } catch (e: Throwable) {
                LogUtil.e(AppConfig.TAG, "Failed to initialize V2Ray core environment", e)
                initialized.set(false)
            }
        } else {
            LogUtil.d(AppConfig.TAG, "V2Ray core environment already initialized, skipping")
        }
    }

    fun reconcileBrowserDialer(dialerAddr: String) {
        try {
            Libv2ray.reconcileBrowserDialer(dialerAddr)
            LogUtil.i(AppConfig.TAG, "Browser dialer reconciled successfully with address: $dialerAddr")
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "Failed to reconcile browser dialer with address: $dialerAddr", e)
        }
    }

    fun getLibVersion(): String {
        return try {
            Libv2ray.checkVersionX() ?: "Unknown"
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "Failed to check V2Ray version", e)
            "Unknown"
        }
    }

    fun measureOutboundDelay(config: String, testUrl: String): Long {
        return try {
            Libv2ray.measureOutboundDelay(config, testUrl)
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "Failed to measure outbound delay", e)
            -1L
        }
    }

    fun fetchCertSha256(useQuic: Boolean, requestJson: String): String? {
        return try {
            if (useQuic) {
                Libv2ray.fetchQuicCertSha256(requestJson)
            } else {
                Libv2ray.fetchTlsCertSha256(requestJson)
            }
        } catch (e: Throwable) {
            LogUtil.e(AppConfig.TAG, "Failed to fetch certificate SHA-256", e)
            null
        }
    }

    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        val nativeHandler = object : NativeCoreCallbackHandler {
            override fun startup(): Long = handler.startup()
            override fun shutdown(): Long = handler.shutdown()
            override fun onEmitStatus(l: Long, s: String?): Long = handler.onEmitStatus(l, s)
        }
        val nativeController = Libv2ray.newCoreController(nativeHandler)
        return BoundCoreController(nativeController)
    }

    private class BoundCoreController(private val delegate: NativeCoreController) : CoreController {
        override val isRunning: Boolean
            get() = delegate.isRunning

        override fun startLoop(configContent: String, tunFd: Int) {
            delegate.startLoop(configContent, tunFd)
        }

        override fun stopLoop() {
            delegate.stopLoop()
        }

        override fun registerProcessFinder(finder: ProcessFinder?) {
            val nativeFinder = finder?.let { f ->
                object : NativeProcessFinder {
                    override fun findProcessByConnection(
                        network: String,
                        srcIP: String,
                        srcPort: Long,
                        destIP: String,
                        destPort: Long,
                    ): Long = f.findProcessByConnection(network, srcIP, srcPort, destIP, destPort)
                }
            }
            delegate.registerProcessFinder(nativeFinder)
        }

        override fun queryAllOutboundTrafficStats(): String {
            return delegate.queryAllOutboundTrafficStats().orEmpty()
        }

        override fun measureDelay(testUrl: String): Long {
            return delegate.measureDelay(testUrl)
        }
    }
}