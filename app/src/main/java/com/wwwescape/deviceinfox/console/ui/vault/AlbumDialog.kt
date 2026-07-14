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

/** Doubles as both "new album" (null [existingName]) and "rename album" forms. */
@Composable
fun AlbumDialog(
    existingName: String?,
    isPending: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(existingName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existingName != null) R.string.console_vault_rename_album_title else R.string.console_vault_new_album_title,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.console_vault_album_name_label)) },
                enabled = !isPending,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !isPending,
                onClick = { onSave(name.trim()) },
            ) { Text(stringResource(R.string.console_calendar_save_action)) }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
