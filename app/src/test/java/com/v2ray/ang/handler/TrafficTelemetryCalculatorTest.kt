package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.OutboundTrafficStat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficTelemetryCalculatorTest {

    @Test
    fun aggregateSeparatesProxyDirectAndIgnoresBlocked() {
        val stats = listOf(
            OutboundTrafficStat(tag = AppConfig.TAG_PROXY, direction = AppConfig.DOWNLINK, value = 10_000L),
            OutboundTrafficStat(tag = AppConfig.TAG_PROXY, direction = AppConfig.UPLINK, value = 2_500L),
            OutboundTrafficStat(tag = AppConfig.TAG_DIRECT, direction = AppConfig.DOWNLINK, value = 1_000L),
            OutboundTrafficStat(tag = AppConfig.TAG_DIRECT, direction = AppConfig.UPLINK, value = 500L),
            OutboundTrafficStat(tag = AppConfig.TAG_BLOCKED, direction = AppConfig.DOWNLINK, value = 99_999L),
        )

        val bucket = TrafficTelemetryCalculator.aggregate(stats)
        assertEquals(11_000L, bucket.totalDownlink)
        assertEquals(3_000L, bucket.totalUplink)
        assertEquals(12_500L, bucket.proxyTotal)
        assertEquals(1_500L, bucket.directTotal)
    }

    @Test
    fun toSpeedPerSecScalesByElapsedMillis() {
        val bucket = TrafficTelemetryCalculator.TrafficBucket(
            proxyDownlink = 4_096L,
            directDownlink = 1_024L,
            proxyUplink = 2_048L,
            directUplink = 512L,
        )

        val oneSecondStat = TrafficTelemetryCalculator.toSpeedPerSec(bucket, elapsedMs = 1_000L)
        assertEquals(5_120L, oneSecondStat.downloadBytesPerSec)
        assertEquals(2_560L, oneSecondStat.uploadBytesPerSec)

        val halfSecondStat = TrafficTelemetryCalculator.toSpeedPerSec(bucket, elapsedMs = 500L)
        assertEquals(10_240L, halfSecondStat.downloadBytesPerSec)
        assertEquals(5_120L, halfSecondStat.uploadBytesPerSec)
    }
}
