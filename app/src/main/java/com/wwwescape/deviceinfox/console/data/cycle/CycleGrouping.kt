package com.wwwescape.deviceinfox.console.data.cycle

/** A synthetic cycle derived from contiguous period days — never persisted, see
 * [CycleGrouping.deriveCycles]. */
data class DerivedCycle(val startDateEpochMillis: Long, val endDateEpochMillis: Long)

/** Groups day logs into cycles at read time — "cycles" (start date, duration) are never stored,
 * only derived from contiguous period-day runs, so there's nothing that can drift out of sync
 * with the day logs themselves. Shared by [CycleRepository.computePrediction] (the prediction
 * math) and `PeriodInsightsView` (cycle-length/period-length trend charts) so both reason about
 * "what counts as a cycle" identically — mirrors the server's own
 * `period_service._group_into_cycles` exactly. */
object CycleGrouping {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** A "period day" is any day log with a real [com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity]
     * set — a spotting-only or symptom/mood/notes-only day log doesn't start/extend a cycle (see
     * `PeriodDayLogEntity`'s own doc comment). Two period days up to this many days apart still
     * belong to the same cycle (a single skipped/unlogged day mid-period shouldn't fragment it
     * into two). */
    const val CYCLE_GROUP_MAX_GAP_DAYS = 1

    /** Returns cycles most-recent-first, matching what every caller here expects (mirrors the old
     * cycle-level `observeEntries`' own ordering). */
    fun deriveCycles(dayLogsDescending: List<PeriodDayLog>): List<DerivedCycle> {
        val periodDayDates = dayLogsDescending
            .filter { it.flowIntensity != null }
            .map { it.dateEpochMillis }
            .distinct()
            .sorted()
        if (periodDayDates.isEmpty()) return emptyList()

        val cycles = mutableListOf<DerivedCycle>()
        var groupStart = periodDayDates.first()
        var groupEnd = periodDayDates.first()
        for (date in periodDayDates.drop(1)) {
            val gapDays = (date - groupEnd) / DAY_MILLIS
            if (gapDays > CYCLE_GROUP_MAX_GAP_DAYS + 1) {
                cycles.add(DerivedCycle(groupStart, groupEnd))
                groupStart = date
            }
            groupEnd = date
        }
        cycles.add(DerivedCycle(groupStart, groupEnd))
        return cycles.sortedByDescending { it.startDateEpochMillis }
    }
}
