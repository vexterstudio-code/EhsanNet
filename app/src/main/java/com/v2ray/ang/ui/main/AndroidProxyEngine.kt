package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure state-transition decision model for the split Normal/Boost bottom bar.
 */
sealed interface ConnectionTransitionDecision {
    data object IgnoreBusy : ConnectionTransitionDecision
    data object PromptSelectConfig : ConnectionTransitionDecision
    data class ConnectNormal(val configId: String) : ConnectionTransitionDecision
    data class SwitchToNormal(val configId: String) : ConnectionTransitionDecision
    data object ScanAndConnectBoost : ConnectionTransitionDecision
    data object SwitchToBoost : ConnectionTransitionDecision
    data object Disconnect : ConnectionTransitionDecision
}

fun planConnectionAction(
    currentStatus: ConnectionStatus,
    activeMode: ConnectionMode,
    targetMode: ConnectionMode,
    selectedConfigId: String?,
    lastActiveConfigId: String?,
): ConnectionTransitionDecision {
    if (currentStatus.isTransitioning) {
        return ConnectionTransitionDecision.IgnoreBusy
    }

    val manualConfigId = selectedConfigId?.takeIf { it.isNotBlank() }
        ?: lastActiveConfigId?.takeIf { it.isNotBlank() }

    return when (targetMode) {
        ConnectionMode.NORMAL -> when {
            currentStatus == ConnectionStatus.CONNECTED && activeMode == ConnectionMode.NORMAL ->
                ConnectionTransitionDecision.Disconnect

            currentStatus == ConnectionStatus.CONNECTED ->
                if (manualConfigId == null) {
                    ConnectionTransitionDecision.PromptSelectConfig
                } else {
                    ConnectionTransitionDecision.SwitchToNormal(manualConfigId)
                }

            manualConfigId == null ->
                ConnectionTransitionDecision.PromptSelectConfig

            else ->
                ConnectionTransitionDecision.ConnectNormal(manualConfigId)
        }

        ConnectionMode.BOOST -> when {
            currentStatus == ConnectionStatus.CONNECTED && activeMode == ConnectionMode.BOOST ->
                ConnectionTransitionDecision.Disconnect

            currentStatus == ConnectionStatus.CONNECTED ->
                ConnectionTransitionDecision.SwitchToBoost

            else ->
                ConnectionTransitionDecision.ScanAndConnectBoost
        }
    }
}

sealed interface ModeTabSelectionDecision {
    data object IgnoreBusy : ModeTabSelectionDecision
    data object NoChange : ModeTabSelectionDecision
    data class SwitchTabOnly(val mode: ConnectionMode) : ModeTabSelectionDecision
    data object SwitchToBoost : ModeTabSelectionDecision
    data class SwitchToNormal(val configId: String) : ModeTabSelectionDecision
    data object PromptSelectConfig : ModeTabSelectionDecision
}

fun planModeTabSelection(
    currentStatus: ConnectionStatus,
    activeMode: ConnectionMode,
    targetMode: ConnectionMode,
    selectedConfigId: String?,
    lastActiveConfigId: String?,
): ModeTabSelectionDecision {
    if (currentStatus.isTransitioning) {
        return ModeTabSelectionDecision.IgnoreBusy
    }
    if (targetMode == activeMode) {
        return ModeTabSelectionDecision.NoChange
    }
    if (currentStatus != ConnectionStatus.CONNECTED) {
        return ModeTabSelectionDecision.SwitchTabOnly(targetMode)
    }

    val manualConfigId = selectedConfigId?.takeIf { it.isNotBlank() }
        ?: lastActiveConfigId?.takeIf { it.isNotBlank() }

    return when (targetMode) {
        ConnectionMode.BOOST -> ModeTabSelectionDecision.SwitchToBoost
        ConnectionMode.NORMAL -> if (manualConfigId == null) {
            ModeTabSelectionDecision.PromptSelectConfig
        } else {
            ModeTabSelectionDecision.SwitchToNormal(manualConfigId)
        }
    }
}

/**
 * Real-core + TCP fallback implementation of [NodeProbeRunner].
 *
 * Uses [CoreTestService] (`:tasks` process) for real proxy outbound latency when the native
 * core (`libv2ray.aar`) is available, and falls back gracefully to bounded TCP socket probes
 * when native outbound probing is unavailable for the build or protocol.
 */
