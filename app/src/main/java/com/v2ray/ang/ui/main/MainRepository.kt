package com.v2ray.ang.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.TrafficSpeedStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MainRepository(
    private val app: AngApplication
) : MainDataSource {

    private val localizedContext: Context
        get() = AppLocaleManager.localizedContext(app)

    private val closed = AtomicBoolean(false)

    // Probe results are finite and must remain lossless until the ViewModel coalesces them.
    private val mainServiceEventChannel = Channel<MainServiceEvent>(Channel.UNLIMITED)
    private val _serviceEvents = MutableSharedFlow<MainServiceEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val nodeFailureCounts = ConcurrentHashMap<String, Int>()

    override val mainServiceEvent: Flow<MainServiceEvent> = mainServiceEventChannel.receiveAsFlow()
    override val serviceEvents: SharedFlow<MainServiceEvent> = _serviceEvents.asSharedFlow()

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            val event = when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> MainServiceEvent.StateRunning
                AppConfig.MSG_STATE_NOT_RUNNING -> MainServiceEvent.StateNotRunning
                AppConfig.MSG_STATE_START_SUCCESS -> MainServiceEvent.StateStartSuccess
                AppConfig.MSG_STATE_START_FAILURE -> MainServiceEvent.StateStartFailure(
                    safeIntent.getStringExtra("content")
                )

                AppConfig.MSG_STATE_STOP_SUCCESS -> MainServiceEvent.StateStopSuccess
                AppConfig.MSG_TRAFFIC_STATS -> safeIntent
                    .serializable<TrafficSpeedStat>("content")
                    ?.let { MainServiceEvent.TrafficSpeed(it) }

                AppConfig.MSG_MEASURE_DELAY_RESULT -> safeIntent
                    .serializable<ConnectionTestResult>("content")
                    ?.let { MainServiceEvent.MeasureDelayResult(it, requestId) }
                AppConfig.MSG_MEASURE_DELAY_CANCEL -> MainServiceEvent.MeasureDelayCancelled(requestId)

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> safeIntent
                    .serializable<RealPingResult>("content")
                    ?.let { MainServiceEvent.MeasureConfigSuccess(it, requestId) }
                AppConfig.MSG_MEASURE_CONFIG_NOTIFY -> MainServiceEvent.MeasureConfigNotify(
                    safeIntent.getStringExtra("content").orEmpty(), requestId
                )

                AppConfig.MSG_MEASURE_CONFIG_FINISH -> MainServiceEvent.MeasureConfigFinish(
                    requestId
                )
                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> MainServiceEvent.MeasureConfigCancelled(requestId)

                else -> null
            }
            event?.let {
                _serviceEvents.tryEmit(it)
                mainServiceEventChannel.trySend(it)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            MessageHelper.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "")
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to unregister service client", it)
        }
        runCatching {
            app.unregisterReceiver(serviceReceiver)
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Failed to unregister main service receiver", it)
        }
        mainServiceEventChannel.close()
    }

    override fun getSelectedSubscriptionId(): String =
        MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    override fun setSelectedSubscriptionId(id: String) {
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, id)
    }

    override fun getSelectServer(): String? = MmkvManager.getSelectServer()

    override fun setSelectServer(guid: String) = MmkvManager.setSelectServer(guid)

    override fun getPersistedConnectionMode(): ConnectionMode = ConnectionMode.fromPref(
        MmkvManager.decodeSettingsString(AppConfig.PREF_CONNECTION_MODE, ConnectionMode.NORMAL.prefValue)
    )

    override fun setPersistedConnectionMode(mode: ConnectionMode) {
        MmkvManager.encodeSettings(AppConfig.PREF_CONNECTION_MODE, mode.prefValue)
    }

    override fun getLastActiveConfigId(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_LAST_ACTIVE_CONFIG_ID, null)
            ?.takeIf { it.isNotBlank() }

    override fun setLastActiveConfigId(guid: String?) {
        MmkvManager.encodeSettings(AppConfig.PREF_LAST_ACTIVE_CONFIG_ID, guid.orEmpty())
    }

    override fun getBoostSubscriptionGroups(): List<SubscriptionGroup> {
        val standaloneTitle = localizedContext.getString(R.string.connection_standalone_group)
        val subs = MmkvManager.decodeSubscriptions()
        val result = ArrayList<SubscriptionGroup>(subs.size + 1)
        val coveredGroupIds = HashSet<String>(subs.size + 1)
        var orderCounter = 0

        fun buildNodes(guids: List<String>, subId: String, subName: String): List<ProxyNode> =
            guids.mapNotNull { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                val affiliation = MmkvManager.decodeServerAffiliationInfo(guid)
                ProxyNode(
                    id = guid,
                    name = profile.remarks.ifBlank { profile.server.orEmpty().ifBlank { guid } },
                    protocolTag = formatProtocolTag(profile),
                    serverAddress = profile.server.orEmpty(),
                    port = profile.serverPort?.toIntOrNull(),
                    subscriptionId = subId,
                    subscriptionName = subName,
                    cachedLatencyMs = affiliation?.testDelayMillis ?: 0L,
                    recentFailureCount = nodeFailureCounts[guid] ?: 0,
                    priorityOrder = orderCounter++,
                )
            }

        subs.forEach { subCache ->
            val subId = subCache.guid
            coveredGroupIds.add(subId)
            val subName = subCache.subscription.remarks.ifBlank { standaloneTitle }
            val nodes = buildNodes(MmkvManager.decodeServerList(subId), subId, subName)
            if (nodes.isNotEmpty()) {
                result += SubscriptionGroup(
                    id = subId,
                    name = subName,
                    isEnabled = subCache.subscription.enabled,
                    nodes = nodes,
                )
            }
        }

        if (AppConfig.DEFAULT_SUBSCRIPTION_ID !in coveredGroupIds) {
            val standaloneNodes = buildNodes(
                guids = MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID),
                subId = AppConfig.DEFAULT_SUBSCRIPTION_ID,
                subName = standaloneTitle,
            )
            if (standaloneNodes.isNotEmpty()) {
                result += SubscriptionGroup(
                    id = AppConfig.DEFAULT_SUBSCRIPTION_ID,
                    name = standaloneTitle,
                    isEnabled = true,
                    nodes = standaloneNodes,
                )
            }
        }

        return result
    }

    override fun getActiveConfigSummary(guid: String?): ActiveConfigSummary? {
        if (guid.isNullOrBlank()) return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val subId = profile.subscriptionId
        val subName = if (subId.isNotBlank()) {
            MmkvManager.decodeSubscription(subId)?.remarks?.takeIf { it.isNotBlank() }
        } else {
            null
        } ?: localizedContext.getString(R.string.connection_standalone_group)

        return ActiveConfigSummary(
            configId = guid,
            name = profile.remarks.ifBlank { profile.server.orEmpty().ifBlank { guid } },
            protocolTag = formatProtocolTag(profile),
            subscriptionId = subId,
            subscriptionName = subName,
        )
    }

    override fun resolveNodeLatency(guid: String): Long? =
        MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis?.takeIf { it > 0L }

    override fun recordNodeConnectionOutcome(guid: String, succeeded: Boolean) {
        if (guid.isBlank()) return
        if (succeeded) {
            nodeFailureCounts.remove(guid)
        } else {
            nodeFailureCounts.compute(guid) { _, prev -> ((prev ?: 0) + 1).coerceAtMost(5) }
        }
    }

    private fun formatProtocolTag(profile: ProfileItem): String {
        val type = profile.configType.name
        val transport = profile.network?.takeIf { it.isNotBlank() }
        val security = profile.security?.takeIf { it.isNotBlank() && it != "none" }
        return listOfNotNull(type, transport, security).joinToString(" / ")
    }

    override fun getConfirmRemove(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    override fun getDoubleColumnDisplay(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)

    override fun getRgbBorderEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_RGB_BORDER_ENABLED, true)

    override fun isGroupAllDisplayEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_GROUP_ALL_DISPLAY)

    override fun getString(resId: Int): String = localizedContext.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        localizedContext.getString(resId, *formatArgs)

    override fun getSubscriptions(): List<SubscriptionCache> {
        val result = mutableListOf<SubscriptionCache>()
        if (isGroupAllDisplayEnabled()) {
            result += SubscriptionCache(
                guid = "",
                subscription = SubscriptionItem().apply {
                    remarks = localizedContext.getString(R.string.filter_config_all)
                }
            )
        }
        result += MmkvManager.decodeSubscriptions()
        return result
    }

    override fun getSubscriptionItem(id: String): SubscriptionItem? =
        MmkvManager.decodeSubscription(id)

    override fun getServerGuidList(groupId: String): List<String> =
        if (groupId.isEmpty()) {
            MmkvManager.decodeAllServerList()
        } else {
            MmkvManager.decodeServerList(groupId)
        }

    override fun decodeServerConfig(guid: String): ProfileItem? =
        MmkvManager.decodeServerConfig(guid)

    override fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo? =
        MmkvManager.decodeServerAffiliationInfo(guid)

    override fun encodeServerList(guids: List<String>, groupId: String) =
        MmkvManager.encodeServerList(ArrayList(guids), groupId)

    override fun removeServer(guid: String) = MmkvManager.removeServer(guid)

    override fun removeAllServer(): Int = MmkvManager.removeAllServer()

    override fun removeInvalidServerByGuid(guid: String): Int =
        MmkvManager.removeInvalidServer(guid)

    override fun removeInvalidServersInGroup(groupId: String): Int =
        if (groupId.isEmpty()) {
            MmkvManager.removeInvalidServer("")
        } else {
            getServerGuidList(groupId).sumOf(::removeInvalidServerByGuid)
        }

    override fun clearAllTestDelayResults(guids: List<String>) =
        MmkvManager.clearAllTestDelayResults(guids)

    override fun sortByTestResultsForSub(subId: String) {
        AngConfigManager.sortByTestResultsForSub(subId)
    }

    override fun getSubsList(): List<String> = MmkvManager.decodeSubsList()

    override suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int> = AngConfigManager.importBatchConfig(server, subscriptionId, updateUI)

    override fun updateConfigViaSubAll(): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSubAll()

    override fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult =
        AngConfigManager.updateConfigViaSub(subscriptionCache)

    override fun shareNonCustomConfigsToClipboard(guids: List<String>): Int =
        AngConfigManager.shareNonCustomConfigsToClipboard(app, guids)

    override fun share2QRCode(guid: String): android.graphics.Bitmap? =
        AngConfigManager.share2QRCode(guid)

    override fun share2Clipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(app, guid) == 0

    override fun sendMsg2Service(msgId: Int, content: String) =
        MessageHelper.sendMsg2Service(app, msgId, content)

    override fun sendMsg2TestService(msg: TestServiceMessage, requestId: String?) =
        MessageHelper.sendMsg2TestService(app, msg, requestId)

    override fun cancelAllPing() {
        sendMsg2TestService(
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
    }

    override fun testCurrentServerRealPing(requestId: String) {
        MessageHelper.sendMsg2ServiceForResult(app, AppConfig.MSG_MEASURE_DELAY, requestId) { handled ->
            if (!handled) {
                val cancelled = MainServiceEvent.MeasureDelayCancelled(requestId)
                _serviceEvents.tryEmit(cancelled)
                mainServiceEventChannel.trySend(cancelled)
            }
        }
    }

    override fun syncSubscriptions() {
        SubscriptionUpdater.sync(app)
    }

    override fun initAssets() {
        SettingsManager.initAssets(app, app.assets)
    }
}
