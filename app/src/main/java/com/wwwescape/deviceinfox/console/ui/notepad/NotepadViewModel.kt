package com.wwwescape.deviceinfox.console.ui.notepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.db.NotepadEntryType
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry
import com.wwwescape.deviceinfox.console.data.notepad.NotepadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NotepadViewModel @Inject constructor(
    private val notepadRepository: NotepadRepository,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(NotepadTab.PRIVATE)

    /** See `VaultViewModel._selectedItemIds`'s identical reasoning — selection mode is derived
     * (non-empty set), not a separate flag. */
    private val _selectedEntryIds = MutableStateFlow<Set<String>>(emptySet())

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    /** Fired once `createNote()`'s network round-trip resolves — the list screen collects this to
     * navigate straight into the new note's editor, since the FAB has no id to navigate to until
     * the server actually assigns one. */
    private val _createdEntryId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val createdEntryId: SharedFlow<String> = _createdEntryId.asSharedFlow()

    val uiState: StateFlow<NotepadUiState> = combine(
        notepadRepository.privateEntries,
        notepadRepository.sharedEntries,
        selectedTab,
        _selectedEntryIds,
    ) { privateEntries, sharedEntries, tab, selected ->
        NotepadUiState(privateEntries, sharedEntries, tab, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotepadUiState())

    fun selectTab(tab: NotepadTab) {
        selectedTab.value = tab
        clearSelection()
    }

    fun createNote() {
        val type = if (selectedTab.value == NotepadTab.PRIVATE) NotepadEntryType.PRIVATE else NotepadEntryType.SHARED
        viewModelScope.launch {
            runCatching { notepadRepository.createEntry(type) }
                .onSuccess { entry -> _createdEntryId.tryEmit(entry.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    fun duplicate(entry: NotepadEntry) {
        viewModelScope.launch {
            runCatching { notepadRepository.duplicateEntry(entry.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    fun delete(entry: NotepadEntry) {
        viewModelScope.launch {
            runCatching { notepadRepository.deleteEntry(entry.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    /** Entry point into selection mode — reached via a row's long-press, same shape
     * `VaultViewModel.enterSelection` uses. */
    fun enterSelection(entryId: String) {
        _selectedEntryIds.value = setOf(entryId)
    }

    fun toggleSelection(entryId: String) {
        _selectedEntryIds.update { current -> if (entryId in current) current - entryId else current + entryId }
    }

    fun selectAll() {
        _selectedEntryIds.value = uiState.value.visibleEntries.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedEntryIds.value = emptySet()
    }

    /** Loops the existing single-entry endpoint per selected note — same tolerant "one failure
     * doesn't block the rest" shape as `VaultViewModel.bulkDelete`, since there's no bulk notepad
     * endpoint either. */
    fun bulkDelete() {
        val targets = uiState.value.selectedEntryIds
        viewModelScope.launch {
            val results = targets.map { id -> runCatching { notepadRepository.deleteEntry(id) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }
}
