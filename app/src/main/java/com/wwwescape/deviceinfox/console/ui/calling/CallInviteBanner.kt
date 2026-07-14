package com.wwwescape.deviceinfox.console.ui.calling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Same debounce shape as CallRoomScreen's StartRow — Accept/Decline are fire-and-forget WS sends
// with no response to hook a reset off.
private const val INVITE_DEBOUNCE_MILLIS = 1_000L

/** Mounted once, above the entire outer nav graph (`ConsoleNavHost`) — same "one point in the tree
 * above every screen" reasoning `FeatureTourOverlay`/`WhatsNewOverlay` already use there, so an
 * incoming call reaches you regardless of which screen you're actually on. No system notification,
 * no sound — a plain in-app card, per CALLING_PLAN.md's "no ringtone, no external trace" rule (the
 * vibration that accompanies a real incoming call lives client-side too, in Phase 2, tied to the
 * same [CallInviteUiState.IncomingCall] this reads). */
@Composable
fun CallInviteBanner(onNavigateToCallRoom: () -> Unit, modifier: Modifier = Modifier, viewModel: CallInviteViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = uiState != CallInviteUiState.None,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
        modifier = modifier,
    ) {
        when (val state = uiState) {
            is CallInviteUiState.IncomingCall -> IncomingCallCard(
                partnerName = state.partnerName,
                onAccept = { viewModel.accept(); onNavigateToCallRoom() },
                onDecline = viewModel::decline,
            )
            is CallInviteUiState.PartnerInRoom -> PartnerInRoomCard(
                partnerName = state.partnerName,
                onClick = onNavigateToCallRoom,
            )
            CallInviteUiState.None -> Unit
        }
    }
}

@Composable
private fun IncomingCallCard(partnerName: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    var debounced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun debounce(action: () -> Unit) {
        if (debounced) return
        debounced = true
        scope.launch { delay(INVITE_DEBOUNCE_MILLIS); debounced = false }
        action()
    }
    Card(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.console_call_room_invite_incoming, partnerName),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { debounce(onDecline) }, enabled = !debounced) {
                    Text(
                        text = stringResource(R.string.console_call_room_decline_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { debounce(onAccept) }, enabled = !debounced) {
                    Text(stringResource(R.string.console_call_room_accept_action))
                }
            }
        }
    }
}

@Composable
private fun PartnerInRoomCard(partnerName: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.console_call_room_invite_partner_in_room, partnerName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
