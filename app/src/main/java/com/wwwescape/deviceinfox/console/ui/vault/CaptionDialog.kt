package com.wwwescape.deviceinfox.console.ui.vault

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R

@Composable
fun CaptionDialog(
    existingCaption: String?,
    isPending: Boolean,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var caption by rememberSaveable { mutableStateOf(existingCaption.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_vault_caption_title)) },
        text = {
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text(stringResource(R.string.console_vault_caption_label)) },
                enabled = !isPending,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = { onSave(caption.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.console_calendar_save_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
