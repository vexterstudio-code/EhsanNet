package com.v2ray.ang.ui.main

import java.util.Locale

enum class ConnectionMode(val prefValue: String) {
    NORMAL("normal"),
    BOOST("boost");

    companion object {
        fun fromPref(value: String?): ConnectionMode =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }
}

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING_BOOST,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR;

    val isTransitioning: Boolean
        get() = this == SCANNING_BOOST || this == CONNECTING || this == DISCONNECTING
}

data class ProxyNode(
    val id: String,
    val name: String,
    val protocolTag: String,
    val serverAddress: String,
    val port: Int?,
    val subscriptionId: String,
    val subscriptionName: String,
    val cachedLatencyMs: Long = 0L,
    val recentFailureCount: Int = 0,
    val priorityOrder: Int = 0,
) {
    fun toActiveSummary(): ActiveConfigSummary = ActiveConfigSummary(
        configId = id,
        name = name,
        protocolTag = protocolTag,
        subscriptionId = subscriptionId,
        subscriptionName = subscriptionName,
    )
}

data class SubscriptionGroup(
    val id: String,
    val name: String,
    val isEnabled: Boolean,
    val nodes: List<ProxyNode>,
)

data class NodeTestResult(
    val node: ProxyNode,
    val samplesMs: List<Long>,
    val successfulSamples: List<Long>,
    val medianLatencyMs: Long,
    val jitterMs: Long,
    val packetLossRatio: Double,
    val boostScore: Double,
    val isReachable: Boolean,
)

data class ActiveConfigSummary(
    val configId: String,
    val name: String,
    val protocolTag: String,
    val subscriptionId: String,
    val subscriptionName: String,
)

data class BoostScanProgress(
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val bestCandidateLatencyMs: Long? = null,
)

data class ConnectionUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeMode: ConnectionMode = ConnectionMode.NORMAL,
    val selectedConfigId: String? = null,
    val activeConfigSummary: ActiveConfigSummary? = null,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val lastPingMs: Long? = null,
    val scanProgress: BoostScanProgress? = null,
    val statusMessage: String? = null,
)

/**
 * Formats byte-per-second throughput into clean dynamic units with stable numeric precision.
 * Returns "0 KB/s" for idle/disconnected states so speed counters never show stale values.
 */
fun formatSpeedBytesPerSec(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 KB/s"
    if (bytesPerSec < 1024L) return "$bytesPerSec B/s"

    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) {
        return if (kb >= 100.0) {
            String.format(Locale.US, "%.0f KB/s", kb)
        } else {
            String.format(Locale.US, "%.1f KB/s", kb)
        }
    }

    val mb = kb / 1024.0
    if (mb < 1024.0) {
        return String.format(Locale.US, "%.2f MB/s", mb)
    }

    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB/s", gb)
}
