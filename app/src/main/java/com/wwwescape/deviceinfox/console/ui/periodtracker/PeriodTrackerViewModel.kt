package com.wwwescape.deviceinfox.console.ui.periodtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository
import com.wwwescape.deviceinfox.console.data.cycle.CycleRepository
import com.wwwescape.deviceinfox.console.data.cycle.CycleVisibility
import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.pairing.PairingRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingStatus
import com.wwwescape.deviceinfox.console.data.release.ReleaseRepository
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeriodTrackerViewModel @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val pairingRepository: PairingRepository,
    private val consoleSettingsRepository: ConsoleSettingsRepository,
    private val calendarRepository: CalendarRepository,
    releaseRepository: ReleaseRepository,
    liveLocationRepository: LiveLocationRepository,
) : ViewModel() {

    /** Which tab is active in [CycleVisibility.TwoCycles] mode — null means "self" (the
     * default tab). Irrelevant for [CycleVisibility.SingleCycle], which has no tabs. */
    private val selectedTabPartnerId = MutableStateFlow<String?>(null)

    /** Drives the Settings gear icon's badge dot on this tab — see [CalendarViewModel]'s
     * identical field and [ReleaseRepository]'s own doc comment. */
    val isUpdateAvailable: StateFlow<Boolean> = releaseRepository.isUpdateAvailable

    /** Takes precedence over [isUpdateAvailable] on the same badge dot — see [HomeViewModel]'s
     * identical field. */
    val isLiveLocationActive: StateFlow<Boolean> = combine(
        liveLocationRepository.selfSharing,
        liveLocationRepository.partnerLocation,
    ) { self, partner -> self || partner.isSharing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // saveDayLog/deleteDayLog used to run inside a bare runCatching with no branches at all — a
    // failed save/delete looked completely silent (not even a Toast), and the editor dialog
    // dismissed itself the instant Save/Delete was tapped regardless of whether the network call
    // had even completed yet. Same isSaving/succeeded/failed shape as CalendarViewModel.
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _saveSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSucceeded: SharedFlow<Unit> = _saveSucceeded.asSharedFlow()

    private val _saveFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val saveFailed: SharedFlow<String?> = _saveFailed.asSharedFlow()

    private val _deleteSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteSucceeded: SharedFlow<Unit> = _deleteSucceeded.asSharedFlow()

    private val _deleteFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val deleteFailed: SharedFlow<String?> = _deleteFailed.asSharedFlow()

    val uiState: StateFlow<PeriodTrackerUiState> = combine(
        perTabState(),
        calendarRepository.intimacyDates,
    ) { state, intimacyDates -> state.copy(intimacyDates = intimacyDates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodTrackerUiState())

    /** Everything [uiState] carried before the Intimacy Log overlay was added — kept as its own
     * function since intimacy is couple-wide (not per-tab), so it combines at the outer level
     * above rather than inside this per-tab pipeline. */
    private fun perTabState() = combine(
        cycleRepository.visibility,
        pairingRepository.pairingStatus,
        selectedTabPartnerId,
    ) { visibility, pairingStatus, tabOverride ->
        val partnerDisplayName = (pairingStatus as? PairingStatus.Paired)?.partnerDisplayName
        val selectedId: String?
        val canEdit: Boolean
        when (visibility) {
            is CycleVisibility.TwoCycles -> {
                selectedId = tabOverride ?: visibility.selfId
                canEdit = selectedId == visibility.selfId
            }
            is CycleVisibility.SingleCycle -> {
                selectedId = visibility.trackedPartnerId
                canEdit = visibility.isSelf
            }
            CycleVisibility.Hidden -> {
                selectedId = null
                canEdit = false
            }
        }
        CombinedTabState(visibility, partnerDisplayName, selectedId, canEdit)
    }.flatMapLatest { (visibility, partnerDisplayName, selectedId, canEdit) ->
        if (selectedId == null) {
            flowOf(PeriodTrackerUiState(visibility = visibility))
        } else {
            val cycleLengthSeedFlow = if (canEdit) consoleSettingsRepository.averageCycleLengthDaysSeed else flowOf(null)
            val periodLengthSeedFlow = if (canEdit) consoleSettingsRepository.averagePeriodLengthDaysSeed else flowOf(null)
            val lutealPhaseSeedFlow = if (canEdit) consoleSettingsRepository.averageLutealPhaseDaysSeed else flowOf(null)
            combine(
                cycleRepository.observeDayLogs(selectedId),
                cycleRepository.observePrediction(selectedId, cycleLengthSeedFlow, periodLengthSeedFlow, lutealPhaseSeedFlow),
            ) { dayLogs, prediction ->
                PeriodTrackerUiState(
                    visibility = visibility,
                    selectedPartnerId = selectedId,
                    partnerDisplayName = partnerDisplayName,
                    dayLogs = dayLogs,
                    prediction = prediction,
                    canEdit = canEdit,
                )
            }.onStart {
                // No WebSocket push for cycles (see CycleRepository's doc comment) - re-fetch
                // this tab every time it's (re)selected/opened rather than only once.
                runCatching { cycleRepository.refresh(selectedId) }
            }
        }
    }

    fun selectTab(partnerId: String) {
        selectedTabPartnerId.value = partnerId
    }

    /** Pure pass-through to [CycleRepository.isUnexpectedCycleStart] against whatever's already
     * loaded in [uiState] — the Screen calls this from a *new* day log's Save action, before ever
     * calling [saveDayLog], to decide whether to show a confirmation first. */
    fun isUnexpectedCycleStart(dateEpochMillis: Long, flowIntensity: CycleFlowIntensity?): Boolean =
        cycleRepository.isUnexpectedCycleStart(uiState.value.dayLogs, uiState.value.prediction, dateEpochMillis, flowIntensity)

    fun saveDayLog(
        id: String?,
        dateEpochMillis: Long,
        flowIntensity: CycleFlowIntensity?,
        symptoms: List<String>,
        notes: String?,
    ) {
        if (!uiState.value.canEdit) return
        val partnerId = uiState.value.selectedPartnerId ?: return
        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                cycleRepository.saveDayLog(id, partnerId, dateEpochMillis, flowIntensity, symptoms, notes)
            }.onSuccess { _saveSucceeded.tryEmit(Unit) }
                .onFailure { _saveFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isSaving.value = false
        }
    }

    fun deleteDayLog(id: String) {
        if (!uiState.value.canEdit) return
        viewModelScope.launch {
            _isDeleting.value = true
            runCatching { cycleRepository.deleteDayLog(id) }
                .onSuccess { _deleteSucceeded.tryEmit(Unit) }
                .onFailure { _deleteFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isDeleting.value = false
        }
    }

    /** Onboarding's "Get Started" action — persists all three local seeds then logs one day per
     * day of the assumed period-length range via [CycleRepository.saveOnboardingDayLogs] (each
     * defaulted to MEDIUM flow, individually editable afterward), which is what makes
     * onboarding's visibility condition (`dayLogs.isEmpty()`, see `PeriodTrackerScreen`)
     * permanently false afterward. */
    fun completeOnboarding(
        startDateEpochMillis: Long,
        cycleLengthSeedDays: Int,
        periodLengthSeedDays: Int,
        lutealPhaseSeedDays: Int,
    ) {
        if (!uiState.value.canEdit || _isSaving.value) return
        val partnerId = uiState.value.selectedPartnerId ?: return
        viewModelScope.launch {
            runCatching { consoleSettingsRepository.setAverageCycleLengthDaysSeed(cycleLengthSeedDays) }
            runCatching { consoleSettingsRepository.setAveragePeriodLengthDaysSeed(periodLengthSeedDays) }
            runCatching { consoleSettingsRepository.setAverageLutealPhaseDaysSeed(lutealPhaseSeedDays) }

            _isSaving.value = true
            runCatching {
                cycleRepository.saveOnboardingDayLogs(partnerId, startDateEpochMillis, periodLengthSeedDays)
            }.onSuccess { _saveSucceeded.tryEmit(Unit) }
                .onFailure { _saveFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isSaving.value = false
        }
    }

    private data class CombinedTabState(
        val visibility: CycleVisibility,
        val partnerDisplayName: String?,
        val selectedId: String?,
        val canEdit: Boolean,
    )
}
