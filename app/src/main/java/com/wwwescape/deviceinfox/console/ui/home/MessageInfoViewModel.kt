package com.wwwescape.deviceinfox.console.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Backs the "Message info" screen (the 3-dot menu's Info option, own messages only) — a single
 * message looked up by id out of the same live [MessageRepository.conversation] flow every other
 * screen already observes, rather than a dedicated one-off fetch. That means this screen updates
 * live too: if the partner reads the message while this screen happens to still be open, the
 * Read row appears without needing to back out and reopen it. */
@HiltViewModel
class MessageInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageRepository: MessageRepository,
) : ViewModel() {
    private val messageId: String = checkNotNull(savedStateHandle["messageId"])

    val message: StateFlow<ConsoleMessage?> = messageRepository.conversation
        .map { messages -> messages.firstOrNull { it.id == messageId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
