package com.wwwescape.deviceinfox.console.ui.periodtracker

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog

/** Real symptom tags — anything else riding in [PeriodDayLog.symptoms] ([SPOTTING_KEY], the
 * [MOOD_KEYS]) is a synthetic tag layered on top of the same synced string list rather than a
 * schema change, so this list must stay filtered out of "symptom" UI. */
internal val SYMPTOM_KEYS = listOf("cramps", "headache", "fatigue", "bloating", "mood_swings", "acne")

/** A 4th "Spotting" flow level with no backing server enum value (`CycleFlowIntensity` only has
 * LIGHT/MEDIUM/HEAVY) — represented as a tag in the same synced [PeriodDayLog.symptoms] list
 * instead, so it still round-trips to a partner for free. */
internal const val SPOTTING_KEY = "spotting"

internal const val MOOD_PREFIX = "mood_"
internal val MOOD_KEYS = listOf("mood_happy", "mood_sensitive", "mood_sad", "mood_energetic")

internal fun symptomLabelRes(key: String): Int = when (key) {
    "cramps" -> R.string.console_period_symptom_cramps
    "headache" -> R.string.console_period_symptom_headache
    "fatigue" -> R.string.console_period_symptom_fatigue
    "bloating" -> R.string.console_period_symptom_bloating
    "mood_swings" -> R.string.console_period_symptom_mood_swings
    else -> R.string.console_period_symptom_acne
}

internal fun moodLabelRes(key: String): Int = when (key) {
    "mood_happy" -> R.string.console_period_mood_happy
    "mood_sensitive" -> R.string.console_period_mood_sensitive
    "mood_sad" -> R.string.console_period_mood_sad
    else -> R.string.console_period_mood_energetic
}

/** Real [SYMPTOM_KEYS] entries only — excludes the mood tags and the spotting flow tag that also
 * live in the same synced list, so symptom-chip UI never renders them as symptoms. */
internal fun List<String>.asSymptomTags(): List<String> = filter { it in SYMPTOM_KEYS }

internal fun List<String>.asMoodTags(): List<String> = filter { it.startsWith(MOOD_PREFIX) }

internal fun List<String>.hasSpotting(): Boolean = SPOTTING_KEY in this

@Composable
internal fun symptomLabel(key: String): String = stringResource(symptomLabelRes(key))

@Composable
internal fun moodLabel(key: String): String = stringResource(moodLabelRes(key))
