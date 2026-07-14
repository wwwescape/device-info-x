package com.wwwescape.deviceinfox.console.ui.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.calling.CallRoomRepository
import com.wwwescape.deviceinfox.console.data.calling.CallStatus
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Two distinct signals (CALLING_PLAN.md §5), both driven by the same [CallRoomRepository.roomState]
 * every screen using the Call Room already observes — nothing new to keep in sync. [None] covers
 * both "nothing's happening" and "I'm already on the Call Room screen myself," so this banner
 * never doubles up with what that screen is already showing. */
sealed interface CallInviteUiState {
    data object None : CallInviteUiState

    /** A real ring — this device is the callee and hasn't answered yet. High-urgency, interrupts. */
    data class IncomingCall(val partnerName: String) : CallInviteUiState

    /** Purely informational — the partner is in the Call Room, nothing is ringing. Low-urgency. */
    data class PartnerInRoom(val partnerName: String) : CallInviteUiState
}

@HiltViewModel
class CallInviteViewModel @Inject constructor(
    private val repository: CallRoomRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    val uiState: StateFlow<CallInviteUiState> = combine(
        repository.roomState,
        partnerRepository.selfProfile,
        partnerRepository.partnerProfile,
    ) { state, self, partner ->
        val myId = self?.id
        val partnerId = partner?.id
        val partnerName = partner?.displayName
        val selfOnCallRoomScreen = myId != null && state.present[myId] == true
        when {
            partnerName == null || selfOnCallRoomScreen -> CallInviteUiState.None
            state.status == CallStatus.RINGING && state.calleeId == myId -> CallInviteUiState.IncomingCall(partnerName)
            partnerId != null && state.present[partnerId] == true -> CallInviteUiState.PartnerInRoom(partnerName)
            else -> CallInviteUiState.None
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallInviteUiState.None)

    /** Fires `call.accept` immediately — the caller has navigated into the Call Room screen too,
     * which itself sends `screen.call_room_enter` on composition, so no separate "join the room"
     * call is needed here. Not gated on that navigation completing first (CALLING_PLAN.md §3). */
    fun accept() = repository.acceptCall()
    fun decline() = repository.declineCall()
}
