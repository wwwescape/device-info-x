package com.wwwescape.deviceinfox.console.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageJumpRequester
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Which of the two nearly-identical read-only message lists a given `StarredPinnedMessagesScreen`
 * instance renders — see that screen's own doc comment for why this is one parameterized
 * screen/ViewModel rather than two separate ones. */
enum class StarredPinnedMode { STARRED, PINNED }

data class StarredPinnedUiState(
    val starredMessages: List<ConsoleMessage> = emptyList(),
    val pinnedMessages: List<ConsoleMessage> = emptyList(),
    val partnerDisplayName: String? = null,
) {
    fun messagesFor(mode: StarredPinnedMode): List<ConsoleMessage> = when (mode) {
        StarredPinnedMode.STARRED -> starredMessages
        StarredPinnedMode.PINNED -> pinnedMessages
    }
}

/** Backs both the Starred and Pinned messages screens — one `ViewModel` observing both
 * repository flows rather than two separate ones parameterized by [StarredPinnedMode] via
 * assisted injection, since a boolean-filtered Room query for the list this doesn't currently
 * display is negligible overhead next to avoiding that machinery entirely. */
@HiltViewModel
class StarredPinnedMessagesViewModel @Inject constructor(
    messageRepository: MessageRepository,
    pairingRepository: PairingRepository,
    private val messageJumpRequester: MessageJumpRequester,
) : ViewModel() {
    val uiState: StateFlow<StarredPinnedUiState> = combine(
        messageRepository.starredMessages,
        messageRepository.pinnedMessages,
        pairingRepository.pairingStatus,
    ) { starred, pinned, pairingStatus ->
        StarredPinnedUiState(
            starredMessages = starred,
            pinnedMessages = pinned,
            partnerDisplayName = (pairingStatus as? PairingStatus.Paired)?.partnerDisplayName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StarredPinnedUiState())

    /** Tapping a row — relayed to `HomeViewModel` via [MessageJumpRequester], see its own doc
     * comment for why this indirection exists. The caller pops back to Messages itself right
     * after calling this; this class has no navigation concerns of its own. */
    fun jumpTo(messageId: String) {
        messageJumpRequester.requestJumpTo(messageId)
    }
}
