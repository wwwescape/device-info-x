package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/location.py`. */
@Serializable
data class LocationUpdateDto(
    val lat: Double,
    val lng: Double,
    @SerialName("accuracy_m") val accuracyM: Double? = null,
)

@Serializable
data class PartnerLocationStatusDto(
    @SerialName("user_id") val userId: String,
    val sharing: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("accuracy_m") val accuracyM: Double? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LocationStatusResponseDto(
    @SerialName("self_status") val selfStatus: PartnerLocationStatusDto,
    @SerialName("partner_status") val partnerStatus: PartnerLocationStatusDto? = null,
)
