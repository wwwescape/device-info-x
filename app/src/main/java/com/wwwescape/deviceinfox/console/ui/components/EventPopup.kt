package com.wwwescape.deviceinfox.console.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

/**
 * Shared "greeting" popup — a title, an icon in a themed circle, a main message, and an optional
 * second line underneath. Used for both the birthday popup and the national/catholic/hindu
 * holiday-event popup ([com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsScreen]), which are
 * otherwise the same shape (title/icon/message), differing only in which icon and text they pass
 * in — birthday's [secondaryMessage] is the account holder's own optional custom note from
 * Settings, holiday events never pass one.
 *
 * A plain [Image], not an [androidx.compose.material3.Icon] — the four PNGs this renders
 * (`birthday`/`national`/`catholic`/`hindu` under `res/drawable/`) are full-color illustrations,
 * not tintable monochrome vectors, and `Icon` would force-tint them to a single color via
 * `LocalContentColor`.
 */
@Composable
fun EventPopup(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    secondaryMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.console_event_popup_dismiss))
            }
        },
        title = {
            Text(text = title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                if (!secondaryMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = secondaryMessage,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
