package com.wwwescape.deviceinfox.console.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.vault.VaultAlbum

@Composable
fun AlbumManagementDialog(
    albums: List<VaultAlbum>,
    onNewAlbum: () -> Unit,
    onRenameAlbum: (VaultAlbum) -> Unit,
    onDeleteAlbum: (VaultAlbum) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_vault_manage_albums_title)) },
        text = {
            Column {
                if (albums.isEmpty()) {
                    Text(
                        text = stringResource(R.string.console_vault_no_albums_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                albums.forEach { album ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(album.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (album.isMine) {
                            IconButton(onClick = { onRenameAlbum(album) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.console_vault_rename_album_title))
                            }
                            IconButton(onClick = { onDeleteAlbum(album) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.console_calendar_delete_action))
                            }
                        }
                    }
                }
                TextButton(onClick = onNewAlbum, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.console_vault_new_album_title))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_continue)) }
        },
        modifier = modifier,
    )
}
