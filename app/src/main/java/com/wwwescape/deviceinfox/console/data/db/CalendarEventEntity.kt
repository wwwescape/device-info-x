package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The single, unified calendar entry — every [EventType] lives here; there is no separate
 * ad-hoc/reminder split anymore. [recurrenceRule] is a raw RFC5545 RRULE string (or null for a
 * one-off entry) — see [com.wwwescape.deviceinfox.console.ui.calendar.RecurrencePreset] for the
 * simple picker this app's UI exposes on top of it. [reminderMinutesBefore] mirrors the server's
 * `CalendarEventReminder` rows (a plain list, not a boolean) — the Notification toggle just
 * seeds/clears this list at the UI layer with the canned `[1440, 5]` pair.
 *
 * The `intimacy*` fields flatten the server's separate, 1:1 `IntimacyLog` row directly onto this
 * entity (rather than a second Room entity/DAO) — only ever populated for the 5 eligible types
 * (see `EventEligibility.kt`), all-null/empty otherwise. */
@Entity(tableName = "calendar_events", indices = [Index("startAtEpochMillis")])
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val type: EventType,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val isAllDay: Boolean = true,
    val location: String?,
    val recurrenceRule: String?,
    val cancelled: Boolean = false,
    val cancellationReason: String? = null,
    /** A real partner id (one of the pair's own two) or [CANCELLED_BY_BOTH] — same shape as
     * [intimacyInitiatedByPartnerId]. */
    val cancelledByPartnerId: String? = null,
    val reminderMinutesBefore: List<Int> = emptyList(),
    val intimacyRating: Int? = null,
    val intimacyDurationMinutes: Int? = null,
    val intimacyInitiatedByPartnerId: String? = null,
    val intimacyProtectionUsed: Boolean? = null,
    val intimacyPositions: List<String> = emptyList(),
    val intimacyLocations: List<String> = emptyList(),
    val intimacyMoods: List<String> = emptyList(),
    val intimacyRounds: Int? = null,
    val intimacyOrgasmedByPartnerId: String? = null,
    val createdByPartnerId: String,
    val createdAtEpochMillis: Long,
    /** The event's single distinguished cover photo — a plain nullable pair of columns rather
     * than a join-table row like [com.wwwescape.deviceinfox.console.data.db.CalendarAttachmentEntity],
     * since there's exactly one (or none) per event. [coverFilePath] is the already-downloaded
     * local file (cover photos are eager-downloaded like image attachments, never lazy) — see
     * `CalendarRepository.resolveCoverPhoto`. */
    val coverMediaId: String? = null,
    val coverFilePath: String? = null,
)
