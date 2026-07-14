package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/models/calendar_event.py`'s `EventType` — the single unified type covering every
 * calendar entry (there is no separate ad-hoc/reminder split server-side either anymore). */
@Serializable
enum class EventTypeDto {
    @SerialName("birthday") BIRTHDAY,
    @SerialName("wedding_anniversary") WEDDING_ANNIVERSARY,
    @SerialName("anniversary") ANNIVERSARY,
    @SerialName("vacation") VACATION,
    @SerialName("planned_trip") PLANNED_TRIP,
    @SerialName("planned_date") PLANNED_DATE,
    @SerialName("unplanned_date") UNPLANNED_DATE,
    @SerialName("planned_drive") PLANNED_DRIVE,
    @SerialName("unplanned_drive") UNPLANNED_DRIVE,
    @SerialName("reminder") REMINDER,
    @SerialName("custom") CUSTOM,
}

/** Mirrors `app/schemas/intimacy_log.py`. All fields optional — this app always sends the whole
 * object wholesale (never a true partial patch), matching how [CalendarEventUpdateDto] itself
 * always resends every field rather than diffing. */
@Serializable
data class IntimacyLogDto(
    val rating: Int? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("initiated_by") val initiatedBy: String? = null,
    @SerialName("protection_used") val protectionUsed: Boolean? = null,
    val positions: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val rounds: Int? = null,
    @SerialName("orgasmed_by") val orgasmedBy: String? = null,
)

/** Mirrors `app/schemas/calendar.py`. `color`/`recurrence_end_at` aren't part of any Android
 * screen yet, so they're simply absent here — an absent field on update leaves the server's
 * value untouched (`exclude_unset=True`), and on create it falls back to the schema's own
 * default (`None`/empty list). */
@Serializable
data class CalendarEventCreateDto(
    val type: EventTypeDto,
    val title: String,
    val description: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("all_day") val allDay: Boolean,
    val location: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String? = null,
    val cancelled: Boolean = false,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("reminder_minutes_before") val reminderMinutesBefore: List<Int> = emptyList(),
    val intimacy: IntimacyLogDto? = null,
    @SerialName("attachment_media_ids") val attachmentMediaIds: List<String> = emptyList(),
    @SerialName("cover_media_id") val coverMediaId: String? = null,
)

@Serializable
data class CalendarEventUpdateDto(
    val type: EventTypeDto,
    val title: String,
    val description: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("all_day") val allDay: Boolean,
    val location: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String? = null,
    val cancelled: Boolean = false,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("reminder_minutes_before") val reminderMinutesBefore: List<Int> = emptyList(),
    val intimacy: IntimacyLogDto? = null,
    @SerialName("attachment_media_ids") val attachmentMediaIds: List<String> = emptyList(),
    @SerialName("cover_media_id") val coverMediaId: String? = null,
)

/** Mirrors `CalendarEventOut`. `recurrence_end_at`/`color` are read but not surfaced anywhere yet
 * — no local entity field to store them in, so a value set via some other client would simply
 * not display here. */
@Serializable
data class CalendarEventOutDto(
    val id: String,
    @SerialName("created_by") val createdBy: String,
    val type: EventTypeDto,
    val title: String,
    val description: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("all_day") val allDay: Boolean,
    val location: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String? = null,
    val cancelled: Boolean = false,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("reminder_minutes_before") val reminderMinutesBefore: List<Int> = emptyList(),
    val intimacy: IntimacyLogDto? = null,
    val attachments: List<MediaAssetOutDto> = emptyList(),
    @SerialName("cover_media") val coverMedia: MediaAssetOutDto? = null,
    @SerialName("created_at") val createdAt: String,
)
