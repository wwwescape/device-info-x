package com.wwwescape.deviceinfox.console.data.calendar

import com.wwwescape.deviceinfox.console.data.db.EventType
import com.wwwescape.deviceinfox.console.data.db.isIntimacyEligible
import com.wwwescape.deviceinfox.console.data.db.referenceDateEpochMillis
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment

/** A shared-agenda entry — every [EventType] uses this same shape now (no more separate
 * ad-hoc/reminder split). [displayAtEpochMillis] is what the agenda sorts and groups-by-month on
 * — for a recurring entry this is the *next* occurrence, recomputed at read time (see
 * [CalendarRepository]'s `computeNextOccurrence`), not the original [startAtEpochMillis].
 *
 * The `intimacy*` fields are only ever populated when [isIntimacyEligible] holds — see
 * `EventEligibility.kt`. */
data class CalendarItem(
    val id: String,
    val title: String,
    val notes: String?,
    val type: EventType,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long?,
    val isAllDay: Boolean,
    val location: String?,
    val recurrenceRule: String?,
    val cancelled: Boolean,
    val cancellationReason: String?,
    val reminderMinutesBefore: List<Int>,
    val intimacyRating: Int?,
    val intimacyDurationMinutes: Int?,
    val intimacyInitiatedByPartnerId: String?,
    val intimacyProtectionUsed: Boolean?,
    val intimacyPositions: List<String>,
    val intimacyLocations: List<String>,
    val intimacyMoods: List<String>,
    val intimacyRounds: Int?,
    val intimacyOrgasmedByPartnerId: String?,
    val displayAtEpochMillis: Long,
    val attachments: List<MessageAttachment> = emptyList(),
    /** The event's single distinguished cover photo — null until one's been set, or once
     * downloaded (cover photos are eager-downloaded, same as image attachments; see
     * `CalendarRepository.resolveCoverPhoto`). Rendered as the hero banner's background in
     * [com.wwwescape.deviceinfox.console.ui.calendar.EventEditorDialog]/`EventDetailScreen`, and
     * as a faint background behind the agenda card in `CalendarScreen`'s `AgendaItemRow`. */
    val coverPhotoFilePath: String? = null,
    val coverMediaId: String? = null,
) {
    /** Whether this entry has a notification schedule at all — drives the Notification toggle
     * and any "has reminders" indicator in the agenda list. */
    val hasNotification: Boolean get() = reminderMinutesBefore.isNotEmpty()

    /** Whether [recurrenceRule] is set — drives the agenda's "Repeat" badge, same as the old
     * `isRecurringYearly` flag did. */
    val isRecurring: Boolean get() = recurrenceRule != null

    /** Whether this event's *own* date/type combination currently permits an Intimacy Log —
     * judged against the real "now", not [displayAtEpochMillis] (which is shifted forward to a
     * recurring entry's *next* occurrence — irrelevant here, since none of the 5 eligible types
     * ever recur). */
    val isIntimacyEligible: Boolean
        get() = isIntimacyEligible(type, referenceDateEpochMillis(startAtEpochMillis, endAtEpochMillis))

    /** Whether anything was actually logged — [isIntimacyEligible] only says logging is
     * *allowed*, not that it happened. */
    val hasIntimacyLogged: Boolean
        get() = intimacyRating != null || intimacyDurationMinutes != null || intimacyInitiatedByPartnerId != null ||
            intimacyProtectionUsed != null || intimacyPositions.isNotEmpty() || intimacyLocations.isNotEmpty() ||
            intimacyMoods.isNotEmpty() || intimacyRounds != null || intimacyOrgasmedByPartnerId != null
}