class AndroidNodeProbeRunner(
    private val dataSource: MainDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val useNativeCore: () -> Boolean = { CoreNativeManager.isNativeCoreAvailable() },
    private val tcpProbe: suspend (host: String, port: Int, timeoutMs: Int) -> Long = { host, port, timeoutMs ->
        SpeedtestManager.socketConnectTime(host, port, timeoutMs)
    },
) : NodeProbeRunner {

    override suspend fun probeBatch(
        nodes: List<ProxyNode>,
        perNodeTimeoutMs: Long,
        maxConcurrency: Int,
        onProgress: ((completed: Int, total: Int, bestLatencyMs: Long?) -> Unit)?,
    ): Map<String, Long> {
        if (nodes.isEmpty()) return emptyMap()
        if (!useNativeCore()) {
            return super.probeBatch(nodes, perNodeTimeoutMs, maxConcurrency, onProgress)
        }

        val requestId = "boost-batch-${UUID.randomUUID()}"
        val results = ConcurrentHashMap<String, Long>(nodes.size)
        val completed = AtomicInteger(0)
        val bestLatency = AtomicInteger(Int.MAX_VALUE)
        val expectedGuids = nodes.mapTo(HashSet(nodes.size)) { it.id }

        val effectiveBatches = ((nodes.size + maxConcurrency.coerceAtLeast(1) - 1) /
            maxConcurrency.coerceAtLeast(1)).coerceAtLeast(1)
        val totalBudgetMs = (effectiveBatches * perNodeTimeoutMs).coerceIn(
            perNodeTimeoutMs + 1_500L,
            MAX_BATCH_TIMEOUT_MS,
        )

        try {
            withTimeoutOrNull(totalBudgetMs) {
                kotlinx.coroutines.coroutineScope {
                    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                        dataSource.serviceEvents.collect { event ->
                            when (event) {
                                is MainServiceEvent.MeasureConfigSuccess -> {
                                    if (event.requestId != requestId) return@collect
                                    val guid = event.result.guid
                                    if (guid !in expectedGuids) return@collect
                                    val delay = event.result.delayMillis
                                    val isFirstForNode = results.putIfAbsent(guid, delay) == null
                                    if (delay > 0L && delay < Int.MAX_VALUE) {
                                        bestLatency.accumulateAndGet(delay.toInt(), ::minOf)
                                    }
                                    if (isFirstForNode) {
                                        val done = completed.incrementAndGet()
                                        val best = bestLatency.get().takeIf { it < Int.MAX_VALUE }?.toLong()
                                        onProgress?.invoke(done, nodes.size, best)
                                    }
                                }

                                is MainServiceEvent.MeasureConfigFinish -> {
                                    if (event.requestId == requestId) {
                                        throw BatchDoneSignal
                                    }
                                }

                                is MainServiceEvent.MeasureConfigCancelled -> {
                                    if (event.requestId == requestId) {
                                        throw BatchDoneSignal
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }

                    dataSource.sendMsg2TestService(
                        TestServiceMessage(
                            key = AppConfig.MSG_MEASURE_CONFIG_START,
                            serverGuids = nodes.map { it.id },
                            onlyTcp = false,
                        ),
                        requestId,
                    )

                    try {
                        collector.join()
                    } catch (_: BatchDoneSignal) {
                        collector.cancel()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            dataSource.cancelAllPing()
            throw cancelled
        }

        nodes.forEach { node ->
            results.putIfAbsent(node.id, -1L)
        }
        return results
    }

    override suspend fun probeSingle(node: ProxyNode, timeoutMs: Long): Long {
        if (useNativeCore()) {
            val requestId = "boost-node-${UUID.randomUUID()}"
            val nativeLatency = try {
                withTimeoutOrNull(timeoutMs) {
                    var resultMs = -1L
                    kotlinx.coroutines.coroutineScope {
                        val listener = launch(start = CoroutineStart.UNDISPATCHED) {
                            dataSource.serviceEvents
                                .filter { event ->
                                    when (event) {
                                        is MainServiceEvent.MeasureConfigSuccess -> event.requestId == requestId
                                        is MainServiceEvent.MeasureConfigFinish -> event.requestId == requestId
                                        is MainServiceEvent.MeasureConfigCancelled -> event.requestId == requestId
                                        else -> false
                                    }
                                }
                                .first { event ->
                                    when (event) {
                                        is MainServiceEvent.MeasureConfigSuccess -> {
                                            if (event.result.guid == node.id) {
                                                resultMs = event.result.delayMillis
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        is MainServiceEvent.MeasureConfigFinish,
                                        is MainServiceEvent.MeasureConfigCancelled -> true
                                        else -> false
                                    }
                                }
                        }
                        dataSource.sendMsg2TestService(
                            TestServiceMessage(
                                key = AppConfig.MSG_MEASURE_CONFIG_START,
                                serverGuids = listOf(node.id),
                                onlyTcp = false,
                            ),
                            requestId,
                        )
                        listener.join()
                    }
                    resultMs
                } ?: -1L
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            if (nativeLatency > 0L) {
                return nativeLatency
            }
        }

        val host = node.serverAddress.takeIf { it.isNotBlank() } ?: return -1L
        val port = node.port?.takeIf { it in 1..65535 } ?: return -1L
        return withContext(ioDispatcher) {
            tcpProbe(host, port, timeoutMs.coerceIn(500L, 4_500L).toInt())
        }
    }

    private object BatchDoneSignal : CancellationException("Boost batch probe finished")

    private companion object {
        const val MAX_BATCH_TIMEOUT_MS = 18_000L
    }
}
