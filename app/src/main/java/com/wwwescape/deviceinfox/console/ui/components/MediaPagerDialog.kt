package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind

/** Full-screen swipeable media viewer shared by Messages (`HomeScreen`'s in-chat image/video tap)
 * and the Shared Media grid (`ConversationMediaScreen`) — swiping left/right at 1x zoom moves to
 * the next/previous image or video in [messages], whichever order the caller already has them in
 * (chat order for Messages, newest-first for Shared Media); pinching still zooms an image without
 * leaving its page, per [ZoomableImage]'s own doc comment. Documents are excluded from the page
 * list entirely — they're download-only, no viewer, matching `DocumentAttachmentRow`'s existing
 * convention, so a document is never what opens this dialog to begin with. */
@Composable
fun MediaPagerDialog(
    messages: List<ConsoleMessage>,
    initialMessageId: String,
    onDismiss: () -> Unit,
    onDownloadImage: (MessageAttachment) -> Unit,
    onDownloadVideo: (MessageAttachment) -> Unit,
    ensureVideoDownloaded: suspend (String, MessageAttachment) -> String?,
    onSaveToVault: ((String, MessageAttachment) -> Unit)? = null,
) {
    val pageMessages = remember(messages) {
        messages.filter { it.attachment != null && it.attachment.kind != MessageAttachmentKind.DOCUMENT }
    }
    if (pageMessages.isEmpty()) {
        onDismiss()
        return
    }
    val initialPage = remember(pageMessages, initialMessageId) {
        pageMessages.indexOfFirst { it.id == initialMessageId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pageMessages.size }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    val currentMessage = pageMessages[pagerState.currentPage.coerceIn(pageMessages.indices)]
    val currentAttachment = currentMessage.attachment ?: return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = zoomedPages[pagerState.currentPage] != true,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageMessage = pageMessages[page]
                val attachment = pageMessage.attachment ?: return@HorizontalPager
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (attachment.kind == MessageAttachmentKind.VIDEO) {
                        VideoPlayerPage(
                            parentId = pageMessage.id,
                            initialLocalPath = attachment.localVideoFilePath,
                            ensureDownloaded = { ensureVideoDownloaded(pageMessage.id, attachment) },
                        )
                    } else {
                        ZoomableImage(
                            model = attachment.filePath,
                            contentDescription = null,
                            onZoomedChanged = { zoomed -> zoomedPages[page] = zoomed },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel), tint = Color.White)
                }
                Row {
                    onSaveToVault?.let { save ->
                        IconButton(onClick = { save(currentMessage.id, currentAttachment) }) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (currentAttachment.kind == MessageAttachmentKind.VIDEO) {
                                onDownloadVideo(currentAttachment)
                            } else {
                                onDownloadImage(currentAttachment)
                            }
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(
                                if (currentAttachment.kind == MessageAttachmentKind.VIDEO) {
                                    R.string.console_home_download_video_action
                                } else {
                                    R.string.console_home_download_image_action
                                },
                            ),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
