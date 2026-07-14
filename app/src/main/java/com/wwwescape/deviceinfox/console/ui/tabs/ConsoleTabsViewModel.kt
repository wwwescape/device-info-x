package com.wwwescape.deviceinfox.console.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.cycle.CycleRepository
import com.wwwescape.deviceinfox.console.data.cycle.CycleVisibility
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.events.HolidayEvent
import com.wwwescape.deviceinfox.console.data.events.HolidayEventsRepository
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsScreen]'s birthday popup — partner name
 * for the "loves you very much" line plus whatever optional custom message the *partner* wrote
 * for this occasion (see [checkBirthday]), if any. */
data class BirthdayPopupData(val partnerName: String, val customMessage: String?)

/** Single source of truth for whether the Period Tracker tab should be shown — every other
 * consumer of this rule (the old "more" dropdown, [com.wwwescape.deviceinfox.console.ui.periodtracker.PeriodTrackerScreen]'s
 * own defensive check) is gone now that tab visibility itself is the one gate. */
@HiltViewModel
class ConsoleTabsViewModel @Inject constructor(
    cycleRepository: CycleRepository,
    private val partnerRepository: PartnerRepository,
    private val settingsRepository: ConsoleSettingsRepository,
    private val holidayEventsRepository: HolidayEventsRepository,
) : ViewModel() {
    val isPeriodTrackerVisible: StateFlow<Boolean> = cycleRepository.visibility
        .map { it != CycleVisibility.Hidden }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _birthdayPopupData = MutableStateFlow<BirthdayPopupData?>(null)

    /** Non-null exactly once per calendar day, checked fresh in [init] since this ViewModel is
     * recreated on every console entry ([com.wwwescape.deviceinfox.console.ui.nav.ConsoleActivity]
     * is `finish()`'d on every lock/background, so there's no separate "just unlocked" signal
     * needed). */
    val birthdayPopupData: StateFlow<BirthdayPopupData?> = _birthdayPopupData.asStateFlow()

    private val _holidayEventPopup = MutableStateFlow<HolidayEvent?>(null)

    /** Same one-per-day shape as [birthdayPopupData], for national/catholic/hindu holidays from
     * `res/raw/events.json` — see [checkHolidayEvent] for why it only ever fires on an entry
     * where the birthday popup didn't. */
    val holidayEventPopup: StateFlow<HolidayEvent?> = _holidayEventPopup.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { checkBirthday() }
            runCatching { checkHolidayEvent() }
        }
    }

    private suspend fun checkBirthday() {
        val self = partnerRepository.selfProfile.first() ?: return
        val birthdayMillis = self.birthdayEpochMillis ?: return
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val birthday = Instant.ofEpochMilli(birthdayMillis).atZone(zoneId).toLocalDate()
        if (birthday.monthValue != today.monthValue || birthday.dayOfMonth != today.dayOfMonth) return

        // Nothing to attribute the "loves you" line to yet — if they pair later today, the next
        // console entry will still show it, since the "shown" flag below isn't set until here.
        val partner = partnerRepository.partnerProfile.first() ?: return

        val todayIso = today.toString()
        if (settingsRepository.lastBirthdayPopupShownDate.first() == todayIso) return
        settingsRepository.setLastBirthdayPopupShownDate(todayIso)
        // The message shown here is what the PARTNER authored (in their own Settings), not
        // anything local — it's meant as something they wrote *for* this occasion, not a note to
        // themselves, so it only ever surfaces on the birthday-haver's popup.
        _birthdayPopupData.value = BirthdayPopupData(partner.firstName, partner.birthdayMessage)
    }

    /** Deliberately skipped on any entry where the birthday popup already fired (checked above,
     * same `init` block, always runs first) — the two would otherwise be free to show as two
     * stacked dialogs the instant the console opens, which reads as broken rather than deliberate
     * for what should be a rare coincidence anyway. Skipping here (rather than marking today's
     * date "shown" and losing it) means it still shows on a later entry the same day, once the
     * birthday popup has already had its one showing. */
    private suspend fun checkHolidayEvent() {
        if (_birthdayPopupData.value != null) return
        val today = LocalDate.now(ZoneId.systemDefault())
        val event = holidayEventsRepository.eventForDate(today) ?: return

        val todayIso = today.toString()
        if (settingsRepository.lastHolidayEventPopupShownDate.first() == todayIso) return
        settingsRepository.setLastHolidayEventPopupShownDate(todayIso)
        _holidayEventPopup.value = event
    }

    fun dismissBirthdayPopup() {
        _birthdayPopupData.value = null
    }

    fun dismissHolidayEventPopup() {
        _holidayEventPopup.value = null
    }
}
