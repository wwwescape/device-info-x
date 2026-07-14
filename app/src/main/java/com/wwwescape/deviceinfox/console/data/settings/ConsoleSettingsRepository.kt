package com.wwwescape.deviceinfox.console.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consoleSettingsDataStore by preferencesDataStore(name = "console_settings")

/** Private-mode preferences — physically separate DataStore file from the public app's own
 * settings ([com.wwwescape.deviceinfox.data.settings.SettingsRepository]). */
@Singleton
class ConsoleSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationTierKey = stringPreferencesKey("notification_tier")

    val notificationTier: Flow<NotificationTier> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[notificationTierKey]
            ?.let { name -> runCatching { NotificationTier.valueOf(name) }.getOrNull() }
            ?: NotificationTier.GENERIC
    }

    suspend fun setNotificationTier(tier: NotificationTier) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[notificationTierKey] = tier.name }
    }

    private val notificationImportanceKey = stringPreferencesKey("notification_importance")

    val notificationImportance: Flow<NotificationImportance> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[notificationImportanceKey]
            ?.let { name -> runCatching { NotificationImportance.valueOf(name) }.getOrNull() }
            ?: NotificationImportance.HIGH
    }

    suspend fun setNotificationImportance(importance: NotificationImportance) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[notificationImportanceKey] = importance.name }
    }

    private val notificationSoundModeKey = stringPreferencesKey("notification_sound_mode")

    val notificationSoundMode: Flow<NotificationSoundMode> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[notificationSoundModeKey]
            ?.let { name -> runCatching { NotificationSoundMode.valueOf(name) }.getOrNull() }
            ?: NotificationSoundMode.ALWAYS
    }

    suspend fun setNotificationSoundMode(mode: NotificationSoundMode) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[notificationSoundModeKey] = mode.name }
    }

    private val notificationSoundToneKey = stringPreferencesKey("notification_sound_tone")

    val notificationSoundTone: Flow<NotificationSoundTone> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[notificationSoundToneKey]
            ?.let { name -> runCatching { NotificationSoundTone.valueOf(name) }.getOrNull() }
            ?: NotificationSoundTone.DEFAULT
    }

    suspend fun setNotificationSoundTone(tone: NotificationSoundTone) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[notificationSoundToneKey] = tone.name }
    }

    private val notificationSoundThrottleMinutesKey = intPreferencesKey("notification_sound_throttle_minutes")

    /** [NotificationSoundMode.THROTTLED]'s window length, in minutes — user-configurable (5/10/15,
     * `ConsoleSettingsScreen`'s segmented button row next to the Throttled row), read by
     * `ConsoleFcmService.resolveChannelId` instead of a fixed constant. Defaults to 15 — today's
     * previously-hardcoded behavior — so existing installs see no change until they opt in. */
    val notificationSoundThrottleMinutes: Flow<Int> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[notificationSoundThrottleMinutesKey] ?: DEFAULT_SOUND_THROTTLE_MINUTES
    }

    suspend fun setNotificationSoundThrottleMinutes(minutes: Int) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[notificationSoundThrottleMinutesKey] = minutes }
    }

    private val lastSoundPlayedAtKey = longPreferencesKey("last_sound_played_at_epoch_millis")

    /** Only read/written under [NotificationSoundMode.THROTTLED] — when a push last actually
     * played sound, so `ConsoleFcmService.resolveChannelId` can tell whether
     * [notificationSoundThrottleMinutes]'s window has elapsed. */
    val lastSoundPlayedAtEpochMillis: Flow<Long?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[lastSoundPlayedAtKey]
    }

    suspend fun setLastSoundPlayedAtEpochMillis(epochMillis: Long) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[lastSoundPlayedAtKey] = epochMillis }
    }

    private fun notificationCountKey(category: String) = intPreferencesKey("notification_count_$category")

    /** Per-`PushCategory` (`ConsoleFcmService`) unseen-push count — incremented every time
     * `showNotification` fires for that category, and what drives the "(x3)" title suffix once
     * it's above 1. Reset back to 0 the moment that category's shade notification is either
     * swiped away (`NotificationDismissReceiver`) or tapped (`MainActivity`) — either dismissal
     * counts as "seen," so a stale count can't silently climb forever between visits. */
    suspend fun incrementAndGetNotificationCount(category: String): Int {
        var result = 1
        context.consoleSettingsDataStore.edit { prefs ->
            val key = notificationCountKey(category)
            result = (prefs[key] ?: 0) + 1
            prefs[key] = result
        }
        return result
    }

    suspend fun resetNotificationCount(category: String) {
        context.consoleSettingsDataStore.edit { prefs -> prefs.remove(notificationCountKey(category)) }
    }

    private val onlinePingLastSentAtKey = longPreferencesKey("online_ping_last_sent_at_epoch_millis")

    /** Local mirror of the tap, purely so the header icon can grey itself out instantly and
     * across process death without a network round trip — the server's own
     * `User.last_online_ping_sent_at` is the actual source of truth for the 15-minute cooldown
     * (see `MessageRepository.sendOnlinePing`), so this being out of sync with the server (e.g. a
     * reinstall resetting it) only ever costs an extra rejected tap, never lets the cooldown be
     * bypassed. */
    val onlinePingLastSentAtEpochMillis: Flow<Long?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[onlinePingLastSentAtKey]
    }

    suspend fun setOnlinePingLastSentAtEpochMillis(epochMillis: Long) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[onlinePingLastSentAtKey] = epochMillis }
    }

    private val averageCycleLengthDaysSeedKey = intPreferencesKey("average_cycle_length_days_seed")

    /** Local-only, on-device estimate the user picks during Period Tracker onboarding (or later
     * revises from Settings) — never synced, since it only ever seeds *this device's own* first
     * prediction before enough real cycle entries exist to average from (see
     * `CycleRepository.computePrediction`). Null means "never set" — onboarding derives its
     * one-time visibility from whether any cycle entries exist yet, not from this being null. */
    val averageCycleLengthDaysSeed: Flow<Int?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[averageCycleLengthDaysSeedKey]
    }

    suspend fun setAverageCycleLengthDaysSeed(days: Int) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[averageCycleLengthDaysSeedKey] = days.coerceIn(20, 45) }
    }

    private val averagePeriodLengthDaysSeedKey = intPreferencesKey("average_period_length_days_seed")

    /** Same local-only, on-device nature as [averageCycleLengthDaysSeed] — how many days a period
     * itself typically lasts (3–7), used both to give onboarding's very first logged entry a real
     * end date instead of leaving it open-ended, and as the fallback for
     * `CyclePrediction.predictedDurationDays` before 2+ entries with both dates set exist. */
    val averagePeriodLengthDaysSeed: Flow<Int?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[averagePeriodLengthDaysSeedKey]
    }

    suspend fun setAveragePeriodLengthDaysSeed(days: Int) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[averagePeriodLengthDaysSeedKey] = days.coerceIn(3, 7) }
    }

    private val averageLutealPhaseDaysSeedKey = intPreferencesKey("average_luteal_phase_days_seed")

    /** Same local-only, on-device nature as [averageCycleLengthDaysSeed]/[averagePeriodLengthDaysSeed]
     * — an optional, advanced override for the luteal-phase length `CycleRepository.computePrediction`
     * otherwise assumes a fixed 14-day textbook value for. Null means "never set," which
     * `computePrediction` reads as "use the textbook default," not as "use 0." */
    val averageLutealPhaseDaysSeed: Flow<Int?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[averageLutealPhaseDaysSeedKey]
    }

    suspend fun setAverageLutealPhaseDaysSeed(days: Int) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[averageLutealPhaseDaysSeedKey] = days.coerceIn(10, 16) }
    }

    private val lastBirthdayPopupShownDateKey = stringPreferencesKey("last_birthday_popup_shown_date")

    /** ISO `LocalDate` string (e.g. "2026-08-04") of the last day the birthday popup was shown —
     * dedupes it to once per day without ever needing to fire again mid-session, since
     * [com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsViewModel] checks this once per fresh
     * console entry. */
    val lastBirthdayPopupShownDate: Flow<String?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[lastBirthdayPopupShownDateKey]
    }

    suspend fun setLastBirthdayPopupShownDate(date: String) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[lastBirthdayPopupShownDateKey] = date }
    }

    private val lastHolidayEventPopupShownDateKey = stringPreferencesKey("last_holiday_event_popup_shown_date")

    /** Same shape as [lastBirthdayPopupShownDate] — the ISO date the holiday-event popup last
     * showed, deduping it to once per day regardless of which event matched (at most one is
     * expected to match any given day in practice, so a single date string is enough — no need to
     * track "which event" separately). */
    val lastHolidayEventPopupShownDate: Flow<String?> = context.consoleSettingsDataStore.data.map { prefs ->
        prefs[lastHolidayEventPopupShownDateKey]
    }

    suspend fun setLastHolidayEventPopupShownDate(date: String) {
        context.consoleSettingsDataStore.edit { prefs -> prefs[lastHolidayEventPopupShownDateKey] = date }
    }

    private val recentEmojiKey = stringPreferencesKey("recent_emoji")

    /** Most-recently-used first. Stored as a single delimited string rather than one key per
     * emoji — a handful of characters, no need for `stringSetPreferencesKey`'s unordered-set
     * semantics when order (most-recent-first) is the entire point.
     *
     * [splitRecentEmojis] self-heals data written back while [RECENT_EMOJI_DELIMITER] was
     * briefly a corrupted NUL byte instead of a space (a real, confirmed-on-device bug — the
     * constant itself is fixed, but a value already persisted under the old delimiter doesn't
     * un-corrupt itself just because the code that reads it later changed) — without this, an
     * old multi-emoji blob stays stuck together as one oversized entry forever: it renders as one
     * grid cell wrapping several glyphs, and tapping it sends all of them at once. */
    val recentEmojis: Flow<List<String>> = context.consoleSettingsDataStore.data.map { prefs ->
        splitRecentEmojis(prefs[recentEmojiKey])
    }

    suspend fun recordEmojiUsed(emoji: String) {
        context.consoleSettingsDataStore.edit { prefs ->
            val current = splitRecentEmojis(prefs[recentEmojiKey])
            val updated = (listOf(emoji) + current.filter { it != emoji }).take(MAX_RECENT_EMOJI)
            prefs[recentEmojiKey] = updated.joinToString(RECENT_EMOJI_DELIMITER)
        }
    }

    /** Splits on the real delimiter first, then re-splits any entry that still contains an
     * embedded NUL byte on that instead — see [recentEmojis]'s own doc comment for why a stray
     * NUL can be baked into already-persisted data. A real emoji grapheme cluster never contains
     * a NUL byte itself, so this can't misfire on legitimately-stored data. */
    private fun splitRecentEmojis(stored: String?): List<String> =
        stored
            ?.split(RECENT_EMOJI_DELIMITER)
            ?.filter { it.isNotEmpty() }
            ?.flatMap { entry ->
                if (entry.contains(LEGACY_CORRUPTED_DELIMITER)) {
                    entry.split(LEGACY_CORRUPTED_DELIMITER).filter { it.isNotEmpty() }
                } else {
                    listOf(entry)
                }
            }
            ?: emptyList()

    private companion object {
        const val RECENT_EMOJI_DELIMITER = " "

        /** What [RECENT_EMOJI_DELIMITER] was briefly, incorrectly, a literal NUL character
         * instead of a space — see [splitRecentEmojis]. */
        const val LEGACY_CORRUPTED_DELIMITER = '\u0000'
        const val MAX_RECENT_EMOJI = 16
        const val DEFAULT_SOUND_THROTTLE_MINUTES = 15
    }
}
