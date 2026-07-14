package com.wwwescape.deviceinfox.console.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the shared-media grid — mirrors [com.wwwescape.deviceinfox.console.ui.home.HomeViewModel]'s
 * download/save-to-vault actions verbatim (same [MessageRepository]/[VaultRepository] calls, same
 * event-flow shape) rather than reaching back into `HomeViewModel` itself, since this screen has
 * its own back-stack-entry scope and no reason to share a `ViewModel` instance with Messages. */
@HiltViewModel
class ConversationMediaViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val mediaMessages: StateFlow<List<ConsoleMessage>> = messageRepository.conversationMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    private val _imageSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageSavedEvent: SharedFlow<Unit> = _imageSavedEvent.asSharedFlow()

    private val _videoSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val videoSavedEvent: SharedFlow<Unit> = _videoSavedEvent.asSharedFlow()

    private val _documentSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val documentSavedEvent: SharedFlow<Unit> = _documentSavedEvent.asSharedFlow()

    /** See `HomeViewModel.vaultSaveEvent`'s doc comment for why this carries a `Boolean`. */
    private val _vaultSaveEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vaultSaveEvent: SharedFlow<Boolean> = _vaultSaveEvent.asSharedFlow()

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                _errorEvent.tryEmit((e as? ConsoleApiException)?.detail)
            }
        }
    }

    fun downloadImage(attachment: MessageAttachment) {
        viewModelScope.launch {
            messageRepository.saveImageToPrivateDownloads(attachment)
            _imageSavedEvent.tryEmit(Unit)
        }
    }

    fun downloadVideo(attachment: MessageAttachment) {
        launchCatching {
            messageRepository.saveVideoToPrivateDownloads(attachment)
            _videoSavedEvent.tryEmit(Unit)
        }
    }

    /** See `HomeViewModel.ensureVideoDownloaded`'s doc comment — same suspend-and-return-path
     * shape, needed by [com.wwwescape.deviceinfox.console.ui.components.VideoPreviewDialog]. */
    suspend fun ensureVideoDownloaded(messageId: String, attachment: MessageAttachment): String? =
        runCatching { messageRepository.downloadVideoIfNeeded(messageId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** Documents are download-only, no viewer (see `DocumentAttachmentRow`'s own doc comment) —
     * tapping a document cell in the grid goes straight through this, same as the Save button
     * on the original message bubble. */
    fun saveDocument(messageId: String, attachment: MessageAttachment) {
        launchCatching {
            val localPath = messageRepository.downloadDocumentIfNeeded(messageId, attachment)
            messageRepository.saveDocumentToPrivateDownloads(attachment.copy(localDocumentFilePath = localPath))
            _documentSavedEvent.tryEmit(Unit)
        }
    }

    fun saveAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        launchCatching {
            val saved = vaultRepository.saveAttachmentToVault(attachment, originalMessageId = messageId)
            _vaultSaveEvent.tryEmit(saved)
        }
    }

    fun saveVideoAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        launchCatching {
            val localPath = messageRepository.downloadVideoIfNeeded(messageId, attachment)
            val saved = vaultRepository.saveAttachmentToVault(attachment.copy(localVideoFilePath = localPath), originalMessageId = messageId)
            _vaultSaveEvent.tryEmit(saved)
        }
    }
}
