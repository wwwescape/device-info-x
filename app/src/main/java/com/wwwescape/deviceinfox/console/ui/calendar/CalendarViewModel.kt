package com.wwwescape.deviceinfox.console.ui.calendar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository
import com.wwwescape.deviceinfox.console.data.calendar.IntimacyLogInput
import com.wwwescape.deviceinfox.console.data.db.EventType
import com.wwwescape.deviceinfox.console.data.db.PartnerEntity
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.release.ReleaseRepository
import com.wwwescape.deviceinfox.console.data.vault.VaultRepository
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val partnerRepository: PartnerRepository,
    private val vaultRepository: VaultRepository,
    private val sessionManager: ConsoleSessionManager,
    releaseRepository: ReleaseRepository,
    liveLocationRepository: LiveLocationRepository,
) : ViewModel() {

    val calendarWipedEvent: SharedFlow<Unit> = calendarRepository.calendarWipedEvent

    /** Drives the Settings gear icon's badge dot on this tab — every tab has its own Settings
     * entry point (`onSettingsClick`), so each needs this independently; see [ReleaseRepository]'s
     * own doc comment for when/how this actually gets checked. */
    val isUpdateAvailable: StateFlow<Boolean> = releaseRepository.isUpdateAvailable

    /** Takes precedence over [isUpdateAvailable] on the same badge dot — see [HomeViewModel]'s
     * identical field. */
    val isLiveLocationActive: StateFlow<Boolean> = combine(
        liveLocationRepository.selfSharing,
        liveLocationRepository.partnerLocation,
    ) { self, partner -> self || partner.isSharing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<CalendarUiState> = calendarRepository.agenda
        .map { CalendarUiState(items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Feeds the Optional Details section's "Who started it" picker — labeled with the two
     * partners' actual first names, not a generic "Myself/Partner". `null` until pairing has
     * synced the partner's own profile down. */
    val selfPartner: StateFlow<PartnerEntity?> = partnerRepository.selfProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val partnerPartner: StateFlow<PartnerEntity?> = partnerRepository.partnerProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Same reasoning as HomeViewModel's errorEvent: saveEvent/deleteEvent used to run inside a
    // bare runCatching with no failure branch at all, so the editor dialog already dismisses
    // itself the instant it calls onSave — a real failure (a 4xx/5xx from the server, or an
    // actual network drop) looked identical to a successful save, with nothing to tell them
    // apart. Null means "no server-provided detail, show a generic network-error message" (a
    // real IOException/timeout, not a ConsoleApiException) — resolving that generic string is
    // left to the UI layer, same division HomeViewModel/HomeScreen already use.
    private val _saveFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val saveFailed: SharedFlow<String?> = _saveFailed.asSharedFlow()

    private val _deleteFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val deleteFailed: SharedFlow<String?> = _deleteFailed.asSharedFlow()

    // Blocks the editor dialog's Save button, and — paired with saveSucceeded — lets it dismiss
    // only once the network call actually resolves, instead of dismissing synchronously the
    // instant Save is tapped (which used to make a slow save and a failed save look identical:
    // the dialog was already gone either way).
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _saveSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSucceeded: SharedFlow<Unit> = _saveSucceeded.asSharedFlow()

    private val _deleteSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteSucceeded: SharedFlow<Unit> = _deleteSucceeded.asSharedFlow()

    init {
        // Calendar events have no WebSocket push confirmation guarantee across app restarts
        // (see CalendarRepository's doc comment), so re-fetch on every visit to this screen
        // rather than relying solely on the singleton repository's one-time init sync.
        viewModelScope.launch { runCatching { calendarRepository.refresh() } }
    }

    fun saveEvent(
        id: String?,
        title: String,
        type: EventType,
        notes: String?,
        startAtEpochMillis: Long,
        endAtEpochMillis: Long?,
        isAllDay: Boolean,
        location: String?,
        recurrenceRule: String?,
        cancelled: Boolean,
        cancellationReason: String?,
        cancelledBy: String?,
        reminderMinutesBefore: List<Int>,
        intimacy: IntimacyLogInput?,
        newMediaUris: List<Uri> = emptyList(),
        newDocumentUris: List<Uri> = emptyList(),
        keptAttachmentMediaIds: List<String> = emptyList(),
        newCoverPhotoUri: Uri? = null,
        keptCoverMediaId: String? = null,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            runCatching {
                calendarRepository.saveEvent(
                    id = id,
                    title = title,
                    type = type,
                    notes = notes,
                    startAtEpochMillis = startAtEpochMillis,
                    endAtEpochMillis = endAtEpochMillis,
                    isAllDay = isAllDay,
                    location = location,
                    recurrenceRule = recurrenceRule,
                    cancelled = cancelled,
                    cancellationReason = cancellationReason,
                    cancelledBy = cancelledBy,
                    reminderMinutesBefore = reminderMinutesBefore,
                    intimacy = intimacy,
                    newMediaUris = newMediaUris,
                    newDocumentUris = newDocumentUris,
                    keptAttachmentMediaIds = keptAttachmentMediaIds,
                    newCoverPhotoUri = newCoverPhotoUri,
                    keptCoverMediaId = keptCoverMediaId,
                )
            }.onSuccess { _saveSucceeded.tryEmit(Unit) }
                .onFailure { _saveFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isSaving.value = false
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            _isDeleting.value = true
            runCatching { calendarRepository.deleteEvent(id) }
                .onSuccess { _deleteSucceeded.tryEmit(Unit) }
                .onFailure { _deleteFailed.tryEmit((it as? ConsoleApiException)?.detail) }
            _isDeleting.value = false
        }
    }

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    private val _imageExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageExportedEvent: SharedFlow<Unit> = _imageExportedEvent.asSharedFlow()

    private val _videoExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val videoExportedEvent: SharedFlow<Unit> = _videoExportedEvent.asSharedFlow()

    private val _documentExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val documentExportedEvent: SharedFlow<Unit> = _documentExportedEvent.asSharedFlow()

    fun exportEventImageAttachment(attachment: MessageAttachment, destinationUri: Uri) {
        viewModelScope.launch {
            runCatching { calendarRepository.exportEventImage(attachment, destinationUri) }
                .onSuccess { _imageExportedEvent.tryEmit(Unit) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    /** Mirrors `HomeViewModel.ensureVideoDownloaded` — called directly from `VideoPreviewDialog`'s
     * `LaunchedEffect`, not fire-and-forget, since the caller needs the resulting path in hand. */
    suspend fun ensureEventVideoDownloaded(eventId: String, attachment: MessageAttachment): String? =
        runCatching { calendarRepository.downloadEventVideoIfNeeded(eventId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** Mirrors `HomeViewModel.exportVideo` — always ensures the download itself first, rather
     * than trusting [attachment]'s own (possibly still-null) `localVideoFilePath`. */
    fun exportEventVideoAttachment(eventId: String, attachment: MessageAttachment, destinationUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val localPath = calendarRepository.downloadEventVideoIfNeeded(eventId, attachment)
                calendarRepository.exportEventVideo(attachment.copy(localVideoFilePath = localPath), destinationUri)
            }.onSuccess { _videoExportedEvent.tryEmit(Unit) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    /** Mirrors `HomeViewModel.exportDocument` — one combined ensure-download-then-export action;
     * `PdfPreviewDialog`'s own `ensureDocumentDownloaded` may already have resolved the path by
     * the time this runs, in which case `downloadEventDocumentIfNeeded` is a quick no-op. */
    fun exportEventDocumentAttachment(eventId: String, attachment: MessageAttachment, destinationUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val localPath = calendarRepository.downloadEventDocumentIfNeeded(eventId, attachment)
                calendarRepository.exportEventDocument(attachment.copy(localDocumentFilePath = localPath), destinationUri)
            }.onSuccess { _documentExportedEvent.tryEmit(Unit) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    /** Mirrors `ensureEventVideoDownloaded`, document-flavored — `PdfPreviewDialog` calls this
     * from its own `LaunchedEffect` to show a loading state while the document downloads before
     * `PdfPageViewer` can open it. */
    suspend fun ensureEventDocumentDownloaded(eventId: String, attachment: MessageAttachment): String? =
        runCatching { calendarRepository.downloadEventDocumentIfNeeded(eventId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** "Save to Vault" for an event's attachment — mirrors `HomeViewModel.saveAttachmentToVault`'s
     * trio exactly, just passing `originalEventId` instead of `originalMessageId` (see
     * `VaultRepository.saveAttachmentToVault`'s doc comment for why an event needs both
     * `originalEventId` and the attachment's own `mediaId` to dedupe correctly, unlike a message). */
    private val _vaultSaveEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vaultSaveEvent: SharedFlow<Boolean> = _vaultSaveEvent.asSharedFlow()

    /** Same re-entry-guard shape as `HomeViewModel.pendingVaultSaveMessageIds` — keyed on
     * `eventId` rather than `messageId`, closing the same double-tap-duplicates-a-vault-item race
     * for Calendar's own Save-to-Vault buttons. */
    private val _pendingVaultSaveEventIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingVaultSaveEventIds: StateFlow<Set<String>> = _pendingVaultSaveEventIds.asStateFlow()

    fun saveEventImageAttachmentToVault(eventId: String, attachment: MessageAttachment) {
        if (eventId in _pendingVaultSaveEventIds.value) return
        _pendingVaultSaveEventIds.update { it + eventId }
        viewModelScope.launch {
            runCatching { vaultRepository.saveAttachmentToVault(attachment, originalEventId = eventId) }
                .onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveEventIds.update { it - eventId }
        }
    }

    fun saveEventVideoAttachmentToVault(eventId: String, attachment: MessageAttachment) {
        if (eventId in _pendingVaultSaveEventIds.value) return
        _pendingVaultSaveEventIds.update { it + eventId }
        viewModelScope.launch {
            runCatching {
                val localPath = calendarRepository.downloadEventVideoIfNeeded(eventId, attachment)
                vaultRepository.saveAttachmentToVault(attachment.copy(localVideoFilePath = localPath), originalEventId = eventId)
            }.onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveEventIds.update { it - eventId }
        }
    }

    fun saveEventDocumentAttachmentToVault(eventId: String, attachment: MessageAttachment) {
        if (eventId in _pendingVaultSaveEventIds.value) return
        _pendingVaultSaveEventIds.update { it + eventId }
        viewModelScope.launch {
            runCatching {
                val localPath = calendarRepository.downloadEventDocumentIfNeeded(eventId, attachment)
                vaultRepository.saveAttachmentToVault(attachment.copy(localDocumentFilePath = localPath), originalEventId = eventId)
            }.onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveEventIds.update { it - eventId }
        }
    }

    /** Must bracket any launch of a system picker (the Export "Save As" dialog) — see
     * [ConsoleSessionManager.isExpectingTransientResult]. */
    fun beginPickerLaunch() = sessionManager.beginExpectingTransientResult()

    fun endPickerLaunch() = sessionManager.endExpectingTransientResult()
}
