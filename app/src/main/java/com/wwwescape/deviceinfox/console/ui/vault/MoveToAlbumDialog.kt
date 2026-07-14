package com.wwwescape.deviceinfox.console.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.vault.VaultAlbum

@Composable
fun MoveToAlbumDialog(
    albums: List<VaultAlbum>,
    currentAlbumId: String?,
    isPending: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_vault_move_to_album_title)) },
        text = {
            Column {
                MoveToAlbumOption(
                    label = stringResource(R.string.console_vault_no_album_label),
                    selected = currentAlbumId == null,
                    enabled = !isPending,
                    onClick = { onSelect(null) },
                )
                albums.forEach { album ->
                    MoveToAlbumOption(
                        label = album.name,
                        selected = currentAlbumId == album.id,
                        enabled = !isPending,
                        onClick = { onSelect(album.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}

@Composable
private fun MoveToAlbumOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
