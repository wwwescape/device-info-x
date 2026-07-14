package com.wwwescape.deviceinfox.console.ui.calendar

import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem

data class CalendarUiState(
    val items: List<CalendarItem> = emptyList(),
)
