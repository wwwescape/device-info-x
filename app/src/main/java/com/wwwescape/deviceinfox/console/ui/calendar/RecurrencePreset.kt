package com.wwwescape.deviceinfox.console.ui.calendar

import com.wwwescape.deviceinfox.R

/** The simple recurring picker the UI exposes — maps onto
 * [com.wwwescape.deviceinfox.console.data.db.CalendarEventEntity.recurrenceRule]'s raw RFC5545
 * RRULE string. The server accepts arbitrary RRULEs (BYDAY, intervals, etc.); this app only ever
 * sends/reads these five canned forms. */
enum class RecurrencePreset(val labelRes: Int) {
    NONE(R.string.console_calendar_frequency_none),
    DAILY(R.string.console_calendar_frequency_daily),
    WEEKLY(R.string.console_calendar_frequency_weekly),
    MONTHLY(R.string.console_calendar_frequency_monthly),
    YEARLY(R.string.console_calendar_frequency_yearly),
}

fun RecurrencePreset.toRecurrenceRule(): String? = when (this) {
    RecurrencePreset.NONE -> null
    RecurrencePreset.DAILY -> "FREQ=DAILY"
    RecurrencePreset.WEEKLY -> "FREQ=WEEKLY"
    RecurrencePreset.MONTHLY -> "FREQ=MONTHLY"
    RecurrencePreset.YEARLY -> "FREQ=YEARLY"
}

/** Anything other than the five canned forms (a custom RRULE from some other client, or none)
 * falls back to [RecurrencePreset.NONE] — this app has no UI for arbitrary RRULEs. */
fun String?.toRecurrencePreset(): RecurrencePreset = when (this) {
    "FREQ=DAILY" -> RecurrencePreset.DAILY
    "FREQ=WEEKLY" -> RecurrencePreset.WEEKLY
    "FREQ=MONTHLY" -> RecurrencePreset.MONTHLY
    "FREQ=YEARLY" -> RecurrencePreset.YEARLY
    else -> RecurrencePreset.NONE
}
