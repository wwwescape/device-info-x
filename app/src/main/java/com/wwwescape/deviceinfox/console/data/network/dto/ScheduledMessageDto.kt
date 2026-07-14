package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/scheduled_message.py`. Always privately owned by the sender — unlike
 * Notepad there's no shared/private split, so no `type` field at all here. */
@Serializable
data class ScheduledMessageCreateDto(
    val body: String,
    @SerialName("scheduled_at") val scheduledAt: String,
)

@Serializable
data class ScheduledMessageOutDto(
    val id: String,
    val body: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("created_at") val createdAt: String,
)
