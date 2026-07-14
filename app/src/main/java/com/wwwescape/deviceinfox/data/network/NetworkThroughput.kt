package com.wwwescape.deviceinfox.data.network

/** A single real device-wide throughput sample, from [NetworkRepository.trafficUpdates]. */
data class NetworkThroughput(
    val downMbps: Float,
    val upMbps: Float,
)
