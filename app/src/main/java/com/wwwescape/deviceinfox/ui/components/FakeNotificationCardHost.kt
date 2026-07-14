package com.wwwescape.deviceinfox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.data.notifications.fakeNotificationCardStateEntryPoint

/**
 * Stealth-layer echo of the most recent real console push, meant to sit directly above
 * [DashboardHero] — see [com.wwwescape.deviceinfox.data.notifications.FakeNotificationCardState]'s
 * own doc comment for the full reasoning and its close triggers. Renders nothing when there's no
 * card to show (rather than an empty placeholder), so callers can place this unconditionally.
 */
@Composable
fun FakeNotificationCardHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = remember { context.fakeNotificationCardStateEntryPoint() }
    val card by state.current.collectAsState()

    card?.let { current ->
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = current.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = state::dismiss) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
        }
    }
}
