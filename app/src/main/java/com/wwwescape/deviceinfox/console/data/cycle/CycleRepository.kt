package com.wwwescape.deviceinfox.console.data.cycle

import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity
import com.wwwescape.deviceinfox.console.data.db.PartnerDao
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.db.PeriodDayLogDao
import com.wwwescape.deviceinfox.console.data.db.PeriodDayLogEntity
import com.wwwescape.deviceinfox.console.data.network.PeriodApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.FlowIntensityDto
import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.isoDateStringToEpochMillis
import com.wwwescape.deviceinfox.console.data.network.dto.toCycleFlowIntensity
import com.wwwescape.deviceinfox.console.data.network.dto.toFlowIntensityDto
import com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateString
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Real, server-backed period tracking (Phase 11.7, day-level logging since Phase 12) — `Room is
 * what the UI sees` still holds: [observeDayLogs] is the same Room-backed `Flow`
 * `PeriodTrackerViewModel` has always observed.
 *
 * One row per logged day, not per whole period (see [PeriodDayLogEntity]'s own doc comment) —
 * "cycles" (start date, duration, the prediction math below) are never stored, only derived at
 * read time from contiguous period-day runs via [CycleGrouping.deriveCycles] (shared with
 * `PeriodInsightsView`'s trend charts), so nothing can drift out of sync with the day logs
 * themselves. This mirrors the server's own `period_service._group_into_cycles` exactly.
 *
 * The server enforces owner-only writes (`period_service.py`: `create_day_log` always attaches to
 * the caller regardless of any client-supplied target, `update_day_log`/`delete_day_log` 403 if
 * the day log isn't the caller's own) while allowing read access to a paired partner's day logs
 * (the `SERVER_PHASES.md` partner-visibility decision). `PeriodTrackerUiState.canEdit` gates the
 * FAB/edit/delete UI to only the self tab, matching what the server will actually allow —
 * otherwise a save on the partner's tab would either silently attach to the wrong user or 403.
 *
 * No WebSocket push exists for period data (same as calendar), so each tab does its own
 * fetch-then-replace [refresh] when it's actually viewed, driven by `PeriodTrackerViewModel`.
 */
@Singleton
class CycleRepository @Inject constructor(
    private val periodDayLogDao: PeriodDayLogDao,
    private val partnerDao: PartnerDao,
    private val partnerRepository: PartnerRepository,
    private val periodApi: PeriodApi,
) {
    /** "Both male -> hidden; one female -> single cycle; both female -> two independent
     * cycles" per the design spec, generalized to: visible the moment either side is
     * explicitly FEMALE, hidden otherwise — that covers both-MALE, unpaired-MALE,
     * both-UNSPECIFIED (e.g. profiles never filled in), and any MALE/UNSPECIFIED mix, all of
     * which lack the explicit signal the feature is relevant. */
    val visibility: Flow<CycleVisibility> = combine(
        partnerRepository.selfProfile,
        partnerDao.observePartner(),
    ) { self, partner ->
        val femaleIds = listOfNotNull(self, partner).filter { it.gender == PartnerGender.FEMALE }.map { it.id }
        when {
            femaleIds.size == 2 -> CycleVisibility.TwoCycles(selfId = self!!.id, partnerId = partner!!.id)
            femaleIds.size == 1 ->
                CycleVisibility.SingleCycle(trackedPartnerId = femaleIds.first(), isSelf = femaleIds.first() == self?.id)
            else -> CycleVisibility.Hidden
        }
    }

    /** Descending by date (most recent first). */
    fun observeDayLogs(partnerId: String): Flow<List<PeriodDayLog>> =
        periodDayLogDao.observeForPartner(partnerId).map { entries -> entries.map { it.toPeriodDayLog() } }

    /** [cycleLengthSeedDays]/[periodLengthSeedDays]/[lutealPhaseSeedDays] are local-only, on-device
     * estimates (see `ConsoleSettingsRepository`) that only ever make sense for the caller's own
     * tab — the ViewModel decides eligibility (gated on `canEdit`) and passes `flowOf(null)` for
     * all three on a partner's tab, since there's no way to read a partner's own device-local
     * preferences. */
    fun observePrediction(
        partnerId: String,
        cycleLengthSeedDays: Flow<Int?> = flowOf(null),
        periodLengthSeedDays: Flow<Int?> = flowOf(null),
        lutealPhaseSeedDays: Flow<Int?> = flowOf(null),
    ): Flow<CyclePrediction?> =
        combine(
            observeDayLogs(partnerId),
            cycleLengthSeedDays,
            periodLengthSeedDays,
            lutealPhaseSeedDays,
        ) { dayLogs, cycleSeed, periodSeed, lutealSeed ->
            computePrediction(dayLogs, cycleSeed, periodSeed, lutealSeed)
        }

    /** Full replace-sync of one tab's day logs — [partnerId] doubles as the server's real user id
     * (self/partner ids were re-stamped to their server UUIDs in Phase 11.2), so it's passed
     * straight through as `user_id`. */
    suspend fun refresh(partnerId: String) {
        val dayLogs = consoleApiCall { periodApi.list(userId = partnerId) }
        periodDayLogDao.replaceForPartner(partnerId, dayLogs.map { it.toEntity(partnerId) })
    }

    suspend fun saveDayLog(
        id: String?,
        partnerId: String,
        dateEpochMillis: Long,
        flowIntensity: CycleFlowIntensity?,
        symptoms: List<String>,
        notes: String?,
    ) {
        val logDate = dateEpochMillis.toIsoDateString()
        val flowDto = flowIntensity?.toFlowIntensityDto()
        val result = if (id == null) {
            consoleApiCall { periodApi.create(PeriodDayLogCreateDto(logDate, symptoms, flowDto, notes)) }
        } else {
            consoleApiCall { periodApi.update(id, PeriodDayLogUpdateDto(logDate, symptoms, flowDto, notes)) }
        }
        periodDayLogDao.upsert(result.toEntity(partnerId))
    }

    /** Onboarding's "Get Started" action — logs one day per day in the assumed period-length
     * range, each defaulted to MEDIUM flow (individually editable afterward) so they immediately
     * count as period days for prediction purposes (see [PeriodDayLogEntity]'s own doc comment).
     * All the network creates happen first, then every resulting row is written to Room in a
     * single [PeriodDayLogDao.upsertAll] transaction — deliberately not one [saveDayLog] call per
     * day, which would let `PeriodTrackerUiState.dayLogs` flip non-empty (and onboarding
     * disappear) after just the *first* day landed, mid-loop, instead of once the whole range is
     * in. */
    suspend fun saveOnboardingDayLogs(partnerId: String, startDateEpochMillis: Long, periodLengthDays: Int) {
        val entities = (0 until periodLengthDays).map { offset ->
            val logDate = (startDateEpochMillis + offset * DAY_MILLIS).toIsoDateString()
            val result = consoleApiCall {
                periodApi.create(PeriodDayLogCreateDto(logDate, emptyList(), FlowIntensityDto.MEDIUM, null))
            }
            result.toEntity(partnerId)
        }
        periodDayLogDao.upsertAll(entities)
    }

    suspend fun deleteDayLog(id: String) {
        consoleApiCall { periodApi.delete(id) }
        periodDayLogDao.delete(id)
    }

    /** Whether saving a *new* day log with [flowIntensity] on [candidateDateEpochMillis] would
     * both (a) start a brand-new derived cycle — not merely extend one already logged nearby, see
     * [CycleGrouping.deriveCycles] — and (b) land far enough from [prediction]'s own predicted next start that
     * it's worth confirming with the user before committing, mirroring the confirmation this app
     * already shows for other late/retroactive entries (e.g. Calendar's own patterns). Only a
     * real flow value can start a cycle at all (see [PeriodDayLogEntity]'s doc comment) — a
     * spotting/symptom-only day log never triggers this, and neither does editing an
     * already-logged day (its date can't change — see `PeriodDayLogEditor`), which is why this is
     * only ever called for a brand-new day log. Pure and stateless so the caller (the ViewModel)
     * can check it against the already-loaded [dayLogsDescending]/[prediction] before ever calling
     * [saveDayLog] — no server round-trip needed just to decide whether to ask first. */
    fun isUnexpectedCycleStart(
        dayLogsDescending: List<PeriodDayLog>,
        prediction: CyclePrediction?,
        candidateDateEpochMillis: Long,
        flowIntensity: CycleFlowIntensity?,
    ): Boolean {
        if (flowIntensity == null || prediction == null) return false
        val hasNearbyPeriodDay = dayLogsDescending.any { log ->
            log.flowIntensity != null &&
                kotlin.math.abs(candidateDateEpochMillis - log.dateEpochMillis) / DAY_MILLIS <=
                    CycleGrouping.CYCLE_GROUP_MAX_GAP_DAYS + 1
        }
        if (hasNearbyPeriodDay) return false

        val toleranceDays = maxOf(prediction.predictedNextStartRangeHalfWidthDays, MIN_CONFIRM_TOLERANCE_DAYS)
        val deviationDays = kotlin.math.abs(candidateDateEpochMillis - prediction.predictedNextStartEpochMillis) / DAY_MILLIS
        return deviationDays > toleranceDays
    }

    /** The "Delete data" action for Period Tracker in Settings — wipes logged days for both
     * partners (confirmed, deliberate divergence from every other mutation here, which stays
     * owner-only), matching `period_service.delete_all_day_logs`. */
    suspend fun wipeAllDayLogs() {
        consoleApiCall { periodApi.deleteAllMine() }
        periodDayLogDao.deleteAll()
    }

    /** Null only if [PeriodDayLogOutDto.logDate] fails to parse — never expected from the
     * server's own `log_date` field. [partnerId] is threaded through rather than trusting
     * [PeriodDayLogOutDto.userId] directly so a self-tab refresh and a partner-tab refresh can't
     * cross-contaminate the wrong side of [PeriodDayLogEntity.partnerId] even though today
     * they're always equal in practice. */
    private fun PeriodDayLogOutDto.toEntity(partnerId: String) = PeriodDayLogEntity(
        id = id,
        partnerId = partnerId,
        dateEpochMillis = logDate.isoDateStringToEpochMillis() ?: 0L,
        flowIntensity = flowIntensity?.toCycleFlowIntensity(),
        symptoms = symptoms.takeIf { it.isNotEmpty() }?.joinToString(","),
        notes = notes,
    )

    private fun PeriodDayLogEntity.toPeriodDayLog() = PeriodDayLog(
        id = id,
        partnerId = partnerId,
        dateEpochMillis = dateEpochMillis,
        flowIntensity = flowIntensity,
        symptoms = symptoms?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        notes = notes,
    )

    private companion object {
        const val DEFAULT_CYCLE_LENGTH_DAYS = 28
        const val DEFAULT_DURATION_DAYS = 5
        const val MAX_CYCLES_FOR_AVERAGE = 6
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /** Textbook luteal-phase length (the days between ovulation and the next period), which
         * stays far more constant across cycles than the follicular phase does — this is the
         * standard basis calendar-based fertility estimates use, not something derivable from
         * this app's own logged data. The fallback when the caller hasn't set their own estimate
         * via `ConsoleSettingsRepository.averageLutealPhaseDaysSeed` (see [computePrediction]'s
         * [lutealPhaseSeedDays] parameter) — same "textbook default, optionally overridden" shape
         * as [DEFAULT_CYCLE_LENGTH_DAYS]/[DEFAULT_DURATION_DAYS] above. */
        const val LUTEAL_PHASE_DAYS = 14
        const val FERTILE_WINDOW_DAYS_BEFORE_OVULATION = 5
        const val FERTILE_WINDOW_DAYS_AFTER_OVULATION = 1

        /** A range wider than this stops being a useful "likely Mar 12–15" display and starts
         * reading as noise — [CyclePrediction.isIrregular] is what should carry the "this isn't
         * reliable" signal past this point, not an ever-widening range. */
        const val MAX_RANGE_HALF_WIDTH_DAYS = 7

        /** Spec-derived threshold ("cycle-length variance exceeds ~7–8 days") for when logged
         * cycles are irregular enough that even a range shouldn't be presented as reliable —
         * measured as the standard deviation of gaps between logged starts, in days, the same
         * unit the spec's own "7–8 days" figure is given in. */
        const val IRREGULAR_VARIANCE_THRESHOLD_DAYS = 7.5

        /** Floor tolerance for [isUnexpectedCycleStart] even when
         * [CyclePrediction.predictedNextStartRangeHalfWidthDays] is 0 (a "confident" single-date
         * prediction, e.g. too few cycles logged yet for a variance estimate) — a period landing
         * a day or two early/late is completely normal and not worth interrupting the save for;
         * only a deviation past this (or past the real confidence range, if wider) asks first. */
        const val MIN_CONFIRM_TOLERANCE_DAYS = 3

        /** [dayLogsDescending] is most-recent-first (see [observeDayLogs]) and is first grouped
         * into derived cycles via [CycleGrouping.deriveCycles] (shared with `PeriodInsightsView`'s
         * trend charts, so both reason about "what counts as a cycle" identically) — everything
         * below then runs over those
         * synthetic start/end pairs exactly as it used to run over raw cycle-level entries. A
         * single derived cycle predicts off [cycleLengthSeedDays] if the caller has one (their
         * own onboarding estimate) or else the textbook 28-day average; two or more average the
         * actual gaps between the most recent [MAX_CYCLES_FOR_AVERAGE] start dates.
         * [predictedDurationDays] is always derived the same way regardless of cycle count: the
         * average logged start-to-end span across derived cycles, or [periodLengthSeedDays] (the
         * caller's own onboarding estimate) if there are none yet, or a 5-day guess if neither
         * exists. Ovulation/fertile-window fields are calculated [lutealPhaseSeedDays] before the
         * next period if the caller has set that optional/advanced estimate, or the textbook
         * 14-day [LUTEAL_PHASE_DAYS] otherwise — see the class doc on [CyclePrediction] for why
         * these are estimates, not guarantees.
         *
         * [CyclePrediction.predictedNextStartRangeHalfWidthDays]/[CyclePrediction.isIrregular] are
         * both derived from the same gaps-between-starts list [gapsDays] already averages —
         * need at least 3 derived cycles (2+ gaps) for a variance estimate to mean anything at
         * all, matching the spec's own "once 3+ cycles" framing; fewer than that renders as a
         * single confident date (half-width 0), same as before this field existed. */
        fun computePrediction(
            dayLogsDescending: List<PeriodDayLog>,
            cycleLengthSeedDays: Int?,
            periodLengthSeedDays: Int?,
            lutealPhaseSeedDays: Int? = null,
        ): CyclePrediction? {
            val cyclesDescending = CycleGrouping.deriveCycles(dayLogsDescending)
            val lastStart = cyclesDescending.firstOrNull()?.startDateEpochMillis ?: return null
            val durationsDays = cyclesDescending.map { cycle ->
                ((cycle.endDateEpochMillis - cycle.startDateEpochMillis) / DAY_MILLIS).toInt() + 1
            }
            val predictedDurationDays = when {
                durationsDays.isNotEmpty() -> durationsDays.average().toInt().coerceAtLeast(1)
                periodLengthSeedDays != null -> periodLengthSeedDays.coerceIn(3, 7)
                else -> DEFAULT_DURATION_DAYS
            }

            val gapsDays = if (cyclesDescending.size >= 2) {
                cyclesDescending.map { it.startDateEpochMillis }.take(MAX_CYCLES_FOR_AVERAGE + 1)
                    .zipWithNext { newer, older -> ((newer - older) / DAY_MILLIS).toInt() }
            } else {
                emptyList()
            }
            val cycleLengthDays = if (gapsDays.isNotEmpty()) {
                gapsDays.average().toInt().coerceAtLeast(1)
            } else {
                cycleLengthSeedDays?.coerceIn(20, 45) ?: DEFAULT_CYCLE_LENGTH_DAYS
            }
            val predictedNextStartEpochMillis = lastStart + cycleLengthDays * DAY_MILLIS

            var rangeHalfWidthDays = 0
            var isIrregular = false
            if (gapsDays.size >= 2) {
                val meanGapDays = gapsDays.average()
                val varianceDays = gapsDays.sumOf { (it - meanGapDays) * (it - meanGapDays) } / gapsDays.size
                val stdDevDays = sqrt(varianceDays)
                rangeHalfWidthDays = stdDevDays.roundToInt().coerceIn(0, MAX_RANGE_HALF_WIDTH_DAYS)
                isIrregular = stdDevDays > IRREGULAR_VARIANCE_THRESHOLD_DAYS
            }

            val lutealPhaseDays = lutealPhaseSeedDays?.coerceIn(10, 16) ?: LUTEAL_PHASE_DAYS
            val predictedOvulationEpochMillis = predictedNextStartEpochMillis - lutealPhaseDays * DAY_MILLIS
            return CyclePrediction(
                averageCycleLengthDays = cycleLengthDays,
                predictedNextStartEpochMillis = predictedNextStartEpochMillis,
                predictedNextStartRangeHalfWidthDays = rangeHalfWidthDays,
                isIrregular = isIrregular,
                predictedDurationDays = predictedDurationDays,
                predictedOvulationEpochMillis = predictedOvulationEpochMillis,
                fertileWindowStartEpochMillis = predictedOvulationEpochMillis - FERTILE_WINDOW_DAYS_BEFORE_OVULATION * DAY_MILLIS,
                fertileWindowEndEpochMillis = predictedOvulationEpochMillis + FERTILE_WINDOW_DAYS_AFTER_OVULATION * DAY_MILLIS,
                currentCycleStartEpochMillis = lastStart,
            )
        }
    }
}
