package com.wwwescape.deviceinfox.console.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.auth.AuthRepository
import com.wwwescape.deviceinfox.console.data.auth.DuressStatus
import com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository
import com.wwwescape.deviceinfox.console.data.cycle.CycleRepository
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import com.wwwescape.deviceinfox.console.data.network.AccountRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.network.ConsoleServerConfig
import com.wwwescape.deviceinfox.console.data.network.ServerStatusRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingRepository
import com.wwwescape.deviceinfox.console.data.pairing.ServerPartnerCodeRepository
import com.wwwescape.deviceinfox.console.data.release.ReleaseRepository
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import com.wwwescape.deviceinfox.console.data.settings.NotificationImportance
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone
import com.wwwescape.deviceinfox.console.data.settings.NotificationTier
import com.wwwescape.deviceinfox.console.data.settings.TonePreviewPlayer
import com.wwwescape.deviceinfox.console.data.vault.VaultRepository
import com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Frequent enough that "the server just came back up" reflects in the UI without a manual
 * refresh, infrequent enough not to spam a health check nobody's watching for most of that time. */
private const val SERVER_STATUS_POLL_INTERVAL_MILLIS = 15_000L

@HiltViewModel
class ConsoleSettingsViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val partnerCodeRepository: ServerPartnerCodeRepository,
    private val consoleSettingsRepository: ConsoleSettingsRepository,
    private val pairingRepository: PairingRepository,
    private val sessionManager: ConsoleSessionManager,
    private val serverConfig: ConsoleServerConfig,
    private val messageRepository: MessageRepository,
    private val calendarRepository: CalendarRepository,
    private val cycleRepository: CycleRepository,
    private val vaultRepository: VaultRepository,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val serverStatusRepository: ServerStatusRepository,
    private val channelManager: ConsolePushChannelManager,
    private val tonePreviewPlayer: TonePreviewPlayer,
    releaseRepository: ReleaseRepository,
    liveLocationRepository: LiveLocationRepository,
) : ViewModel() {

    /** Not part of [uiState]'s `combine` (already at the 5-flow direct-overload limit) — reads
     * synchronously off [ConsoleServerConfig]'s `StateFlow` since the configured server
     * practically never changes while Settings is open. */
    val serverUrl: String? get() = serverConfig.baseUrl.value

    /** Drives the flashing "Update available" card at the top of this screen — see
     * [ReleaseRepository]'s own doc comment for when/how this actually gets checked (not on this
     * screen's own open, only on WS connect). */
    val isUpdateAvailable: StateFlow<Boolean> = releaseRepository.isUpdateAvailable
    val updateDownloadUrl: StateFlow<String?> = releaseRepository.downloadUrl

    /** Drives the badge dot on the map-pin icon specifically (not the gear icon — once you're
     * already on this screen, the gear's own dot on every *other* tab has done its job; the
     * map-pin is where the "something live is happening" signal belongs here instead). */
    val isLiveLocationActive: StateFlow<Boolean> = combine(
        liveLocationRepository.selfSharing,
        liveLocationRepository.partnerLocation,
    ) { self, partner -> self || partner.isSharing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Polled on a fixed interval for as long as Settings stays open (this coroutine dies with
     * the ViewModel when the screen is left) — not tied into [uiState]'s `combine` for the same
     * reason [serverUrl] isn't, plus polling belongs to this screen's lifetime specifically,
     * unlike everything else in [uiState] which reflects state that exists independent of
     * whether Settings happens to be open. */
    private val _serverStatus = MutableStateFlow(ServerStatus.CHECKING)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _serverStatus.value = if (serverStatusRepository.isOnline()) ServerStatus.ONLINE else ServerStatus.OFFLINE
                delay(SERVER_STATUS_POLL_INTERVAL_MILLIS)
            }
        }
    }

    /** Passive readout for the "Duress Code status" row — see [AuthRepository.duressStatus]. Read
     * once on open rather than folded into [uiState]'s `combine`: it can only change via a code
     * unlock/change, neither of which happens while this screen is open. */
    private val _duressStatus = MutableStateFlow(DuressStatus.PENDING)
    val duressStatus: StateFlow<DuressStatus> = _duressStatus.asStateFlow()

    init {
        viewModelScope.launch { _duressStatus.value = authRepository.duressStatus() }
    }

    /** Filled in from `GET /pairing/code`'s masked preview on load — [ServerPartnerCodeRepository.fullCode]
     * takes priority once the user actually regenerates, since that's the only moment the real
     * code is knowable at all. */
    private val partnerCodePreview = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConsoleSettingsUiState> = combine(
        partnerRepository.selfProfile,
        partnerCodeRepository.fullCode,
        partnerCodePreview,
        consoleSettingsRepository.notificationTier,
        pairingRepository.pairingStatus,
    ) { profile, fullCode, preview, tier, pairingStatus ->
        ConsoleSettingsUiState(
            displayName = profile?.displayName.orEmpty(),
            firstName = profile?.firstName.orEmpty(),
            lastName = profile?.lastName.orEmpty(),
            photoUri = profile?.photoUri,
            birthdayEpochMillis = profile?.birthdayEpochMillis,
            gender = profile?.gender ?: PartnerGender.UNSPECIFIED,
            notificationTier = tier,
            partnerCode = fullCode ?: preview,
            pairingStatus = pairingStatus,
            // What *this* user wrote — shown on the *partner's* birthday popup, never this
            // user's own (see PartnerEntity.birthdayMessage's doc comment).
            birthdayCustomMessage = profile?.birthdayMessage,
        )
    }.combine(consoleSettingsRepository.averageCycleLengthDaysSeed) { state, seed ->
        state.copy(averageCycleLengthDaysSeed = seed)
    }.combine(consoleSettingsRepository.averagePeriodLengthDaysSeed) { state, seed ->
        state.copy(averagePeriodLengthDaysSeed = seed)
    }.combine(consoleSettingsRepository.averageLutealPhaseDaysSeed) { state, seed ->
        state.copy(averageLutealPhaseDaysSeed = seed)
    }.combine(consoleSettingsRepository.notificationImportance) { state, importance ->
        state.copy(notificationImportance = importance)
    }.combine(consoleSettingsRepository.notificationSoundMode) { state, soundMode ->
        state.copy(notificationSoundMode = soundMode)
    }.combine(consoleSettingsRepository.notificationSoundTone) { state, soundTone ->
        state.copy(notificationSoundTone = soundTone)
    }.combine(consoleSettingsRepository.notificationSoundThrottleMinutes) { state, throttleMinutes ->
        state.copy(notificationSoundThrottleMinutes = throttleMinutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsoleSettingsUiState())

    init {
        viewModelScope.launch {
            partnerCodePreview.value = runCatching { partnerCodeRepository.fetchPreview() }.getOrNull()
        }
    }

    // saveProfile used to run inside a bare runCatching with no branches at all — a failed save
    // was completely silent, and a successful one had no confirmation either.
    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _saveProfileSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveProfileSucceeded: SharedFlow<Unit> = _saveProfileSucceeded.asSharedFlow()

    private val _saveProfileFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val saveProfileFailed: SharedFlow<String?> = _saveProfileFailed.asSharedFlow()

    fun saveProfile(
        displayName: String,
        firstName: String,
        lastName: String,
        photoUri: String?,
        birthdayEpochMillis: Long?,
        gender: PartnerGender,
    ) {
        viewModelScope.launch {
            _isSavingProfile.value = true
            runCatching {
                partnerRepository.saveSelfProfile(displayName, firstName, lastName, photoUri, birthdayEpochMillis, gender)
            }.onSuccess { _saveProfileSucceeded.tryEmit(Unit) }
                .onFailure { _saveProfileFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isSavingProfile.value = false
        }
    }

    fun setNotificationTier(tier: NotificationTier) {
        viewModelScope.launch { consoleSettingsRepository.setNotificationTier(tier) }
    }

    /** Persists the choice and re-applies it to the live notification channel immediately —
     * unlike [setNotificationTier], this can't just wait for the wording to be read next time a
     * push arrives, since the channel itself (not just what it says) is what needs to change. */
    fun setNotificationImportance(importance: NotificationImportance) {
        viewModelScope.launch {
            consoleSettingsRepository.setNotificationImportance(importance)
            channelManager.syncChannels()
        }
    }

    /** Unlike [setNotificationImportance]/[setNotificationSoundTone], this never needs to touch
     * [channelManager] — both the audible and silent channels already exist regardless of this
     * setting; changing the mode only affects which of them a future push picks, resolved fresh
     * each time in `ConsoleFcmService.resolveChannelId`. */
    fun setNotificationSoundMode(mode: NotificationSoundMode) {
        viewModelScope.launch { consoleSettingsRepository.setNotificationSoundMode(mode) }
    }

    /** Same reasoning as [setNotificationSoundMode] — never touches [channelManager], since this
     * only changes how `ConsoleFcmService.resolveChannelId` measures [NotificationSoundMode.THROTTLED]'s
     * window on the next push, not anything about the channels themselves. */
    fun setNotificationSoundThrottleMinutes(minutes: Int) {
        viewModelScope.launch { consoleSettingsRepository.setNotificationSoundThrottleMinutes(minutes) }
    }

    /** Unlike [setNotificationSoundMode], this does need to touch [channelManager] — a channel's
     * sound can only be set at creation, so switching tones deletes and recreates the audible
     * channel (see [ConsolePushChannelManager]'s class doc). */
    fun setNotificationSoundTone(tone: NotificationSoundTone) {
        viewModelScope.launch {
            consoleSettingsRepository.setNotificationSoundTone(tone)
            channelManager.syncChannels()
        }
    }

    /** Which tone is currently loaded into the Settings preview button — playing or paused — if
     * any, deliberately independent of [setNotificationSoundTone]: previewing a tone never changes
     * the saved selection, and picking a tone never starts/stops a preview. See [TonePreviewPlayer]. */
    val previewingTone: StateFlow<NotificationSoundTone?> = tonePreviewPlayer.activeTone

    /** True only while [previewingTone] is actively playing, not merely loaded-and-paused — drives
     * the preview button's Play/Pause icon. */
    val isTonePreviewPlaying: StateFlow<Boolean> = tonePreviewPlayer.isPlaying

    /** Elapsed fraction of [previewingTone]'s playback, driving the preview button's progress
     * ring — `0f` (idle, faint outline only) whenever nothing is loaded; holds its last value
     * while paused rather than resetting, so pausing doesn't visually read as "starting over." */
    val previewProgress: StateFlow<Float> = tonePreviewPlayer.progress

    fun previewTone(tone: NotificationSoundTone) = tonePreviewPlayer.playOrToggle(tone)

    /** Called when the Settings screen leaves composition, so a preview can't keep playing in the
     * background after navigating away — [TonePreviewPlayer] has no lifecycle of its own to catch
     * this itself, unlike [VoicePlayer][com.wwwescape.deviceinfox.console.data.messaging.VoicePlayer]'s
     * existing call sites, which never needed this precaution. */
    fun stopTonePreview() = tonePreviewPlayer.stop()

    fun setAverageCycleLengthDaysSeed(days: Int) {
        viewModelScope.launch { consoleSettingsRepository.setAverageCycleLengthDaysSeed(days) }
    }

    fun setAveragePeriodLengthDaysSeed(days: Int) {
        viewModelScope.launch { consoleSettingsRepository.setAveragePeriodLengthDaysSeed(days) }
    }

    fun setAverageLutealPhaseDaysSeed(days: Int) {
        viewModelScope.launch { consoleSettingsRepository.setAverageLutealPhaseDaysSeed(days) }
    }

    fun setBirthdayCustomMessage(message: String) {
        viewModelScope.launch { runCatching { partnerRepository.setBirthdayMessage(message) } }
    }

    private val _isRegeneratingCode = MutableStateFlow(false)
    val isRegeneratingCode: StateFlow<Boolean> = _isRegeneratingCode.asStateFlow()

    private val _regenerateCodeFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val regenerateCodeFailed: SharedFlow<String?> = _regenerateCodeFailed.asSharedFlow()

    fun regeneratePartnerCode() {
        viewModelScope.launch {
            _isRegeneratingCode.value = true
            runCatching { partnerCodeRepository.generateCode() }
                .onFailure { _regenerateCodeFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isRegeneratingCode.value = false
        }
    }

    private val _isUnpairing = MutableStateFlow(false)
    val isUnpairing: StateFlow<Boolean> = _isUnpairing.asStateFlow()

    private val _unpairFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val unpairFailed: SharedFlow<String?> = _unpairFailed.asSharedFlow()

    /** [unpairSucceeded] flipping true is the caller's cue to lock/exit, same as [serverChanged]
     * — there's no account left paired to show a settings screen for. `unpair()` previously
     * wasn't even wrapped in `runCatching` at all, so a failure here was an uncaught exception
     * inside `viewModelScope.launch` rather than just a silent no-op. */
    private val _unpairSucceeded = MutableStateFlow(false)
    val unpairSucceeded: StateFlow<Boolean> = _unpairSucceeded.asStateFlow()

    fun unpair() {
        viewModelScope.launch {
            _isUnpairing.value = true
            runCatching { pairingRepository.unpair() }
                .onSuccess { _unpairSucceeded.value = true }
                .onFailure { _unpairFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isUnpairing.value = false
        }
    }

    private val _deleteSucceeded = MutableSharedFlow<DataSection?>(extraBufferCapacity = 1)

    /** Emits which section just finished deleting successfully — null means "Delete all data".
     * Every "Delete data"/"Delete all data" action used to close its confirmation dialog and go
     * completely silent regardless of outcome; that silence is what made a real, recurring
     * failure (see [deleteFailed]) look like a no-op bug rather than an error worth retrying. */
    val deleteSucceeded: SharedFlow<DataSection?> = _deleteSucceeded.asSharedFlow()

    private val _deleteFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteFailed: SharedFlow<Unit> = _deleteFailed.asSharedFlow()

    private val _isDeleting = MutableStateFlow(false)

    /** Shared by [deleteData] and [deleteAllData] — only one of the 5 delete confirmation dialogs
     * can be open at a time, so a single flag is enough to block its buttons while the delete is
     * in flight, the same way [DestroyAccountViewModel][com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountViewModel]'s
     * `isDestroying` blocks its own confirmation dialog. */
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /** The "Delete data" action for one of the 4 content sections. */
    fun deleteData(section: DataSection) {
        viewModelScope.launch {
            _isDeleting.value = true
            runCatching {
                when (section) {
                    DataSection.MESSAGES -> messageRepository.wipeConversation()
                    DataSection.CALENDAR -> calendarRepository.wipeCalendar()
                    DataSection.PERIOD -> cycleRepository.wipeAllDayLogs()
                    DataSection.LOCKER -> vaultRepository.deleteAllMyLockerData()
                }
            }.onSuccess { _deleteSucceeded.tryEmit(section) }.onFailure { _deleteFailed.tryEmit(Unit) }
            _isDeleting.value = false
        }
    }

    /** The "Delete all data" Dangerous action — same effect as running all 4 section deletes at
     * once; each runs independently so one section failing doesn't block the others, but
     * [deleteSucceeded]/[deleteFailed] only reports success once every one of them has. */
    fun deleteAllData() {
        viewModelScope.launch {
            _isDeleting.value = true
            val results = listOf(
                runCatching { messageRepository.wipeConversation() },
                runCatching { calendarRepository.wipeCalendar() },
                runCatching { cycleRepository.wipeAllDayLogs() },
                runCatching { vaultRepository.deleteAllMyLockerData() },
            )
            if (results.all { it.isSuccess }) _deleteSucceeded.tryEmit(null) else _deleteFailed.tryEmit(Unit)
            _isDeleting.value = false
        }
    }

    private val _serverChanged = MutableStateFlow(false)
    val serverChanged: StateFlow<Boolean> = _serverChanged.asStateFlow()

    private val _isChangingServer = MutableStateFlow(false)
    val isChangingServer: StateFlow<Boolean> = _isChangingServer.asStateFlow()

    /** Persistent state rather than a one-shot event like [deleteFailed] — [ChangeServerDialog]
     * renders this as inline text and stays open on failure (mirroring
     * [DestroyAccountDialog][com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog]'s
     * `destroyFailed`), since a Toast would arrive after the console has already exited on
     * success and would be pointless to show while the dialog itself is still up on failure. */
    private val _changeServerFailed = MutableStateFlow(false)
    val changeServerFailed: StateFlow<Boolean> = _changeServerFailed.asStateFlow()

    /** "Change server" — wipes every local trace of the current server's account and points the
     * app at [newServerUrl]. [serverChanged] flipping true is the caller's cue to lock the
     * console the same way [DestroyAccountDialog][com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog]'s
     * `onDestroyed` does — there's no account left connected to show a settings screen for. */
    fun changeServer(newServerUrl: String) {
        viewModelScope.launch {
            _isChangingServer.value = true
            _changeServerFailed.value = false
            runCatching { accountRepository.changeServer(newServerUrl) }
                .onSuccess { _serverChanged.value = true }
                .onFailure { _changeServerFailed.value = true }
            _isChangingServer.value = false
        }
    }

    /** Must bracket any launch of a system picker (e.g. the profile photo picker) — see
     * [ConsoleSessionManager.isExpectingTransientResult]. */
    fun beginPickerLaunch() = sessionManager.beginExpectingTransientResult()

    fun endPickerLaunch() = sessionManager.endExpectingTransientResult()
}
