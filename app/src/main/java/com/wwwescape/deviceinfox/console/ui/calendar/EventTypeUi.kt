package com.wwwescape.deviceinfox.console.ui.calendar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.data.db.EventType
import com.wwwescape.deviceinfox.console.data.db.isPastOrToday

/** Single source of truth for how each [EventType] is labeled/iconified — shared by
 * [EventEditorDialog]'s category picker and [CalendarScreen]'s agenda rows, so the two can never
 * drift apart the way the old separate `EventCategory`/`iconFor` definitions could.
 *
 * This is the *static* label — [PLANNED_DATE]/[PLANNED_DRIVE] always read "Planned Date"/
 * "Planned Drive" here regardless of date. Anywhere displaying an actual event should use
 * [displayLabelRes] instead, which auto-relabels those two once their date has passed. */
val EventType.labelRes: Int
    get() = when (this) {
        EventType.BIRTHDAY -> R.string.console_calendar_type_birthday
        EventType.WEDDING_ANNIVERSARY -> R.string.console_calendar_type_wedding_anniversary
        EventType.ANNIVERSARY -> R.string.console_calendar_type_anniversary
        EventType.VACATION -> R.string.console_calendar_type_vacation
        EventType.PLANNED_TRIP -> R.string.console_calendar_type_trip
        EventType.PLANNED_DATE -> R.string.console_calendar_category_planned_date
        EventType.UNPLANNED_DATE -> R.string.console_calendar_type_unplanned_date
        EventType.PLANNED_DRIVE -> R.string.console_calendar_type_planned_drive
        EventType.UNPLANNED_DRIVE -> R.string.console_calendar_type_unplanned_drive
        EventType.REMINDER -> R.string.console_calendar_type_reminder
        EventType.CUSTOM -> R.string.console_calendar_type_custom
    }

/** The auto-relabeling rule: a [EventType.PLANNED_DATE]/[EventType.PLANNED_DRIVE] whose
 * reference date (end if it has one, else start) is today-or-past displays as "Date"/"Drive"
 * instead — the stored [EventType] never changes, only what's shown. Every other type is
 * unaffected and just falls through to the static [labelRes]. */
fun displayLabelRes(type: EventType, referenceDateEpochMillis: Long): Int = when {
    type == EventType.PLANNED_DATE && isPastOrToday(referenceDateEpochMillis) -> R.string.console_calendar_type_date
    type == EventType.PLANNED_DRIVE && isPastOrToday(referenceDateEpochMillis) -> R.string.console_calendar_type_drive
    else -> type.labelRes
}

val CalendarItem.displayLabelRes: Int
    get() = displayLabelRes(type, endAtEpochMillis ?: startAtEpochMillis)

val EventType.icon: ImageVector
    get() = when (this) {
        EventType.BIRTHDAY -> Icons.Rounded.Cake
        EventType.WEDDING_ANNIVERSARY -> Icons.Rounded.Favorite
        EventType.ANNIVERSARY -> Icons.Rounded.Celebration
        EventType.VACATION -> Icons.Rounded.BeachAccess
        EventType.PLANNED_TRIP -> Icons.Rounded.Luggage
        EventType.PLANNED_DATE -> Icons.Rounded.Event
        EventType.UNPLANNED_DATE -> Icons.Rounded.Bolt
        EventType.PLANNED_DRIVE -> Icons.Rounded.DirectionsCar
        EventType.UNPLANNED_DRIVE -> Icons.Rounded.Explore
        EventType.REMINDER -> Icons.Rounded.Alarm
        EventType.CUSTOM -> Icons.Rounded.Notifications
    }
