package com.wwwescape.deviceinfox.console.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import java.io.File

/** Same full-screen black [Dialog] shell as [ImagePreviewDialog] (close + export in the same
 * top-row position), but swaps pinch-zoom for Media3 playback and adds a loading state: unlike
 * an image, a video's bytes may not be local yet (see [MessageAttachment.localVideoFilePath]'s
 * doc comment) — [ensureDownloaded] is called once, on open, and this shows a spinner until it
 * resolves. Shared between Messages and Calendar, same as [ImagePreviewDialog] — [parentId] is
 * just whatever id [ensureDownloaded]'s caller needs to resolve the download (a message id or an
 * event id), opaque to this composable itself. [onSaveToVault] mirrors [ImagePreviewDialog]'s
 * own nullable pattern — pass `null` to omit that button entirely. Export shows
 * [ExportWarningDialog] first, same as [ImagePreviewDialog]/[DocumentAttachmentRow]; [onExport]'s
 * caller (`HomeViewModel.exportVideo`/`CalendarViewModel.exportEventVideoAttachment`) re-resolves
 * the local path itself before exporting (a quick no-op if [ensureDownloaded] already ran), so
 * this dialog doesn't need to thread its own [localPath] through the callback — the `enabled`
 * guard below is purely a UX nicety, not a correctness requirement. */
@Composable
fun VideoPreviewDialog(
    parentId: String,
    attachment: MessageAttachment,
    onDismiss: () -> Unit,
    onExport: (MessageAttachment, Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    ensureDownloaded: suspend () -> String?,
    onSaveToVault: (() -> Unit)? = null,
    isSaveToVaultPending: Boolean = false,
) {
    var localPath by remember(parentId) { mutableStateOf(attachment.localVideoFilePath) }
    LaunchedEffect(parentId) {
        if (localPath == null) {
            localPath = ensureDownloaded()
        }
    }
    val context = LocalContext.current
    var showExportWarning by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(attachment.mimeType),
    ) { uri ->
        onPickerResult()
        uri?.let { onExport(attachment, it) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val currentLocalPath = localPath
            if (currentLocalPath == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.console_home_loading_label),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                val exoPlayer = remember(currentLocalPath) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(currentLocalPath))))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.console_pin_cancel),
                        tint = Color.White,
                    )
                }
                Row {
                    onSaveToVault?.let { saveToVault ->
                        IconButton(onClick = saveToVault, enabled = currentLocalPath != null && !isSaveToVaultPending) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = { showExportWarning = true }, enabled = currentLocalPath != null) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.console_home_export_video_action),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (showExportWarning) {
        ExportWarningDialog(
            onConfirm = {
                showExportWarning = false
                onPickerLaunch()
                exportLauncher.launch(defaultExportFileName(attachment))
            },
            onDismiss = { showExportWarning = false },
        )
    }
}
