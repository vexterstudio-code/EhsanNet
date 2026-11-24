package com.v2ray.ang.ui.main

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core-agnostic probe abstraction used by [BoostSelectorEngine] so latency and stability
 * measurement is decoupled from any specific proxy core (Xray, V2Ray, sing-box, etc.).
 */
interface NodeProbeRunner {
    /**
     * Measures a single real/TCP latency sample for [node] within [timeoutMs].
     * Returns latency in milliseconds (> 0) or <= 0 / -1 on timeout or error.
     */
    suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long

    /**
     * Probes [nodes] concurrently with a bounded semaphore and optional progress callback.
     * Implementations backed by multi-process batch workers may override this for throughput.
     */
    suspend fun probeBatch(
        nodes: List<ProxyNode>,
        perNodeTimeoutMs: Long,
        maxConcurrency: Int = 8,
        onProgress: ((completed: Int, total: Int, bestLatencyMs: Long?) -> Unit)? = null,
    ): Map<String, Long> {
        if (nodes.isEmpty()) return emptyMap()
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val results = ConcurrentHashMap<String, Long>(nodes.size)
        val completed = AtomicInteger(0)
        val bestLatency = AtomicInteger(Int.MAX_VALUE)

        coroutineScope {
            nodes.forEach { node ->
                launch {
                    val sample = semaphore.withPermit {
                        withTimeoutOrNull(perNodeTimeoutMs) {
                            probeSingle(node, perNodeTimeoutMs)
                        } ?: -1L
                    }
                    results[node.id] = sample
                    if (sample > 0L && sample < Int.MAX_VALUE) {
                        bestLatency.accumulateAndGet(sample.toInt(), ::minOf)
                    }
                    val done = completed.incrementAndGet()
                    val currentBest = bestLatency.get().takeIf { it < Int.MAX_VALUE }?.toLong()
                    onProgress?.invoke(done, nodes.size, currentBest)
                }
            }
        }
        return results
    }
}

/**
 * Core-agnostic connection runner abstraction decoupling UI/ViewModel logic from Android
 * foreground VPN/proxy service IPC details.
 */
interface ProxyEngine {
    fun connect(configId: String, mode: ConnectionMode)
    fun disconnect()
    fun reconnect(configId: String, mode: ConnectionMode)
    fun testActiveLatency()
}

sealed interface BoostSelectionOutcome {
    data object EmptyPool : BoostSelectionOutcome

    data class Selected(
        val winner: NodeTestResult,
        val rankedResults: List<NodeTestResult>,
        val scannedNodeCount: Int,
        val enabledSubscriptionCount: Int,
    ) : BoostSelectionOutcome

    data class FallbackSelected(
        val fallbackNode: ProxyNode,
        val rankedResults: List<NodeTestResult>,
        val scannedNodeCount: Int,
        val enabledSubscriptionCount: Int,
    ) : BoostSelectionOutcome
}
