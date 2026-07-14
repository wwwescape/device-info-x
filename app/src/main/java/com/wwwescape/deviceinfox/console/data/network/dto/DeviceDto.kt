package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/device.py`'s `DeviceRegisterRequest`. */
@Serializable
data class DeviceRegisterRequestDto(
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String? = null,
)
