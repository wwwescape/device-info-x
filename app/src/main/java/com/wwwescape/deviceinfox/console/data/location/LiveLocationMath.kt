package com.wwwescape.deviceinfox.console.data.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Great-circle (straight-line) distance — used only to measure how fast the gap between the two
 * partners is actually shrinking (see [EtaState]'s own doc comment), never as the displayed
 * distance/route itself, which is always the road-based figure from the Directions API. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

/** Whether/how a meaningful "time to meet" can be shown — deliberately not derived from either
 * partner's raw speed alone (this app has no per-partner velocity vector, just position samples),
 * but from how fast the straight-line gap between them is *empirically* shrinking between two
 * consecutive updates. That single number already reflects however much both partners are
 * contributing to closing the distance, whichever of them (or both) is actually moving — see the
 * TODOS.md design discussion this was built from. */
sealed interface EtaState {
    /** Fewer than two position samples yet, or the very first update after both partners appear
     * — nothing to compare against yet. */
    data object Unknown : EtaState

    /** Gap isn't shrinking meaningfully (stationary, moving apart, or moving roughly parallel) —
     * showing a countdown here would be actively misleading, not just imprecise. */
    data object NotClosing : EtaState

    data class Estimated(val etaSeconds: Long) : EtaState
}

/** Below this, a "closing speed" reading is treated as GPS noise rather than real motion — plain
 * position jitter between two fixes can register as a small nonzero closing/opening rate even
 * when both parties are genuinely stationary. ~0.3 m/s is well under any real walking pace. */
private const val MIN_CLOSING_SPEED_MPS = 0.3

/** [previousGapMeters]/[previousGapAtEpochMillis] are the prior call's own [currentGapMeters]/
 * [nowMillis] — the caller carries these forward (see [com.wwwescape.deviceinfox.console.ui.livelocation.LiveLocationViewModel]'s
 * own `previousGapMeters`/`previousGapAtEpochMillis` fields) rather than this function owning any
 * state itself, so it stays a plain, independently-testable calculation. */
fun computeEtaState(
    currentGapMeters: Double,
    nowMillis: Long,
    remainingRouteMeters: Long?,
    previousGapMeters: Double?,
    previousGapAtEpochMillis: Long?,
): EtaState {
    if (remainingRouteMeters == null || previousGapMeters == null || previousGapAtEpochMillis == null) return EtaState.Unknown
    val elapsedSeconds = (nowMillis - previousGapAtEpochMillis) / 1000.0
    if (elapsedSeconds <= 0) return EtaState.Unknown
    val closingSpeedMps = (previousGapMeters - currentGapMeters) / elapsedSeconds
    if (closingSpeedMps < MIN_CLOSING_SPEED_MPS) return EtaState.NotClosing
    return EtaState.Estimated((remainingRouteMeters / closingSpeedMps).toLong())
}
