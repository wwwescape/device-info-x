package com.wwwescape.deviceinfox.console.ui.periodtracker

import com.wwwescape.deviceinfox.console.data.cycle.CyclePrediction
import com.wwwescape.deviceinfox.console.data.cycle.CycleVisibility
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import java.time.LocalDate

data class PeriodTrackerUiState(
    val visibility: CycleVisibility = CycleVisibility.Hidden,
    val selectedPartnerId: String? = null,
    val partnerDisplayName: String? = null,
    val dayLogs: List<PeriodDayLog> = emptyList(),
    val prediction: CyclePrediction? = null,
    /** False when viewing the partner's tab — the server only lets a day log's own owner
     * create/edit/delete it (Phase 11.7), so the add/edit/delete UI is hidden rather than
     * letting the user hit a 403. */
    val canEdit: Boolean = false,
    /** From [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository.intimacyDates] —
     * couple-wide, not per-tab, unlike everything else above. Purely a display signal on
     * [CycleCalendarView]'s grid; nothing here is editable from this screen. */
    val intimacyDates: Set<LocalDate> = emptySet(),
)
