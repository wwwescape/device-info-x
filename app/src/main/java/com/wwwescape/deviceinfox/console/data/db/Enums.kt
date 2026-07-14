package com.wwwescape.deviceinfox.console.data.db

/** Drives Period Tracker visibility per the design spec: both MALE -> hidden entirely, one
 * FEMALE -> single cycle shown, both FEMALE -> two independently tracked cycles. Profile
 * configuration, not a hardcoded assumption. */
enum class PartnerGender { MALE, FEMALE, UNSPECIFIED }

/** The single, unified calendar entry type — [com.wwwescape.deviceinfox.console.data.db.CalendarEventEntity]
 * covers every one of these; there is no separate ad-hoc/reminder split anymore. */
enum class EventType {
    BIRTHDAY, WEDDING_ANNIVERSARY, ANNIVERSARY, VACATION, PLANNED_TRIP,
    PLANNED_DATE, UNPLANNED_DATE, PLANNED_DRIVE, UNPLANNED_DRIVE, REMINDER, CUSTOM,
}

/** Vacation/Planned Trip/Planned Date/Unplanned Date/Planned Drive/Unplanned Drive are always
 * one-off; birthdays, anniversaries, reminders, and custom entries may optionally recur. The
 * single source of truth for that rule, reused by
 * [com.wwwescape.deviceinfox.console.ui.calendar.EventEditorDialog]'s recurring picker and
 * [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository.saveEvent]'s clamp. */
val EventType.supportsRecurrence: Boolean
    get() = this == EventType.BIRTHDAY ||
        this == EventType.WEDDING_ANNIVERSARY ||
        this == EventType.ANNIVERSARY ||
        this == EventType.REMINDER ||
        this == EventType.CUSTOM

/** Only these 4 types may ever be marked cancelled — mirrors the server's `CANCELLABLE_TYPES` in
 * `app/models/calendar_event.py`. The single source of truth reused by [EventEditorDialog][com.wwwescape.deviceinfox.console.ui.calendar.EventEditorDialog]'s
 * Cancelled toggle and [CalendarRepository][com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository.saveEvent]'s clamp. */
val EventType.supportsCancellation: Boolean
    get() = this == EventType.PLANNED_DATE ||
        this == EventType.PLANNED_DRIVE ||
        this == EventType.PLANNED_TRIP ||
        this == EventType.CUSTOM

/** Sentinel for [CalendarEventEntity.cancelledByPartnerId] meaning "both partners cancelled it
 * together" — the other valid values are a real partner id. Mirrors the server's
 * `CANCELLED_BY_BOTH` in `app/models/calendar_event.py`; own constant rather than reusing
 * [INTIMACY_INITIATED_BY_BOTH]'s identical string value, matching that constant's own "one
 * sentinel per field/concern" precedent. */
const val CANCELLED_BY_BOTH = "both"

enum class CycleFlowIntensity { LIGHT, MEDIUM, HEAVY }

/** Send/delivery status for a [MessageEntity]. [SENDING] and [FAILED] are local-only — the
 * server has no notion of "sending"; a message either exists there or the send failed — and only
 * ever apply to the optimistic local row a send creates before the server round-trip resolves
 * it, so the composer can show something immediately instead of the chat looking like the tap
 * did nothing. [SENT] and [DELIVERED] both come from the server ([MessageRepository]'s
 * `deliveredAt`-driven mapping in `toMessageEntity`/the `message.delivered` WebSocket event) —
 * [SENT] means the server has the message but the partner's device hasn't acked it yet,
 * [DELIVERED] means it has (read is tracked separately via [MessageEntity.isRead], since a read
 * message is always also delivered). */
enum class MessageDeliveryState { SENT, SENDING, DELIVERED, FAILED }

/** [PRIVATE] entries are visible/editable only by the device's own owner; [SHARED] entries are
 * visible/editable by both paired users — see [NotepadEntryEntity]'s own doc comment. */
enum class NotepadEntryType { PRIVATE, SHARED }
