package com.v2ray.ang.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.matchesPattern
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

private fun applyTestDelayResults(
    servers: List<ServersCache>,
    updates: Map<String, Long>,
): List<ServersCache> = servers.map { server ->
    val delayMillis = updates[server.guid]
    if (delayMillis == null || delayMillis == server.testDelayMillis) {
        server
    } else {
        server.copy(testDelayMillis = delayMillis)
    }
}

private fun applyTestDelayResultsToRows(
    rows: List<ServerRowUiModel>,
    updates: Map<String, Long>,
): List<ServerRowUiModel> = rows.map { row ->
    val delayMillis = updates[row.guid]
    if (delayMillis == null || delayMillis == row.testDelayMillis) {
        row
    } else {
        row.copy(testDelayMillis = delayMillis)
    }
}

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource,
    probeRunner: NodeProbeRunner = AndroidNodeProbeRunner(dataSource),
    private val boostSelectorEngine: BoostSelectorEngine = BoostSelectorEngine(probeRunner),
    proxyEngine: ProxyEngine? = null,
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _vpnCommands = Channel<MainVpnCommand>(Channel.BUFFERED)
    val vpnCommands: Flow<MainVpnCommand> = _vpnCommands.receiveAsFlow()

    private val activeProxyEngine: ProxyEngine = proxyEngine ?: object : ProxyEngine {
        override fun connect(configId: String, mode: ConnectionMode) {
            _vpnCommands.trySend(MainVpnCommand.StartTunnel(configId, mode))
        }

        override fun disconnect() {
            _vpnCommands.trySend(MainVpnCommand.StopTunnel)
        }

        override fun reconnect(configId: String, mode: ConnectionMode) {
            _vpnCommands.trySend(MainVpnCommand.StartTunnel(configId, mode))
        }

        override fun testActiveLatency() {
            testCurrentServerRealPing()
        }
    }

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(buildInitialUiState(dataSource))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupUiFlows = ConcurrentHashMap<String, MutableStateFlow<ServerGroupUiState>>()
    private val groupServerFlows = ConcurrentHashMap<String, StateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null
    private var testResultFlushJob: Job? = null
    private val pendingTestResults = linkedMapOf<String, Long>()

    private val testRequests = MainTestRequests()
    private var bulkTestJob: Job? = null
    private var boostScanJob: Job? = null
    private var transitionJob: Job? = null
    private var stopConfirmationDeferred: CompletableDeferred<Unit>? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> {
                stopConfirmationDeferred?.complete(Unit)
                updateRunningState(false, clearTestingText = false)
            }
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                val activeId = uiState.value.connectionUi.selectedConfigId ?: uiState.value.selectedGuid
                if (!activeId.isNullOrBlank()) {
                    dataSource.recordNodeConnectionOutcome(activeId, succeeded = true)
                    dataSource.setLastActiveConfigId(activeId)
                }
                dataSource.sendMsg2Service(AppConfig.MSG_REGISTER_CLIENT, "")
                updateRunningState(true)
            }

            is MainServiceEvent.StateStartFailure -> {
                val activeId = uiState.value.connectionUi.selectedConfigId ?: uiState.value.selectedGuid
                if (!activeId.isNullOrBlank()) {
                    dataSource.recordNodeConnectionOutcome(activeId, succeeded = false)
                }
                val failureMessage = if (!event.message.isNullOrBlank()) {
                    toastError(event.message)
                    event.message
                } else {
                    toastError(R.string.toast_services_failure)
                    dataSource.getString(R.string.toast_services_failure)
                }
                updateRunningState(false, clearTestingText = true, failureMessage = failureMessage)
            }

            MainServiceEvent.StateStopSuccess -> {
                stopConfirmationDeferred?.complete(Unit)
                updateRunningState(false)
            }

            is MainServiceEvent.TrafficSpeed -> {
                _uiState.update { state ->
                    val connected = state.isRunning ||
                        state.connectionUi.connectionStatus == ConnectionStatus.CONNECTED
                    if (!connected) {
                        state
                    } else {
                        state.copy(
                            connectionUi = state.connectionUi.copy(
                                downloadSpeedBytesPerSec = event.stat.downloadBytesPerSec.coerceAtLeast(0L),
                                uploadSpeedBytesPerSec = event.stat.uploadBytesPerSec.coerceAtLeast(0L),
                            )
                        )
                    }
                }
            }

            is MainServiceEvent.MeasureDelayResult -> {
                if (!uiState.value.isRunning) return
                _uiState.update { state ->
                    val updatedPing = event.result.delayMillis.takeIf { it > 0L } ?: state.connectionUi.lastPingMs
                    state.copy(
                        isTesting = false,
                        status = MainStatus.ConnectionTest(event.result),
                        connectionUi = state.connectionUi.copy(lastPingMs = updatedPing),
                    )
                }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                queueTestResult(event.result, request)
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                if (event.requestId == testRequests.bulk?.id) {
                    _uiState.update { it.copy(status = MainStatus.TestProgress(event.progress)) }
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                val request = testRequests.bulk?.takeIf { it.id == event.requestId } ?: return
                val scheduledFlush = testResultFlushJob
                testResultFlushJob = viewModelScope.launch {
                    scheduledFlush?.cancelAndJoin()
                    if (testRequests.bulk?.id != request.id) return@launch
                    flushPendingTestResults(request)
                    onTestsFinished(request.id)
                }
            }

            is MainServiceEvent.MeasureDelayCancelled -> {
                //if (testRequests.completeCurrent(event.requestId)) resetTestStatus()
            }

            is MainServiceEvent.MeasureConfigCancelled -> {
                if (testRequests.completeBulk(event.requestId) != null) {
                    cancelPendingTestResults()
                    resetTestStatus()
                }
            }
        }
    }

    private fun queueTestResult(result: RealPingResult, request: MainTestRequests.Bulk) {
        pendingTestResults[result.guid] = result.delayMillis
        if (testResultFlushJob?.isActive == true) return

        testResultFlushJob = viewModelScope.launch {
            while (pendingTestResults.isNotEmpty()) {
                delay(TEST_RESULT_FLUSH_INTERVAL_MS)
                flushPendingTestResults(request)
            }
        }
    }

    private suspend fun flushPendingTestResults(request: MainTestRequests.Bulk) {
        if (pendingTestResults.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return

        val updates = cacheMutex.withLock {
            val drained = pendingTestResults.toMap()
            pendingTestResults.clear()
            groupDataCache[request.groupId]?.let { cached ->
                groupDataCache[request.groupId] = applyTestDelayResults(cached, drained)
            }
            drained
        }
        if (updates.isEmpty()) return
        if (testRequests.bulk?.id != request.id) return
        mutableServerGroupState(request.groupId).update { current ->
            current.copy(
                servers = applyTestDelayResults(current.servers, updates),
                rows = applyTestDelayResultsToRows(current.rows, updates),
            )
        }
    }

    internal fun formatStatus(status: MainStatus): String = when (status) {
        MainStatus.Disconnected -> dataSource.getString(R.string.connection_not_connected)
        MainStatus.Connected -> dataSource.getString(R.string.connection_connected)
        MainStatus.Testing -> dataSource.getString(R.string.connection_test_testing)
        is MainStatus.TestProgress -> dataSource.getString(
            R.string.connection_running_task_left,
            status.progress
        )

        is MainStatus.ConnectionTest -> formatConnectionTestResult(status.result)
    }

    private fun formatConnectionTestResult(result: ConnectionTestResult): String {
        val status = if (result.delayMillis >= 0) {
            val delay = dataSource.getString(R.string.server_test_delay_value, result.delayMillis)
            dataSource.getString(R.string.connection_test_available, delay)
        } else {
            val detail = result.errorMessage.ifBlank {
                dataSource.getString(R.string.connection_test_empty_message)
            }
            dataSource.getString(R.string.connection_test_error, detail)
        }

        if (result.delayMillis < 0 || (result.country == null && result.ipAddress == null)) {
            return status
        }

        val unknown = dataSource.getString(R.string.value_unknown)
        return "$status\n(${result.country ?: unknown}) ${result.ipAddress ?: unknown}"
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupServerFlows.computeIfAbsent(groupId) {
            val groupState = mutableServerGroupState(groupId)
            groupState
                .map { it.servers }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = groupState.value.servers,
                )
        }

    internal fun serverGroupState(groupId: String): StateFlow<ServerGroupUiState> =
        mutableServerGroupState(groupId).asStateFlow()

    private fun mutableServerGroupState(groupId: String): MutableStateFlow<ServerGroupUiState> =
        groupUiFlows.computeIfAbsent(groupId) { MutableStateFlow(ServerGroupUiState()) }

    private fun currentServers(): List<ServersCache> =
        mutableServerGroupState(uiState.value.selectedGroupId).value.servers

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub(uiState.value.selectedGroupId)
            is MainAction.UpdateGroupSubscription -> importConfigViaSub(action.groupId)
            MainAction.ExportAll -> exportAllAsync()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> updateSelectedGuid(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            MainAction.LocateHandled -> consumeLocateTarget()
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService -> handleConnectionRequest(uiState.value.connectionUi.activeMode)
            is MainAction.SelectConnectionMode -> selectConnectionMode(action.mode)
            MainAction.ConnectNormal -> handleConnectionRequest(ConnectionMode.NORMAL)
            MainAction.ConnectBoost -> handleConnectionRequest(ConnectionMode.BOOST)
            MainAction.CancelBoostScan -> cancelBoostScan()
            MainAction.ClearStatusMessage -> {
                _uiState.update { it.copy(connectionUi = it.connectionUi.copy(statusMessage = null)) }
            }

            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            is MainAction.ShareFullContent,
            is MainAction.OpenSubscriptionLink,
            MainAction.OpenVipBot -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
                rgbBorderEnabled = dataSource.getRgbBorderEnabled()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        val filteredServers = applyKeywordFilter(servers)
        mutableServerGroupState(groupId).value = ServerGroupUiState(
            servers = filteredServers,
            rows = buildServerRows(groupId, filteredServers)
        )
    }

    private fun buildServerRows(groupId: String, servers: List<ServersCache>): List<ServerRowUiModel> {
        val subscriptionRemarks = if (groupId.isEmpty()) {
            servers.asSequence()
                .map { it.profile.subscriptionId }
                .filter { it.isNotEmpty() }
                .distinct()
                .associateWith { subscriptionId ->
                    dataSource.getSubscriptionItem(subscriptionId)?.remarks.orEmpty()
                }
        } else {
            emptyMap()
        }
        return servers.map { server ->
            buildServerRowUiModel(
                server = server,
                subscriptionRemarks = subscriptionRemarks[server.profile.subscriptionId].orEmpty()
            )
        }
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(
                        id = it.guid,
                        remarks = it.subscription.remarks,
                        usageInfo = it.subscription.usageInfo?.let { info ->
                            SubscriptionInfoParser.withWebPageFallback(info, it.subscription.url)
                        },
                    )
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupUiFlows.keys.removeAll { it !in validIds }
                groupServerFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                val refreshedGuid = dataSource.getSelectServer()
                val activeGuid = refreshedGuid ?: dataSource.getLastActiveConfigId()
                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = refreshedGuid,
                        connectionUi = it.connectionUi.copy(
                            selectedConfigId = activeGuid ?: it.connectionUi.selectedConfigId,
                            activeConfigSummary = dataSource.getActiveConfigSummary(
                                activeGuid ?: it.connectionUi.selectedConfigId
                            ),
                        ),
                    )
                }
                groups.forEach { mutableServerGroupState(it.id) }

                if (groups.isEmpty()) {
                    cacheMutex.withLock { groupDataCache.clear() }
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        configText, uiState.value.selectedGroupId, true
                    )
                    when {
                        count > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, count))
                            setupGroupTab(forceRefresh = true)
                        }

                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub(subId: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(
                                getQuantityString(
                                    R.plurals.title_update_config_count,
                                    result.configCount,
                                    result.configCount,
                                )
                            )

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServerGroupState(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String, asManualNormal: Boolean = false) {
        dataSource.setSelectServer(guid)
        dataSource.setLastActiveConfigId(guid)
        if (asManualNormal) {
            dataSource.setPersistedConnectionMode(ConnectionMode.NORMAL)
        }
        val summary = dataSource.getActiveConfigSummary(guid)
        val cachedPing = dataSource.resolveNodeLatency(guid)
        _uiState.update { state ->
            state.copy(
                selectedGuid = guid,
                connectionUi = state.connectionUi.copy(
                    activeMode = if (asManualNormal) ConnectionMode.NORMAL else state.connectionUi.activeMode,
                    selectedConfigId = guid,
                    activeConfigSummary = summary,
                    lastPingMs = if (!state.isRunning || asManualNormal) cachedPing else state.connectionUi.lastPingMs,
                    statusMessage = null,
                ),
            )
        }
    }

    fun refreshSelectedGuid() {
        val guid = dataSource.getSelectServer()
        val effectiveGuid = guid ?: dataSource.getLastActiveConfigId()
        val summary = dataSource.getActiveConfigSummary(effectiveGuid)
        val cachedPing = effectiveGuid?.let { dataSource.resolveNodeLatency(it) }
        _uiState.update { state ->
            state.copy(
                selectedGuid = guid,
                connectionUi = state.connectionUi.copy(
                    selectedConfigId = effectiveGuid,
                    activeConfigSummary = summary,
                    lastPingMs = cachedPing,
                ),
            )
        }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val groupState = mutableServerGroupState(groupId).value
        val servers = groupState.servers.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val rows = groupState.rows.toMutableList()
        rows.moveItem(fromPosition, toPosition)
        val guids = servers.map { it.guid }
        mutableServerGroupState(groupId).value = ServerGroupUiState(servers, rows)
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        bulkTestJob?.cancel()
        bulkTestJob = null
        testRequests.cancelBulk()
        testRequests.invalidateCurrent()
        cancelPendingTestResults()
        resetTestStatus()
        dataSource.cancelAllPing()
    }

    private fun resetTestStatus() {
        _uiState.update {
            it.copy(
                isTesting = testRequests.isTesting,
                status = if (testRequests.isTesting) MainStatus.Testing
                else if (it.isRunning) MainStatus.Connected else MainStatus.Disconnected
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        cancelAllPing()
        val groupId = uiState.value.selectedGroupId
        val servers = currentServers()
        if (servers.isEmpty()) {
            return
        }
        val serverGuids = servers.map { it.guid }
        mutableServerGroupState(groupId).update { current ->
            current.copy(
                servers = current.servers.map { server ->
                    if (server.testDelayMillis == 0L) server
                    else server.copy(testDelayMillis = 0L)
                },
                rows = current.rows.map { row ->
                    if (row.testDelayMillis == 0L) row
                    else row.copy(testDelayMillis = 0L)
                }
            )
        }
        val request = testRequests.beginBulk(groupId)
        val message = TestServiceMessage(
            key = AppConfig.MSG_MEASURE_CONFIG_START,
            subscriptionId = groupId,
            serverGuids = if (keywordFilter.isNotEmpty()) serverGuids else emptyList(),
            onlyTcp = onlyTcp
        )
        _uiState.update {
            it.copy(
                isTesting = true,
                status = MainStatus.Testing
            )
        }
        bulkTestJob = viewModelScope.launch {
            withContext(ioDispatcher) {
                dataSource.clearAllTestDelayResults(serverGuids)
                val resetGuids = serverGuids.toHashSet()
                cacheMutex.withLock {
                    groupDataCache[groupId]?.let { cached ->
                        groupDataCache[groupId] = cached.map { server ->
                            if (server.guid !in resetGuids || server.testDelayMillis == 0L) server
                            else server.copy(testDelayMillis = 0L)
                        }
                    }
                }
            }
            dataSource.sendMsg2TestService(message, request.id)
        }
    }

    private fun cancelPendingTestResults() {
        testResultFlushJob?.cancel()
        testResultFlushJob = null
        pendingTestResults.clear()
    }

    fun testCurrentServerRealPing() {
        if (!uiState.value.isRunning) return
        _uiState.update { it.copy(isTesting = true, status = MainStatus.Testing) }
        dataSource.testCurrentServerRealPing("")
    }

    private fun onTestsFinished(requestId: String) {
        if (testRequests.completeBulk(requestId) == null) return
        resetTestStatus()
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.clear() }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        if (_uiState.value.groups.none { it.id == groupId }) return
        viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId))
            if (_uiState.value.selectedGroupId != groupId) {
                dataSource.setSelectedSubscriptionId(groupId)
            }
            val target = LocateTarget(groupId, selected)
            _uiState.update {
                it.copy(selectedGroupId = groupId, locateTarget = target)
            }
        }
    }

    private fun consumeLocateTarget() {
        _uiState.update { it.copy(locateTarget = null) }
    }

    // ---------- Connection orchestration (Normal & Boost) ----------
    fun selectConnectionMode(targetMode: ConnectionMode) {
        val current = uiState.value
        when (
            val decision = planModeTabSelection(
                currentStatus = current.connectionUi.connectionStatus,
                activeMode = current.connectionUi.activeMode,
                targetMode = targetMode,
                selectedConfigId = current.selectedGuid,
                lastActiveConfigId = dataSource.getLastActiveConfigId(),
            )
        ) {
            ModeTabSelectionDecision.IgnoreBusy,
            ModeTabSelectionDecision.NoChange -> return

            is ModeTabSelectionDecision.SwitchTabOnly -> {
                dataSource.setPersistedConnectionMode(decision.mode)
                _uiState.update { state ->
                    state.copy(
                        connectionUi = state.connectionUi.copy(
                            activeMode = decision.mode,
                            connectionStatus = if (state.connectionUi.connectionStatus == ConnectionStatus.ERROR) {
                                ConnectionStatus.DISCONNECTED
                            } else {
                                state.connectionUi.connectionStatus
                            },
                            statusMessage = null,
                        )
                    )
                }
            }

            ModeTabSelectionDecision.PromptSelectConfig -> {
                val prompt = dataSource.getString(R.string.connection_select_config_first)
                toast(prompt)
                _uiState.update { state ->
                    state.copy(
                        connectionUi = state.connectionUi.copy(
                            statusMessage = prompt,
                        )
                    )
                }
            }

            is ModeTabSelectionDecision.SwitchToNormal -> {
                transitionJob?.cancel()
                transitionJob = viewModelScope.launch {
                    disconnectAndAwaitStop()
                    startSelectedConfig(decision.configId, ConnectionMode.NORMAL)
                }
            }

            ModeTabSelectionDecision.SwitchToBoost -> {
                launchBoostSelection(disconnectExistingFirst = true)
            }
        }
    }

    fun handleConnectionRequest(targetMode: ConnectionMode) {
        val current = uiState.value
        when (
            val decision = planConnectionAction(
                currentStatus = current.connectionUi.connectionStatus,
                activeMode = current.connectionUi.activeMode,
                targetMode = targetMode,
                selectedConfigId = current.selectedGuid,
                lastActiveConfigId = dataSource.getLastActiveConfigId(),
            )
        ) {
            ConnectionTransitionDecision.IgnoreBusy -> return

            ConnectionTransitionDecision.PromptSelectConfig -> {
                val prompt = dataSource.getString(R.string.connection_select_config_first)
                toast(prompt)
                _uiState.update {
                    it.copy(
                        connectionUi = it.connectionUi.copy(
                            activeMode = ConnectionMode.NORMAL,
                            statusMessage = prompt,
                        )
                    )
                }
            }

            is ConnectionTransitionDecision.ConnectNormal -> {
                startSelectedConfig(decision.configId, ConnectionMode.NORMAL)
            }

            is ConnectionTransitionDecision.SwitchToNormal -> {
                transitionJob?.cancel()
                transitionJob = viewModelScope.launch {
                    disconnectAndAwaitStop()
                    startSelectedConfig(decision.configId, ConnectionMode.NORMAL)
                }
            }

            ConnectionTransitionDecision.ScanAndConnectBoost -> {
                launchBoostSelection(disconnectExistingFirst = false)
            }

            ConnectionTransitionDecision.SwitchToBoost -> {
                launchBoostSelection(disconnectExistingFirst = true)
            }

            ConnectionTransitionDecision.Disconnect -> {
                disconnectActiveTunnel()
            }
        }
    }

    private fun startSelectedConfig(configId: String, mode: ConnectionMode) {
        dataSource.setSelectServer(configId)
        dataSource.setLastActiveConfigId(configId)
        dataSource.setPersistedConnectionMode(mode)
        val summary = dataSource.getActiveConfigSummary(configId)
        val cachedPing = dataSource.resolveNodeLatency(configId)

        _uiState.update { state ->
            state.copy(
                selectedGuid = configId,
                connectionUi = state.connectionUi.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    activeMode = mode,
                    selectedConfigId = configId,
                    activeConfigSummary = summary ?: state.connectionUi.activeConfigSummary,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    lastPingMs = cachedPing ?: state.connectionUi.lastPingMs,
                    scanProgress = null,
                    statusMessage = null,
                ),
            )
        }
        activeProxyEngine.connect(configId, mode)
    }

    private fun disconnectActiveTunnel() {
        boostScanJob?.cancel()
        boostScanJob = null
        transitionJob?.cancel()
        _uiState.update { state ->
            state.copy(
                connectionUi = state.connectionUi.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTING,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    scanProgress = null,
                    statusMessage = null,
                )
            )
        }
        activeProxyEngine.disconnect()
    }

    private suspend fun disconnectAndAwaitStop() {
        val deferred = CompletableDeferred<Unit>()
        stopConfirmationDeferred = deferred
        _uiState.update { state ->
            state.copy(
                connectionUi = state.connectionUi.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTING,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    scanProgress = null,
                    statusMessage = null,
                )
            )
        }
        activeProxyEngine.disconnect()
        withTimeoutOrNull(STOP_CONFIRMATION_TIMEOUT_MS) {
            deferred.await()
        }
        if (stopConfirmationDeferred === deferred) {
            stopConfirmationDeferred = null
        }
    }

    private fun launchBoostSelection(disconnectExistingFirst: Boolean) {
        if (boostScanJob?.isActive == true) return
        transitionJob?.cancel()
        boostScanJob = viewModelScope.launch {
            try {
                if (disconnectExistingFirst) {
                    disconnectAndAwaitStop()
                }
                ensureActive()

                dataSource.setPersistedConnectionMode(ConnectionMode.BOOST)
                _uiState.update { state ->
                    state.copy(
                        connectionUi = state.connectionUi.copy(
                            connectionStatus = ConnectionStatus.SCANNING_BOOST,
                            activeMode = ConnectionMode.BOOST,
                            downloadSpeedBytesPerSec = 0L,
                            uploadSpeedBytesPerSec = 0L,
                            scanProgress = BoostScanProgress(),
                            statusMessage = dataSource.getString(R.string.connection_boost_scanning),
                        )
                    )
                }

                val groups = withContext(ioDispatcher) {
                    dataSource.getBoostSubscriptionGroups()
                }
                ensureActive()

                val outcome = boostSelectorEngine.selectBestNode(
                    subscriptions = groups,
                    preferredFallbackConfigId = uiState.value.selectedGuid ?: dataSource.getLastActiveConfigId(),
                    onProgress = { progress ->
                        _uiState.update { state ->
                            if (state.connectionUi.connectionStatus != ConnectionStatus.SCANNING_BOOST) {
                                state
                            } else {
                                state.copy(
                                    connectionUi = state.connectionUi.copy(
                                        scanProgress = progress,
                                        lastPingMs = progress.bestCandidateLatencyMs ?: state.connectionUi.lastPingMs,
                                    )
                                )
                            }
                        }
                    },
                )
                ensureActive()

                when (outcome) {
                    BoostSelectionOutcome.EmptyPool -> {
                        val emptyMessage = dataSource.getString(R.string.connection_boost_empty_pool)
                        toast(emptyMessage)
                        _uiState.update { state ->
                            state.copy(
                                connectionUi = state.connectionUi.copy(
                                    connectionStatus = ConnectionStatus.DISCONNECTED,
                                    scanProgress = null,
                                    statusMessage = emptyMessage,
                                )
                            )
                        }
                    }

                    is BoostSelectionOutcome.Selected -> {
                        val winner = outcome.winner
                        val node = winner.node
                        val chosenMessage = dataSource.getString(
                            R.string.connection_boost_selected_format,
                            node.name,
                            node.subscriptionName,
                            winner.medianLatencyMs,
                        )
                        toastSuccess(chosenMessage)
                        applyBoostNodeAndConnect(node, winner.medianLatencyMs, chosenMessage)
                    }

                    is BoostSelectionOutcome.FallbackSelected -> {
                        val node = outcome.fallbackNode
                        val fallbackMessage = dataSource.getString(
                            R.string.connection_boost_fallback_format,
                            node.name,
                            node.subscriptionName,
                        )
                        toast(fallbackMessage)
                        applyBoostNodeAndConnect(node, node.cachedLatencyMs.takeIf { it > 0L }, fallbackMessage)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Boost selection failed", error)
                val failMessage = dataSource.getString(R.string.toast_services_failure)
                toastError(failMessage)
                _uiState.update { state ->
                    state.copy(
                        connectionUi = state.connectionUi.copy(
                            connectionStatus = ConnectionStatus.ERROR,
                            scanProgress = null,
                            statusMessage = failMessage,
                        )
                    )
                }
            } finally {
                boostScanJob = null
            }
        }
    }

    private fun applyBoostNodeAndConnect(
        node: ProxyNode,
        latencyMs: Long?,
        statusMessage: String,
    ) {
        dataSource.setSelectServer(node.id)
        dataSource.setLastActiveConfigId(node.id)
        dataSource.setPersistedConnectionMode(ConnectionMode.BOOST)

        _uiState.update { state ->
            state.copy(
                selectedGuid = node.id,
                connectionUi = state.connectionUi.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    activeMode = ConnectionMode.BOOST,
                    selectedConfigId = node.id,
                    activeConfigSummary = node.toActiveSummary(),
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    lastPingMs = latencyMs,
                    scanProgress = null,
                    statusMessage = statusMessage,
                ),
            )
        }
        activeProxyEngine.connect(node.id, ConnectionMode.BOOST)
    }

    fun cancelBoostScan() {
        val activeJob = boostScanJob ?: return
        activeJob.cancel()
        boostScanJob = null
        dataSource.cancelAllPing()
        _uiState.update { state ->
            state.copy(
                connectionUi = state.connectionUi.copy(
                    connectionStatus = if (state.isRunning) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
                    scanProgress = null,
                    statusMessage = dataSource.getString(R.string.connection_boost_cancelled),
                )
            )
        }
    }

    fun onStartAborted(reason: String? = null) {
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                connectionUi = state.connectionUi.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    scanProgress = null,
                    statusMessage = reason,
                ),
            )
        }
    }

    // ---------- Running state ----------
    private fun updateRunningState(
        running: Boolean,
        clearTestingText: Boolean = true,
        failureMessage: String? = null,
    ) {
        if (!running || clearTestingText) testRequests.invalidateCurrent()
        _uiState.update { state ->
            val currentConn = state.connectionUi
            val isScanningNow = currentConn.connectionStatus == ConnectionStatus.SCANNING_BOOST &&
                boostScanJob?.isActive == true
            val resolvedStatus = when {
                running -> ConnectionStatus.CONNECTED
                failureMessage != null -> ConnectionStatus.ERROR
                isScanningNow -> ConnectionStatus.SCANNING_BOOST
                else -> ConnectionStatus.DISCONNECTED
            }
            val effectiveConfigId = state.selectedGuid
                ?: currentConn.selectedConfigId
                ?: dataSource.getLastActiveConfigId()
            val summary = currentConn.activeConfigSummary
                ?.takeIf { it.configId == effectiveConfigId }
                ?: dataSource.getActiveConfigSummary(effectiveConfigId)

            state.copy(
                isRunning = running,
                isTesting = testRequests.isTesting,
                status = if (!clearTestingText && state.isRunning == running) state.status
                else if (running) MainStatus.Connected else MainStatus.Disconnected,
                connectionUi = currentConn.copy(
                    connectionStatus = resolvedStatus,
                    selectedConfigId = effectiveConfigId,
                    activeConfigSummary = summary,
                    downloadSpeedBytesPerSec = if (running) currentConn.downloadSpeedBytesPerSec else 0L,
                    uploadSpeedBytesPerSec = if (running) currentConn.uploadSpeedBytesPerSec else 0L,
                    statusMessage = when {
                        failureMessage != null -> failureMessage
                        running && currentConn.connectionStatus == ConnectionStatus.CONNECTING -> null
                        else -> currentConn.statusMessage
                    },
                ),
            )
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        boostScanJob?.cancel()
        transitionJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val TEST_RESULT_FLUSH_INTERVAL_MS = 500L
        const val STOP_CONFIRMATION_TIMEOUT_MS = 2_500L

        fun buildInitialUiState(dataSource: MainDataSource): MainUiState {
            val selectedGuid = dataSource.getSelectServer()
            val lastActiveConfigId = dataSource.getLastActiveConfigId()
            val effectiveGuid = selectedGuid ?: lastActiveConfigId
            val activeMode = dataSource.getPersistedConnectionMode()
            return MainUiState(
                selectedGroupId = dataSource.getSelectedSubscriptionId(),
                selectedGuid = selectedGuid,
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
                rgbBorderEnabled = dataSource.getRgbBorderEnabled(),
                connectionUi = ConnectionUiState(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    activeMode = activeMode,
                    selectedConfigId = effectiveGuid,
                    activeConfigSummary = dataSource.getActiveConfigSummary(effectiveGuid),
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    lastPingMs = effectiveGuid?.let { dataSource.resolveNodeLatency(it) },
                ),
            )
        }
    }
}
