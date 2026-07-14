package com.wwwescape.deviceinfox.console.data.network.dto

import java.util.Calendar
import java.util.Locale

/** The server's `date` fields (birthday, calendar/event/cycle dates) serialize as plain
 * `YYYY-MM-DD` strings — these convert to/from the epoch-millis-at-local-midnight convention
 * every Android-side entity in this console already uses (see e.g. `CalendarRepository`). No
 * `java.time` here since minSdk 24 has no desugaring configured for it. */
fun Long.toIsoDateString(): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@toIsoDateString }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

fun String.isoDateStringToEpochMillis(): Long? {
    val parts = split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
    }.timeInMillis
}
