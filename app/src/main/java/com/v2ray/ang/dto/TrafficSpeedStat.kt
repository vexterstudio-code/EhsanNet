package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Snapshot of live VPN/proxy throughput emitted once per second from the daemon process.
 */
data class TrafficSpeedStat(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
) : Serializable
