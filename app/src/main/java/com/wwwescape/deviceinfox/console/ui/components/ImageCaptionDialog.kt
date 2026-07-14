package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

/** A preview-and-caption confirmation for sending one image as a message: the picture, an editable
 * caption field pre-filled with [initialCaption], and Cancel/Send. Deliberately generic (image +
 * caption in, caption out) so the future photo/video attachment flows can reuse it as their own
 * preview/caption step — the Calendar Stats chart export is its first user.
 *
 * While [isSending] the caption and both buttons are disabled and Send shows a spinner, and the
 * dialog can't be dismissed by tapping outside or pressing Back — the caller dismisses it once the
 * send resolves (same "stay up until it really resolves" shape the Calendar event editor uses). */
@Composable
fun ImageCaptionDialog(
    image: ImageBitmap,
    initialCaption: String,
    title: String,
    isSending: Boolean,
    onSend: (caption: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var caption by remember { mutableStateOf(initialCaption) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(stringResource(R.string.console_image_caption_label)) },
                    enabled = !isSending,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(caption) }, enabled = !isSending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSending) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.console_image_caption_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text(stringResource(R.string.console_image_caption_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
