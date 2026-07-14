package com.wwwescape.deviceinfox.console.ui.media

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.ui.components.MediaPagerDialog

/** Opened by tapping the partner's name in the Messages conversation header — a single mixed
 * grid of every image/video/document ever shared in the conversation, newest first, matching
 * Signal's plain-grid take on this rather than WhatsApp's Media/Links/Docs tabs (a deliberate v1
 * scope decision, not a gap — see the TODOS.md writeup this was built from). Tapping an image or
 * video reuses the exact same full-screen [ImagePreviewDialog]/[VideoPreviewDialog] `HomeScreen`
 * itself opens for an in-chat bubble; tapping a document downloads it straight away, matching
 * `DocumentAttachmentRow`'s "download-only, no viewer" convention. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationMediaScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationMediaViewModel = hiltViewModel(),
) {
    val mediaMessages by viewModel.mediaMessages.collectAsStateWithLifecycle()
    var previewMessage by remember { mutableStateOf<ConsoleMessage?>(null) }
    val context = LocalContext.current

    val imageSavedMessage = stringResource(R.string.console_home_image_saved)
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect {
            Toast.makeText(context, imageSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val videoSavedMessage = stringResource(R.string.console_home_video_saved)
    LaunchedEffect(Unit) {
        viewModel.videoSavedEvent.collect {
            Toast.makeText(context, videoSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val documentSavedMessage = stringResource(R.string.console_home_document_saved)
    LaunchedEffect(Unit) {
        viewModel.documentSavedEvent.collect {
            Toast.makeText(context, documentSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val vaultSavedMessage = stringResource(R.string.console_home_saved_to_vault_message)
    val vaultAlreadySavedMessage = stringResource(R.string.console_home_already_saved_to_vault_message)
    LaunchedEffect(Unit) {
        viewModel.vaultSaveEvent.collect { saved ->
            Toast.makeText(context, if (saved) vaultSavedMessage else vaultAlreadySavedMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val networkErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: networkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_home_media_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (mediaMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.console_home_media_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(mediaMessages, key = { it.id }) { message ->
                    val attachment = message.attachment ?: return@items
                    MediaGridCell(
                        attachment = attachment,
                        onClick = {
                            if (attachment.kind == MessageAttachmentKind.DOCUMENT) {
                                viewModel.saveDocument(message.id, attachment)
                            } else {
                                previewMessage = message
                            }
                        },
                    )
                }
            }
        }
    }

    previewMessage?.let { message ->
        MediaPagerDialog(
            messages = mediaMessages,
            initialMessageId = message.id,
            onDismiss = { previewMessage = null },
            onDownloadImage = { attachment -> viewModel.downloadImage(attachment) },
            onDownloadVideo = { attachment -> viewModel.downloadVideo(attachment) },
            ensureVideoDownloaded = { messageId, attachment -> viewModel.ensureVideoDownloaded(messageId, attachment) },
            onSaveToVault = { messageId, attachment ->
                if (attachment.kind == MessageAttachmentKind.VIDEO) {
                    viewModel.saveVideoAttachmentToVault(messageId, attachment)
                } else {
                    viewModel.saveAttachmentToVault(messageId, attachment)
                }
            },
        )
    }
}

@Composable
private fun MediaGridCell(attachment: MessageAttachment, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        if (attachment.kind == MessageAttachmentKind.DOCUMENT) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    attachment.originalFilename?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            AsyncImage(
                model = attachment.filePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (attachment.kind == MessageAttachmentKind.VIDEO) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
        }
    }
}
