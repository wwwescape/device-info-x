package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/models/period.py`'s `FlowIntensity`. */
@Serializable
enum class FlowIntensityDto {
    @SerialName("light") LIGHT,
    @SerialName("medium") MEDIUM,
    @SerialName("heavy") HEAVY,
}

/** Mirrors `app/schemas/period.py`'s `PeriodDayLog*` — one row per logged day, not per whole
 * period. */
@Serializable
data class PeriodDayLogCreateDto(
    @SerialName("log_date") val logDate: String,
    val symptoms: List<String> = emptyList(),
    @SerialName("flow_intensity") val flowIntensity: FlowIntensityDto? = null,
    val notes: String? = null,
)

@Serializable
data class PeriodDayLogUpdateDto(
    @SerialName("log_date") val logDate: String,
    val symptoms: List<String> = emptyList(),
    @SerialName("flow_intensity") val flowIntensity: FlowIntensityDto? = null,
    val notes: String? = null,
)

@Serializable
data class PeriodDayLogOutDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("log_date") val logDate: String,
    val symptoms: List<String> = emptyList(),
    @SerialName("flow_intensity") val flowIntensity: FlowIntensityDto? = null,
    val notes: String? = null,
)
