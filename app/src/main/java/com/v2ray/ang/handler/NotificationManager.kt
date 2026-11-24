package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.TrafficSpeedStat
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

internal object TrafficTelemetryCalculator {
    data class TrafficBucket(
        val proxyUplink: Long = 0L,
        val proxyDownlink: Long = 0L,
        val directUplink: Long = 0L,
        val directDownlink: Long = 0L,
    ) {
        val totalUplink: Long get() = proxyUplink + directUplink
        val totalDownlink: Long get() = proxyDownlink + directDownlink
        val proxyTotal: Long get() = proxyUplink + proxyDownlink
        val directTotal: Long get() = directUplink + directDownlink
    }

    fun aggregate(stats: List<OutboundTrafficStat>): TrafficBucket {
        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        stats.forEach { stat ->
            val safeValue = stat.value.coerceAtLeast(0L)
            when {
                stat.tag == AppConfig.TAG_DIRECT -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> directUplink += safeValue
                        AppConfig.DOWNLINK -> directDownlink += safeValue
                    }
                }

                stat.tag != AppConfig.TAG_BLOCKED -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += safeValue
                        AppConfig.DOWNLINK -> proxyDownlink += safeValue
                    }
                }
            }
        }
        return TrafficBucket(
            proxyUplink = proxyUplink,
            proxyDownlink = proxyDownlink,
            directUplink = directUplink,
            directDownlink = directDownlink,
        )
    }

    fun toSpeedPerSec(bucket: TrafficBucket, elapsedMs: Long): TrafficSpeedStat {
        val safeSeconds = max(elapsedMs, 100L) / 1000.0
        return TrafficSpeedStat(
            downloadBytesPerSec = (bucket.totalDownlink / safeSeconds).toLong().coerceAtLeast(0L),
            uploadBytesPerSec = (bucket.totalUplink / safeSeconds).toLong().coerceAtLeast(0L),
        )
    }
}

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000
    private const val TELEMETRY_INTERVAL_MS = 1000L
    private const val NOTIFICATION_NOTIFY_INTERVAL_MS = 3000L

    @Volatile
    private var uiTelemetryActive = false

    @Volatile
    private var lastQueryTime = 0L

    @Volatile
    private var lastNotificationNotifyTime = 0L
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    fun setUiTelemetryActive(active: Boolean) {
        uiTelemetryActive = active
        if (active && CoreServiceManager.isRunning()) {
            startSpeedNotification()
        } else if (!active && !isSpeedNotificationEnabled()) {
            stopSpeedNotification(resetNotificationText = false)
        }
    }

    private fun isSpeedNotificationEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true

    /**
     * Starts unified 1-second traffic telemetry for UI speed bars and optional 3-second
     * speed notifications without splitting core counter resets across multiple pollers.
     */
    fun startSpeedNotification() {
        val showSpeedNotif = isSpeedNotificationEnabled()
        if (!showSpeedNotif && !uiTelemetryActive) return
        if (speedNotificationJob?.isActive == true || !CoreServiceManager.isRunning()) return

        var lastZeroSpeed = false
        var accProxyUp = 0L
        var accProxyDown = 0L
        var accDirectUp = 0L
        var accDirectDown = 0L
        lastQueryTime = System.currentTimeMillis()
        lastNotificationNotifyTime = lastQueryTime

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && CoreServiceManager.isRunning()) {
                delay(TELEMETRY_INTERVAL_MS)
                if (!isActive || !CoreServiceManager.isRunning()) break

                val now = System.currentTimeMillis()
                val elapsedMs = (now - lastQueryTime).coerceAtLeast(250L)
                lastQueryTime = now

                val bucket = TrafficTelemetryCalculator.aggregate(
                    CoreServiceManager.queryAllOutboundTrafficStats()
                )
                if (uiTelemetryActive) {
                    val speedStat = TrafficTelemetryCalculator.toSpeedPerSec(bucket, elapsedMs)
                    getService()?.let { service ->
                        MessageHelper.sendMsg2UI(service, AppConfig.MSG_TRAFFIC_STATS, speedStat)
                    }
                }

                if (isSpeedNotificationEnabled()) {
                    accProxyUp += bucket.proxyUplink
                    accProxyDown += bucket.proxyDownlink
                    accDirectUp += bucket.directUplink
                    accDirectDown += bucket.directDownlink

                    val notifElapsedMs = now - lastNotificationNotifyTime
                    if (notifElapsedMs >= NOTIFICATION_NOTIFY_INTERVAL_MS) {
                        val notifBucket = TrafficTelemetryCalculator.TrafficBucket(
                            proxyUplink = accProxyUp,
                            proxyDownlink = accProxyDown,
                            directUplink = accDirectUp,
                            directDownlink = accDirectDown,
                        )
                        lastZeroSpeed = updateSpeedNotificationWindow(
                            bucket = notifBucket,
                            elapsedMs = notifElapsedMs,
                            lastZeroSpeed = lastZeroSpeed
                        )
                        accProxyUp = 0L
                        accProxyDown = 0L
                        accDirectUp = 0L
                        accDirectDown = 0L
                        lastNotificationNotifyTime = now
                    }
                }
            }
        }
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks ?: service.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_restore_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Fulfills or refreshes the foreground-service contract before a start command can
     * return early. A duplicate startForegroundService call still requires the service
     * to enter foreground state promptly, even when the core is already running.
     */
    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
    }

    /**
     * Stops the speed notification and telemetry loop.
     */
    fun stopSpeedNotification(resetNotificationText: Boolean = true) {
        speedNotificationJob?.let {
            it.cancel()
            speedNotificationJob = null
            if (resetNotificationText && isSpeedNotificationEnabled()) {
                updateNotification("", 0, 0)
            }
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        NotificationHelper.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelNameRes = R.string.notification_channel_service,
            // Foreground-service notifications must remain visible; LOW is silent but valid.
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            lightColor = Color.DKGRAY
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Appends the speed string to the given text.
     * @param text The text to append to.
     * @param name The name of the tag.
     * @param up The uplink speed.
     * @param down The downlink speed.
     */
    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.take(min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    private fun updateSpeedNotificationWindow(
        bucket: TrafficTelemetryCalculator.TrafficBucket,
        elapsedMs: Long,
        lastZeroSpeed: Boolean
    ): Boolean {
        val elapsedSeconds = max(elapsedMs, 100L) / 1000.0
        val proxyTotal = bucket.proxyTotal
        val directTotal = bucket.directTotal
        val zeroSpeed = proxyTotal + directTotal == 0L
        if (!zeroSpeed || !lastZeroSpeed) {
            val text = StringBuilder()
            appendSpeedString(
                text, AppConfig.TAG_PROXY,
                bucket.proxyUplink / elapsedSeconds,
                bucket.proxyDownlink / elapsedSeconds
            )

            appendSpeedString(
                text, AppConfig.TAG_DIRECT,
                bucket.directUplink / elapsedSeconds,
                bucket.directDownlink / elapsedSeconds
            )
            updateNotification(text.toString(), proxyTotal, directTotal)
        }
        return zeroSpeed
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
