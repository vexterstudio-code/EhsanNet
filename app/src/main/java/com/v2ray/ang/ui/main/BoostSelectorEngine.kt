package com.v2ray.ang.ui.main

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Cross-subscription smart auto-selector for Boost Connection mode.
 *
 * Scans all enabled [SubscriptionGroup]s (including standalone/local nodes), runs concurrent
 * bounded probes, performs multi-sample stability verification on top finalists, and ranks
 * candidates by composite [computeBoostScore] combining latency, jitter, packet loss, and
 * recent failure penalties.
 */
class BoostSelectorEngine(
    private val probeRunner: NodeProbeRunner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
    private val perNodeTimeoutMs: Long = DEFAULT_NODE_TIMEOUT_MS,
    private val finalistCount: Int = DEFAULT_FINALIST_COUNT,
    private val stabilitySampleCount: Int = DEFAULT_STABILITY_SAMPLES,
) {

    suspend fun selectBestNode(
        subscriptions: List<SubscriptionGroup>,
        preferredFallbackConfigId: String? = null,
        onProgress: ((BoostScanProgress) -> Unit)? = null,
    ): BoostSelectionOutcome {
        val enabledGroups = subscriptions.filter { it.isEnabled }
        val candidateNodes = enabledGroups
            .flatMap { it.nodes }
            .distinctBy { it.id }

        if (candidateNodes.isEmpty()) {
            return BoostSelectionOutcome.EmptyPool
        }

        val totalNodes = candidateNodes.size
        onProgress?.invoke(BoostScanProgress(scannedCount = 0, totalCount = totalNodes, bestCandidateLatencyMs = null))

        // Phase 1: Wide concurrent latency discovery across all enabled subscriptions.
        val firstWaveSamples = withContext(ioDispatcher) {
            probeRunner.probeBatch(
                nodes = candidateNodes,
                perNodeTimeoutMs = perNodeTimeoutMs,
                maxConcurrency = maxConcurrency,
                onProgress = { done, total, bestMs ->
                    onProgress?.invoke(
                        BoostScanProgress(
                            scannedCount = done,
                            totalCount = total,
                            bestCandidateLatencyMs = bestMs
                        )
                    )
                }
            )
        }

        coroutineContext.ensureActive()

        // Pick top viable finalists for multi-sample stability & jitter verification.
        val finalists = withContext(computeDispatcher) {
            candidateNodes
                .filter { (firstWaveSamples[it.id] ?: -1L) > 0L }
                .sortedWith(
                    compareBy<ProxyNode> {
                        val firstSample = firstWaveSamples[it.id] ?: Long.MAX_VALUE
                        firstSample + (it.recentFailureCount * FAILURE_PENALTY_MS).toLong()
                    }.thenBy { it.priorityOrder }
                )
                .take(finalistCount.coerceAtLeast(1))
        }

        val sampleBuckets = ConcurrentHashMap<String, MutableList<Long>>(candidateNodes.size)
        candidateNodes.forEach { node ->
            sampleBuckets[node.id] = mutableListOf(firstWaveSamples[node.id] ?: -1L)
        }

        // Phase 2: Multi-sample confirmation probes for top finalists to evaluate jitter and loss.
        val extraSamplesNeeded = (stabilitySampleCount - 1).coerceAtLeast(0)
        if (finalists.isNotEmpty() && extraSamplesNeeded > 0) {
            withContext(ioDispatcher) {
                val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
                coroutineScope {
                    val jobs = finalists.flatMap { finalist ->
                        (0 until extraSamplesNeeded).map {
                            async {
                                val sample = semaphore.withPermit {
                                    withTimeoutOrNull(perNodeTimeoutMs) {
                                        probeRunner.probeSingle(finalist, perNodeTimeoutMs)
                                    } ?: -1L
                                }
                                finalist.id to sample
                            }
                        }
                    }
                    jobs.awaitAll().forEach { (nodeId, sample) ->
                        sampleBuckets[nodeId]?.add(sample)
                    }
                }
            }
        }

        coroutineContext.ensureActive()

        val scoredResults = withContext(computeDispatcher) {
            candidateNodes
                .map { node ->
                    evaluateNode(
                        node = node,
                        samplesMs = sampleBuckets[node.id].orEmpty()
                    )
                }
                .sortedWith(
                    compareBy<NodeTestResult> { !it.isReachable }
                        .thenBy { it.boostScore }
                        .thenBy { it.node.priorityOrder }
                )
        }

        val bestReachable = scoredResults.firstOrNull { it.isReachable }
        if (bestReachable != null) {
            onProgress?.invoke(
                BoostScanProgress(
                    scannedCount = totalNodes,
                    totalCount = totalNodes,
                    bestCandidateLatencyMs = bestReachable.medianLatencyMs
                )
            )
            return BoostSelectionOutcome.Selected(
                winner = bestReachable,
                rankedResults = scoredResults,
                scannedNodeCount = totalNodes,
                enabledSubscriptionCount = enabledGroups.size,
            )
        }

        // Fallback guarantee when every probe fails/times out (e.g., strict firewall conditions).
        val fallback = withContext(computeDispatcher) {
            selectFallbackNode(
                candidates = candidateNodes,
                preferredConfigId = preferredFallbackConfigId
            )
        }
        return BoostSelectionOutcome.FallbackSelected(
            fallbackNode = fallback,
            rankedResults = scoredResults,
            scannedNodeCount = totalNodes,
            enabledSubscriptionCount = enabledGroups.size,
        )
    }

    internal fun evaluateNode(node: ProxyNode, samplesMs: List<Long>): NodeTestResult {
        val validSamples = samplesMs.filter { it > 0L }.sorted()
        if (validSamples.isEmpty()) {
            return NodeTestResult(
                node = node,
                samplesMs = samplesMs,
                successfulSamples = emptyList(),
                medianLatencyMs = -1L,
                jitterMs = Long.MAX_VALUE,
                packetLossRatio = 1.0,
                boostScore = Double.MAX_VALUE,
                isReachable = false,
            )
        }

        val median = if (validSamples.size % 2 == 1) {
            validSamples[validSamples.size / 2]
        } else {
            val mid = validSamples.size / 2
            (validSamples[mid - 1] + validSamples[mid]) / 2L
        }
        val jitter = if (validSamples.size >= 2) {
            validSamples.last() - validSamples.first()
        } else {
            0L
        }
        val totalAttempted = samplesMs.size.coerceAtLeast(1)
        val packetLoss = (totalAttempted - validSamples.size).toDouble() / totalAttempted.toDouble()
        val score = computeBoostScore(
            medianLatencyMs = median,
            jitterMs = jitter,
            packetLossRatio = packetLoss,
            recentFailureCount = node.recentFailureCount,
        )

        return NodeTestResult(
            node = node,
            samplesMs = samplesMs,
            successfulSamples = validSamples,
            medianLatencyMs = median,
            jitterMs = jitter,
            packetLossRatio = packetLoss,
            boostScore = score,
            isReachable = true,
        )
    }

    internal fun computeBoostScore(
        medianLatencyMs: Long,
        jitterMs: Long,
        packetLossRatio: Double,
        recentFailureCount: Int,
    ): Double = medianLatencyMs.toDouble() +
        (jitterMs.toDouble() * JITTER_WEIGHT) +
        (packetLossRatio.coerceIn(0.0, 1.0) * PACKET_LOSS_PENALTY_MS) +
        (recentFailureCount.coerceAtLeast(0).toDouble() * FAILURE_PENALTY_MS)

    internal fun selectFallbackNode(
        candidates: List<ProxyNode>,
        preferredConfigId: String?,
    ): ProxyNode {
        return candidates.minWith(
            compareBy<ProxyNode> { it.recentFailureCount }
                .thenBy { it.cachedLatencyMs <= 0L }
                .thenBy { if (it.cachedLatencyMs > 0L) it.cachedLatencyMs else Long.MAX_VALUE }
                .thenBy { it.id != preferredConfigId }
                .thenBy { it.priorityOrder }
        )
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 8
        const val DEFAULT_NODE_TIMEOUT_MS = 3_000L
        const val DEFAULT_FINALIST_COUNT = 5
        const val DEFAULT_STABILITY_SAMPLES = 3
        private const val JITTER_WEIGHT = 0.45
        private const val PACKET_LOSS_PENALTY_MS = 1200.0
        private const val FAILURE_PENALTY_MS = 220.0
    }
}
