package com.wwwescape.deviceinfox.console.ui.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the shared-media grid — mirrors [com.wwwescape.deviceinfox.console.ui.home.HomeViewModel]'s
 * export/save-to-vault actions verbatim (same [MessageRepository]/[VaultRepository] calls, same
 * event-flow shape) rather than reaching back into `HomeViewModel` itself, since this screen has
 * its own back-stack-entry scope and no reason to share a `ViewModel` instance with Messages. */
@HiltViewModel
class ConversationMediaViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val vaultRepository: VaultRepository,
    private val sessionManager: ConsoleSessionManager,
) : ViewModel() {

    val mediaMessages: StateFlow<List<ConsoleMessage>> = messageRepository.conversationMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    private val _imageExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageExportedEvent: SharedFlow<Unit> = _imageExportedEvent.asSharedFlow()

    private val _videoExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val videoExportedEvent: SharedFlow<Unit> = _videoExportedEvent.asSharedFlow()

    private val _documentExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val documentExportedEvent: SharedFlow<Unit> = _documentExportedEvent.asSharedFlow()

    /** See `HomeViewModel.vaultSaveEvent`'s doc comment for why this carries a `Boolean`. */
    private val _vaultSaveEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vaultSaveEvent: SharedFlow<Boolean> = _vaultSaveEvent.asSharedFlow()

    /** Same re-entry-guard shape as `HomeViewModel.pendingVaultSaveMessageIds` — closes the same
     * double-tap-duplicates-a-vault-item race for this grid's own Save-to-Vault buttons. */
    private val _pendingVaultSaveMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingVaultSaveMessageIds: StateFlow<Set<String>> = _pendingVaultSaveMessageIds.asStateFlow()

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                _errorEvent.tryEmit((e as? ConsoleApiException)?.detail)
            }
        }
    }

    fun exportImage(attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            messageRepository.exportImage(attachment, destinationUri)
            _imageExportedEvent.tryEmit(Unit)
        }
    }

    /** Mirrors `HomeViewModel.exportVideo` — always ensures the download itself first, since this
     * grid's document/video cells have no local-path state of their own to guard on. */
    fun exportVideo(messageId: String, attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            val localPath = messageRepository.downloadVideoIfNeeded(messageId, attachment)
            messageRepository.exportVideo(attachment.copy(localVideoFilePath = localPath), destinationUri)
            _videoExportedEvent.tryEmit(Unit)
        }
    }

    /** See `HomeViewModel.ensureVideoDownloaded`'s doc comment — same suspend-and-return-path
     * shape, needed by [com.wwwescape.deviceinfox.console.ui.components.VideoPreviewDialog]. */
    suspend fun ensureVideoDownloaded(messageId: String, attachment: MessageAttachment): String? =
        runCatching { messageRepository.downloadVideoIfNeeded(messageId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** Documents are export-only, no viewer (see `DocumentAttachmentRow`'s own doc comment) —
     * tapping a document cell in the grid goes straight through this, same as the Export button
     * on the original message bubble. */
    fun exportDocument(messageId: String, attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            val localPath = messageRepository.downloadDocumentIfNeeded(messageId, attachment)
            messageRepository.exportDocument(attachment.copy(localDocumentFilePath = localPath), destinationUri)
            _documentExportedEvent.tryEmit(Unit)
        }
    }

    fun saveAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        if (messageId in _pendingVaultSaveMessageIds.value) return
        _pendingVaultSaveMessageIds.update { it + messageId }
        viewModelScope.launch {
            runCatching { vaultRepository.saveAttachmentToVault(attachment, originalMessageId = messageId) }
                .onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveMessageIds.update { it - messageId }
        }
    }

    fun saveVideoAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        if (messageId in _pendingVaultSaveMessageIds.value) return
        _pendingVaultSaveMessageIds.update { it + messageId }
        viewModelScope.launch {
            runCatching {
                val localPath = messageRepository.downloadVideoIfNeeded(messageId, attachment)
                vaultRepository.saveAttachmentToVault(attachment.copy(localVideoFilePath = localPath), originalMessageId = messageId)
            }.onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveMessageIds.update { it - messageId }
        }
    }

    /** Must bracket any launch of a system picker (the Export "Save As" dialog) — see
     * [ConsoleSessionManager.isExpectingTransientResult]. */
    fun beginPickerLaunch() = sessionManager.beginExpectingTransientResult()

    fun endPickerLaunch() = sessionManager.endExpectingTransientResult()
}
