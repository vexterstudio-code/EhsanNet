package com.v2ray.ang.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class BoostSelectorEngineTest {

    private fun node(
        id: String,
        subId: String = "sub1",
        subName: String = "Sub One",
        cachedLatencyMs: Long = 0L,
        recentFailureCount: Int = 0,
        priorityOrder: Int = 0,
    ): ProxyNode = ProxyNode(
        id = id,
        name = "Node-$id",
        protocolTag = "VLESS / ws / tls",
        serverAddress = "1.2.3.4",
        port = 443,
        subscriptionId = subId,
        subscriptionName = subName,
        cachedLatencyMs = cachedLatencyMs,
        recentFailureCount = recentFailureCount,
        priorityOrder = priorityOrder,
    )

    @Test
    fun emptyPoolOrOnlyDisabledSubscriptionsReturnsEmptyPool() = runBlocking {
        val disabledSub = SubscriptionGroup(
            id = "sub-disabled",
            name = "Disabled",
            isEnabled = false,
            nodes = listOf(node("n1", subId = "sub-disabled", subName = "Disabled")),
        )
        val probe = object : NodeProbeRunner {
            override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long = 50L
        }
        val engine = BoostSelectorEngine(probeRunner = probe, ioDispatcher = Dispatchers.Unconfined, computeDispatcher = Dispatchers.Unconfined)

        assertEquals(BoostSelectionOutcome.EmptyPool, engine.selectBestNode(emptyList()))
        assertEquals(BoostSelectionOutcome.EmptyPool, engine.selectBestNode(listOf(disabledSub)))
    }

    @Test
    fun scansAllEnabledSubscriptionsAndIgnoresDisabledOnes() = runBlocking {
        val groups = listOf(
            SubscriptionGroup(
                id = "sub-disabled",
                name = "Disabled",
                isEnabled = false,
                nodes = listOf(node("fast-but-disabled", subId = "sub-disabled", subName = "Disabled")),
            ),
            SubscriptionGroup(
                id = "sub-a",
                name = "Alpha Sub",
                isEnabled = true,
                nodes = listOf(node("a1", subId = "sub-a", subName = "Alpha Sub")),
            ),
            SubscriptionGroup(
                id = "",
                name = "Local",
                isEnabled = true,
                nodes = listOf(node("local-best", subId = "", subName = "Local")),
            ),
        )

        val latencies = mapOf(
            "fast-but-disabled" to 5L,
            "a1" to 190L,
            "local-best" to 42L,
        )
        val probedIds = mutableListOf<String>()
        val probe = object : NodeProbeRunner {
            override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long {
                synchronized(probedIds) { probedIds.add(node.id) }
                return latencies[node.id] ?: -1L
            }
        }

        val engine = BoostSelectorEngine(
            probeRunner = probe,
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
        )
        val outcome = engine.selectBestNode(groups) as BoostSelectionOutcome.Selected

        assertEquals("local-best", outcome.winner.node.id)
        assertEquals("Local", outcome.winner.node.subscriptionName)
        assertEquals(2, outcome.scannedNodeCount)
        assertEquals(2, outcome.enabledSubscriptionCount)
        assertTrue("fast-but-disabled" !in probedIds)
    }

    @Test
    fun prefersStableNodeOverJitteryOrPacketLossyNode() = runBlocking {
        val unstableNode = node("spiky", priorityOrder = 0)
        val stableNode = node("steady", priorityOrder = 1)
        val samplesById = mapOf(
            // Phase 1 = 35ms (fastest initially), then 1 failure & huge jitter spike (420ms)
            "spiky" to ArrayDeque(listOf(35L, -1L, 420L)),
            // Consistent ~60ms across all 3 probes
            "steady" to ArrayDeque(listOf(62L, 58L, 60L)),
        )
        val probe = object : NodeProbeRunner {
            override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long {
                val queue = samplesById[node.id] ?: return -1L
                return synchronized(queue) {
                    if (queue.isEmpty()) -1L else queue.removeFirst()
                }
            }
        }

        val engine = BoostSelectorEngine(
            probeRunner = probe,
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
        )
        val outcome = engine.selectBestNode(
            listOf(SubscriptionGroup("sub1", "Sub", true, listOf(unstableNode, stableNode)))
        ) as BoostSelectionOutcome.Selected

        assertEquals("steady", outcome.winner.node.id)
        assertEquals(60L, outcome.winner.medianLatencyMs)
        assertEquals(0.0, outcome.winner.packetLossRatio)
    }

    @Test
    fun fallsBackGracefullyWhenAllProbesFail() = runBlocking {
        val failingHistory = node("failing", cachedLatencyMs = 40L, recentFailureCount = 3, priorityOrder = 0)
        val healthyCached = node("healthy-cached", cachedLatencyMs = 95L, recentFailureCount = 0, priorityOrder = 1)
        val probe = object : NodeProbeRunner {
            override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long = -1L
        }

        val engine = BoostSelectorEngine(
            probeRunner = probe,
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
        )
        val outcome = engine.selectBestNode(
            subscriptions = listOf(SubscriptionGroup("sub1", "Sub", true, listOf(failingHistory, healthyCached))),
            preferredFallbackConfigId = "healthy-cached",
        ) as BoostSelectionOutcome.FallbackSelected

        assertEquals("healthy-cached", outcome.fallbackNode.id)
    }

    @Test
    fun respectsMaxConcurrencyBound() = runBlocking {
        val maxConcurrency = 3
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val nodes = (1..10).map { idx -> node("n$idx", priorityOrder = idx) }
        val probe = object : NodeProbeRunner {
            override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long {
                val now = active.incrementAndGet()
                peak.accumulateAndGet(now, ::maxOf)
                delay(20)
                active.decrementAndGet()
                return 80L + node.priorityOrder
            }
        }

        val engine = BoostSelectorEngine(
            probeRunner = probe,
            ioDispatcher = Dispatchers.Default,
            computeDispatcher = Dispatchers.Default,
            maxConcurrency = maxConcurrency,
        )
        val outcome = engine.selectBestNode(
            listOf(SubscriptionGroup("sub1", "Sub", true, nodes))
        ) as BoostSelectionOutcome.Selected

        assertTrue(peak.get() <= maxConcurrency, "Expected peak concurrency <= $maxConcurrency, got ${peak.get()}")
        assertEquals("n1", outcome.winner.node.id)
    }

    @Test
    fun cancellationStopsScanCleanly() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                val nodes = (1..5).map { idx -> node("n$idx") }
                val probe = object : NodeProbeRunner {
                    override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long {
                        delay(5_000)
                        return 50L
                    }
                }
                val engine = BoostSelectorEngine(probeRunner = probe, perNodeTimeoutMs = 4_000L)
                val job = async {
                    engine.selectBestNode(listOf(SubscriptionGroup("sub1", "Sub", true, nodes)))
                }
                delay(40)
                job.cancel()
                job.await()
            }
        }
    }
}
