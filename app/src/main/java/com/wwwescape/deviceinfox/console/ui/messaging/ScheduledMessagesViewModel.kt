package com.wwwescape.deviceinfox.console.ui.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ScheduledMessage
import com.wwwescape.deviceinfox.console.data.messaging.ScheduledMessageRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
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

data class ScheduledMessagesUiState(
    val entries: List<ScheduledMessage> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/** Backs the Settings → Scheduled Messages list — [entries] is already sorted soonest-to-send
 * first by [ScheduledMessageRepository]'s own Room query, so this ViewModel never re-sorts it.
 * Selection mode mirrors [com.wwwescape.deviceinfox.console.ui.notepad.NotepadViewModel]'s own
 * shape (a derived, non-empty `Set` rather than a separate flag). */
@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val scheduledMessageRepository: ScheduledMessageRepository,
) : ViewModel() {
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    val uiState: StateFlow<ScheduledMessagesUiState> = combine(
        scheduledMessageRepository.scheduledMessages,
        _selectedIds,
    ) { entries, selected -> ScheduledMessagesUiState(entries, selected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduledMessagesUiState())

    /** Fires the message right away instead of waiting for its scheduled time — moves the send
     * *earlier* only, never edits its content (there's still no in-place edit for a pending
     * message, per the design this was built from). */
    fun sendNow(entry: ScheduledMessage) {
        viewModelScope.launch {
            runCatching { scheduledMessageRepository.sendNow(entry.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    fun delete(entry: ScheduledMessage) {
        viewModelScope.launch {
            runCatching { scheduledMessageRepository.cancel(entry.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    fun enterSelection(id: String) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { current -> if (id in current) current - id else current + id }
    }

    fun selectAll() {
        _selectedIds.value = uiState.value.entries.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Bulk delete only — deliberately no bulk "Send Now" (per the design this was built from):
     * firing several queued sends at once doesn't come up the way clearing out several unwanted
     * ones does. Loops the existing single-entry endpoint per selected message, same tolerant
     * "one failure doesn't block the rest" shape as `NotepadViewModel.bulkDelete`/
     * `VaultViewModel.bulkDelete`. */
    fun bulkDelete() {
        val targets = uiState.value.selectedIds
        viewModelScope.launch {
            val results = targets.map { id -> runCatching { scheduledMessageRepository.cancel(id) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }
}
