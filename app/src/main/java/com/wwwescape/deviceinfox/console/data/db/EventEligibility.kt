package com.wwwescape.deviceinfox.console.data.db

import java.util.Calendar

/** The 5 types an Intimacy Log may be attached to — mirrors the server's
 * `INTIMACY_ELIGIBLE_TYPES` in `app/services/calendar_service.py`. Also exactly the 5 types whose
 * *displayed* label can differ from their stored [EventType] once past (see
 * [com.wwwescape.deviceinfox.console.ui.calendar.displayLabelRes]) — Planned Date/Planned Drive
 * relabel to Date/Drive, while Unplanned Date/Unplanned Drive/Custom keep their label but become
 * eligible for logging. */
val INTIMACY_ELIGIBLE_TYPES: Set<EventType> = setOf(
    EventType.PLANNED_DATE,
    EventType.UNPLANNED_DATE,
    EventType.PLANNED_DRIVE,
    EventType.UNPLANNED_DRIVE,
    EventType.CUSTOM,
)

/** The date an event's "has this passed?" status is judged against — the end of its span if it
 * has one, otherwise its start. */
fun referenceDateEpochMillis(startAtEpochMillis: Long, endAtEpochMillis: Long?): Long =
    endAtEpochMillis ?: startAtEpochMillis

fun isPastOrToday(referenceDateEpochMillis: Long): Boolean {
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfReferenceDay = Calendar.getInstance().apply {
        timeInMillis = referenceDateEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return !startOfReferenceDay.after(startOfToday)
}

fun isIntimacyEligible(type: EventType, referenceDateEpochMillis: Long): Boolean =
    type in INTIMACY_ELIGIBLE_TYPES && isPastOrToday(referenceDateEpochMillis)
