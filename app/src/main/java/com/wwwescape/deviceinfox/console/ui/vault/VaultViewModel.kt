package com.wwwescape.deviceinfox.console.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.release.ReleaseRepository
import com.wwwescape.deviceinfox.console.data.vault.VaultAlbum
import com.wwwescape.deviceinfox.console.data.vault.VaultItem
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val sessionManager: ConsoleSessionManager,
    releaseRepository: ReleaseRepository,
    liveLocationRepository: LiveLocationRepository,
) : ViewModel() {

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

    private val selectedFilter = MutableStateFlow<VaultFilter>(VaultFilter.All)
    private val isSearching = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")

    /** See `HomeViewModel._selectedMessageIds`'s identical reasoning — selection mode is derived
     * (non-empty set), not a separate flag. */
    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())

    /** See `HomeViewModel`'s identical field for why this exists and why it carries a nullable
     * server detail rather than a resolved string. */
    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    /** Fires once a [launchCatching]-wrapped action completes successfully — previously only
     * failure was ever signaled, so a successful caption/move/rename/delete looked identical to
     * one that was still in flight (the dialog was already gone either way). One shared flow for
     * every action here is enough since `VaultScreen`'s `var ...ItemId by remember` pattern only
     * ever shows one dialog at a time. */
    private val _actionSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val actionSucceeded: SharedFlow<Unit> = _actionSucceeded.asSharedFlow()

    /** Blocks whichever dialog is currently open while its [launchCatching]-wrapped action is in
     * flight — previously every dialog dismissed itself synchronously on confirm, before the
     * network call had even started. */
    private val _isPending = MutableStateFlow(false)
    val isPending: StateFlow<Boolean> = _isPending.asStateFlow()

    val lockerWipedEvent: SharedFlow<Unit> = vaultRepository.lockerWipedEvent

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isPending.value = true
            runCatching { block() }
                .onSuccess { _actionSucceeded.tryEmit(Unit) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _isPending.value = false
        }
    }

    private data class FilterState(val filter: VaultFilter, val isSearching: Boolean, val searchQuery: String)

    // kotlinx.coroutines.flow.combine has direct overloads only up to 5 flows (see
    // HomeViewModel's own comment on the same constraint) — nesting the 3 filter/search flows
    // into one sub-combine first keeps every branch type-safe rather than falling back to the
    // untyped vararg overload now that selection needs a 6th flow.
    private val filterState = combine(selectedFilter, isSearching, searchQuery) { filter, searching, query ->
        FilterState(filter, searching, query)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        vaultRepository.items,
        vaultRepository.albums,
        filterState,
        _selectedItemIds,
    ) { items, albums, filter, selectedItemIds ->
        VaultUiState(
            items = items,
            albums = albums,
            selectedFilter = filter.filter,
            isSearching = filter.isSearching,
            searchQuery = filter.searchQuery,
            selectedItemIds = selectedItemIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun selectFilter(filter: VaultFilter) {
        selectedFilter.value = filter
    }

    fun toggleSearch() {
        isSearching.value = !isSearching.value
        searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** New imports land in the currently-filtered album, if any — importing while browsing
     * "Our Trip" adds straight into "Our Trip" rather than needing a second move step. */
    fun importImages(uris: List<Uri>) {
        val albumId = (selectedFilter.value as? VaultFilter.Album)?.albumId
        launchCatching { vaultRepository.importImages(uris, albumId) }
    }

    fun importVideos(uris: List<Uri>) {
        val albumId = (selectedFilter.value as? VaultFilter.Album)?.albumId
        launchCatching { vaultRepository.importVideos(uris, albumId) }
    }

    fun importDocuments(uris: List<Uri>) {
        val albumId = (selectedFilter.value as? VaultFilter.Album)?.albumId
        launchCatching { vaultRepository.importDocuments(uris, albumId) }
    }

    /** Camera/Record Video specifically — unlike [importImages]/[importVideos] above (which land
     * in whatever album filter is currently selected), a captured photo/video always goes to no
     * album regardless of the current filter: taking a photo in the moment isn't the same as
     * having browsed into a specific album first. */
    fun importCapturedImage(uri: Uri) {
        launchCatching { vaultRepository.importImages(listOf(uri), albumId = null) }
    }

    fun importCapturedVideo(uri: Uri) {
        launchCatching { vaultRepository.importVideos(listOf(uri), albumId = null) }
    }

    fun toggleFavorite(item: VaultItem) {
        if (!item.isMine) return
        launchCatching { vaultRepository.setFavorite(item, !item.isFavorite) }
    }

    fun deleteItem(item: VaultItem) {
        if (!item.isMine) return
        launchCatching { vaultRepository.deleteItem(item) }
    }

    fun setCaption(item: VaultItem, caption: String?) {
        if (!item.isMine) return
        launchCatching { vaultRepository.setCaption(item, caption) }
    }

    fun moveToAlbum(item: VaultItem, albumId: String?) {
        if (!item.isMine) return
        launchCatching { vaultRepository.setAlbum(item, albumId) }
    }

    /** Ensures a video/document's bytes are local first (a photo's are always already local) —
     * mirrors `CalendarViewModel.exportEventDocumentAttachment`'s "resolve the download, then act
     * on a copy of the item with that path filled in" shape. */
    fun exportItem(item: VaultItem, destinationUri: Uri) {
        launchCatching {
            val resolved = when (item.kind) {
                MessageAttachmentKind.VIDEO -> item.copy(localVideoFilePath = vaultRepository.downloadVideoIfNeeded(item.id, item))
                MessageAttachmentKind.DOCUMENT -> item.copy(localDocumentFilePath = vaultRepository.downloadDocumentIfNeeded(item.id, item))
                MessageAttachmentKind.IMAGE -> item
            }
            vaultRepository.exportItem(resolved, destinationUri)
        }
    }

    /** Called directly from the viewer dialog's `LaunchedEffect`, not fire-and-forget — the
     * caller needs the resulting local path in hand to hand off to the player. Mirrors
     * `HomeViewModel.ensureVideoDownloaded`. */
    suspend fun ensureVideoDownloaded(item: VaultItem): String? =
        runCatching { vaultRepository.downloadVideoIfNeeded(item.id, item) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** Mirrors [ensureVideoDownloaded], document-flavored — called from `PdfPageViewer`'s own
     * `LaunchedEffect` inside `VaultItemViewerDialog` to show a loading state while the document
     * downloads before it can be rendered. */
    suspend fun ensureDocumentDownloaded(item: VaultItem): String? =
        runCatching { vaultRepository.downloadDocumentIfNeeded(item.id, item) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    fun createAlbum(name: String) {
        launchCatching { vaultRepository.createAlbum(name) }
    }

    fun renameAlbum(album: VaultAlbum, name: String) {
        if (!album.isMine) return
        launchCatching { vaultRepository.renameAlbum(album.id, name) }
    }

    fun deleteAlbum(album: VaultAlbum) {
        if (!album.isMine) return
        if (selectedFilter.value == VaultFilter.Album(album.id)) selectedFilter.value = VaultFilter.All
        launchCatching { vaultRepository.deleteAlbum(album.id) }
    }

    /** Entry point into selection mode — reached via a grid cell's long-press (there's no
     * existing gesture to conflict with here, unlike Messages where long-press already opens the
     * reaction picker), rather than a per-item menu action like `HomeViewModel.enterSelection`. */
    fun enterSelection(itemId: String) {
        _selectedItemIds.value = setOf(itemId)
    }

    fun toggleSelection(itemId: String) {
        _selectedItemIds.update { current ->
            if (itemId in current) current - itemId else current + itemId
        }
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    /** Loops the existing single-item endpoint per selected item — same tolerant "one failure
     * doesn't block the rest" shape as `HomeViewModel.bulkSetStarred`/`bulkSetPinned`, since
     * there's no bulk vault endpoint either. Silently skips a partner-owned item (`!isMine`),
     * mirroring [toggleFavorite]/[deleteItem]/[moveToAlbum]'s own single-item no-op rule, rather
     * than surfacing a per-item error for something the UI already prevents selecting for edit. */
    fun bulkSetFavorite(favorite: Boolean) {
        val targets = uiState.value.selectedItems.filter { it.isMine }
        launchCatching {
            val results = targets.map { item -> runCatching { vaultRepository.setFavorite(item, favorite) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }

    fun bulkMoveToAlbum(albumId: String?) {
        val targets = uiState.value.selectedItems.filter { it.isMine }
        launchCatching {
            val results = targets.map { item -> runCatching { vaultRepository.setAlbum(item, albumId) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }

    fun bulkDelete() {
        val targets = uiState.value.selectedItems.filter { it.isMine }
        launchCatching {
            val results = targets.map { item -> runCatching { vaultRepository.deleteItem(item) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }

    /** Must bracket any launch of a system picker (import or export) — see
     * [ConsoleSessionManager.isExpectingTransientResult]. */
    fun beginPickerLaunch() = sessionManager.beginExpectingTransientResult()

    fun endPickerLaunch() = sessionManager.endExpectingTransientResult()
}
