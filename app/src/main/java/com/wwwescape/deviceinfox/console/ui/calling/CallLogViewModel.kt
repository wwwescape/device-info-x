package com.wwwescape.deviceinfox.console.ui.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.calling.CallLogEntry
import com.wwwescape.deviceinfox.console.data.calling.CallLogRepository
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CallLogUiState {
    data object Loading : CallLogUiState
    data class Content(val entries: List<CallLogEntry>) : CallLogUiState
    data object Error : CallLogUiState
}

/** Backs the Call Room's Log tab — a plain one-shot fetch per [refresh] call (see
 * [CallLogRepository]'s own doc comment for why this doesn't push live over the WebSocket the way
 * [CallRoomViewModel]'s room state does); the screen calls [refresh] every time the tab is
 * (re)entered, which is enough to pick up a call that just resolved without needing a live stream. */
@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val repository: CallLogRepository,
    private val partnerRepository: PartnerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CallLogUiState>(CallLogUiState.Loading)
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CallLogUiState.Loading
            val selfId = partnerRepository.selfProfile.first()?.id
            if (selfId == null) {
                _uiState.value = CallLogUiState.Error
                return@launch
            }
            _uiState.value = runCatching { repository.fetchLog(selfId) }
                .fold(
                    onSuccess = { CallLogUiState.Content(it) },
                    onFailure = { CallLogUiState.Error },
                )
        }
    }
}
