package com.wwwescape.deviceinfox.console.data.cycle

import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity

/** One logged day, not one whole period — see [PeriodDayLogEntity][com.wwwescape.deviceinfox.console.data.db.PeriodDayLogEntity]'s
 * doc comment for what makes a day a "period day" for cycle-grouping/prediction purposes. */
data class PeriodDayLog(
    val id: String,
    val partnerId: String,
    val dateEpochMillis: Long,
    val flowIntensity: CycleFlowIntensity?,
    val symptoms: List<String>,
    val notes: String?,
)

/** [predictedOvulationEpochMillis]/[fertileWindowStartEpochMillis]/[fertileWindowEndEpochMillis]
 * are estimates only, derived from [predictedNextStartEpochMillis] using the standard ~14-day
 * luteal-phase assumption (see `CycleRepository.computePrediction`) — never a substitute for a
 * real fertility test or reliable contraception, and the UI must always present them with that
 * caveat rather than as a guarantee. */
data class CyclePrediction(
    val averageCycleLengthDays: Int,
    val predictedNextStartEpochMillis: Long,
    /** Half-width, in days, of the confidence range around [predictedNextStartEpochMillis] —
     * `0` means "confident enough to show as a single date" (fewer than 3 cycles logged yet, or
     * negligible variance across the ones that are), `>0` means the UI should render a range
     * instead (e.g. "likely Mar 12–15") rather than implying false precision. Derived from the
     * standard deviation of gaps between logged cycle starts — see
     * `CycleRepository.computePrediction`. */
    val predictedNextStartRangeHalfWidthDays: Int,
    /** True once that same variance crosses a threshold high enough that even a range shouldn't
     * be presented as reliable — the UI should hedge harder (e.g. "your cycles vary a lot, treat
     * this as a rough estimate") rather than let either a single date or a range imply more
     * confidence than the underlying data supports. */
    val isIrregular: Boolean,
    val predictedDurationDays: Int,
    val predictedOvulationEpochMillis: Long,
    val fertileWindowStartEpochMillis: Long,
    val fertileWindowEndEpochMillis: Long,
    /** The most recent *derived* cycle's start date (see `CycleRepository.deriveCycles`) — never
     * a raw day log's own date, since under day-level logging the most recently logged day isn't
     * necessarily a cycle start. Null only when no period day has ever been logged (mirrors
     * [CyclePrediction] itself being null in that case; kept alongside the other fields rather
     * than computed separately so the UI never needs its own copy of the grouping logic). Used
     * for `CycleCalendarView`'s "Cycle Day N" header. */
    val currentCycleStartEpochMillis: Long?,
)
