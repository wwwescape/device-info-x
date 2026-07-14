package com.wwwescape.deviceinfox.console.ui.calling

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calling.CallLogEntry
import com.wwwescape.deviceinfox.console.data.calling.CallLogStatus
import com.wwwescape.deviceinfox.console.data.calling.CallType
import com.wwwescape.deviceinfox.console.ui.components.PresenceAvatar
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceIcon
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceTier
import com.wwwescape.deviceinfox.console.ui.components.screenPresenceTierColor
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val START_DEBOUNCE_MILLIS = 1_000L
private const val END_BANNER_MILLIS = 3_000L

private enum class CallRoomTab { CALL, LOG }

/** The Call Room — Messages header's call icon (no menu, this is the only entry point, see
 * CALLING_PLAN.md §6). Deliberately calm rather than eventful: no confetti/fireworks the way Game
 * Room's win banner has, muted `AnimatedContent` crossfades between states, and the same
 * name + presence-dot status-row language the rest of this app's presence UI already uses.
 * `RECORD_AUDIO` (and `CAMERA` for a video call) are requested contextually right here, on the
 * first real Start/Accept tap, rather than at install — consistent with how sensitive permissions
 * elsewhere in this app are already requested lazily. A video call's Active state replaces the
 * usual centered-column layout with a full-bleed remote video + a local preview thumbnail;
 * everything else (waiting/Start/ringing/voice-active) keeps the plain centered layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRoomScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CallRoomViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerDisplayName by viewModel.partnerDisplayName.collectAsStateWithLifecycle()
    val partnerTier by viewModel.partnerPresenceTier.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraOn.collectAsStateWithLifecycle()
    val localVideoTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val isReconnecting by viewModel.isReconnecting.collectAsStateWithLifecycle()
    val partnerName = partnerDisplayName ?: stringResource(R.string.console_home_title_fallback)

    var selectedTab by remember { mutableStateOf(CallRoomTab.CALL) }
    // A ringing/active call takes over regardless of which tab was open — browsing the Log tab
    // must never cause a missed incoming call to go unnoticed.
    LaunchedEffect(uiState) {
        if (uiState is CallRoomUiState.RingingOutgoing || uiState is CallRoomUiState.RingingIncoming || uiState is CallRoomUiState.Active) {
            selectedTab = CallRoomTab.CALL
        }
    }

    // Announces presence on entry, withdraws it on exit — same DisposableEffect(Unit) shape every
    // other shared-room screen in this app uses (Game Room, Messages' own "Here" tier); Compose
    // entering/leaving composition is the real "opened/closed this screen" signal, not any
    // ViewModel lifecycle callback. Leaving also tears down any live media session — see
    // CallRoomViewModel's own uiState collector, which reacts to the resulting idle state.
    DisposableEffect(Unit) {
        viewModel.enterRoom()
        onDispose { viewModel.leaveRoom() }
    }

    // Video calls only — voice calls rely on the proximity carve-out instead (CALLING_PLAN.md
    // §8.1: no proximity exception for video, the screen should just stay on for the duration).
    val view = LocalView.current
    val isVideoActive = uiState.let { it is CallRoomUiState.Active && it.callType == CallType.VIDEO }
    DisposableEffect(isVideoActive) {
        view.keepScreenOn = isVideoActive
        onDispose { view.keepScreenOn = false }
    }

    var banner by remember { mutableStateOf<CallEndBanner?>(null) }
    LaunchedEffect(Unit) {
        viewModel.callEndedBanner.collect { reason ->
            banner = reason
            delay(END_BANNER_MILLIS)
            banner = null
        }
    }

    val context = LocalContext.current
    var pendingMediaAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) pendingMediaAction?.invoke()
        pendingMediaAction = null
    }
    fun withMediaPermissions(callType: CallType, action: () -> Unit) {
        val required = if (callType == CallType.VIDEO) {
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingMediaAction = action
            mediaPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Tabs only make sense between calls — once one is ringing/active it takes the whole screen
    // (and a video call needs the full-bleed layout below, with no room for a TabRow anyway).
    val showTabs = uiState !is CallRoomUiState.RingingOutgoing &&
        uiState !is CallRoomUiState.RingingIncoming &&
        uiState !is CallRoomUiState.Active

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_call_room_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
                if (showTabs) {
                    TabRow(selectedTabIndex = if (selectedTab == CallRoomTab.CALL) 0 else 1) {
                        Tab(
                            selected = selectedTab == CallRoomTab.CALL,
                            onClick = { selectedTab = CallRoomTab.CALL },
                            text = { Text(stringResource(R.string.console_call_room_tab_call)) },
                        )
                        Tab(
                            selected = selectedTab == CallRoomTab.LOG,
                            onClick = { selectedTab = CallRoomTab.LOG },
                            text = { Text(stringResource(R.string.console_call_room_tab_log)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (selectedTab == CallRoomTab.LOG) {
            CallLogTab(modifier = Modifier.padding(innerPadding))
        } else if (isVideoActive) {
            // Full-bleed video, not the centered-column treatment every other state uses below —
            // the partner status row and any transient banner overlay on top instead of pushing
            // the video around.
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                VideoRendererView(
                    eglBaseContext = viewModel.eglBaseContext,
                    track = remoteVideoTrack,
                    mirror = false,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isCameraOn) {
                    VideoRendererView(
                        eglBaseContext = viewModel.eglBaseContext,
                        track = localVideoTrack,
                        mirror = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(100.dp, 140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp)) {
                    PartnerStatusRow(name = partnerName, tier = partnerTier)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val activeState = uiState as? CallRoomUiState.Active
                    if (activeState != null) {
                        if (isReconnecting) {
                            Text(
                                text = stringResource(R.string.console_call_room_reconnecting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        ActiveCallControls(
                            isMuted = isMuted,
                            onToggleMute = viewModel::toggleMute,
                            onEnd = viewModel::endCall,
                            isSpeakerOn = isSpeakerOn,
                            onToggleSpeaker = viewModel::toggleSpeaker,
                            isCameraOn = isCameraOn,
                            onToggleCamera = viewModel::toggleCamera,
                            onSwitchCamera = viewModel::switchCamera,
                            showVideoControls = true,
                        )
                    }
                    banner?.let { reason ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = bannerText(reason, partnerName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                PartnerStatusColumn(name = partnerName, tier = partnerTier)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AnimatedContent(
                            targetState = uiState,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "call_room_state",
                        ) { state ->
                            CallRoomStateContent(
                                state = state,
                                onStartCall = { type -> withMediaPermissions(type) { viewModel.startCall(type) } },
                                onCancel = viewModel::cancelCall,
                                onAccept = {
                                    val callType = (state as? CallRoomUiState.RingingIncoming)?.callType ?: CallType.VOICE
                                    withMediaPermissions(callType, viewModel::acceptCall)
                                },
                                onDecline = viewModel::declineCall,
                                onEnd = viewModel::endCall,
                                isMuted = isMuted,
                                onToggleMute = viewModel::toggleMute,
                                isSpeakerOn = isSpeakerOn,
                                onToggleSpeaker = viewModel::toggleSpeaker,
                                isReconnecting = isReconnecting,
                            )
                        }

                        banner?.let { reason ->
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = bannerText(reason, partnerName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRendererView(eglBaseContext: EglBase.Context, track: VideoTrack?, mirror: Boolean, modifier: Modifier = Modifier) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                rendererRef = this
            }
        },
        onRelease = { renderer ->
            runCatching { renderer.release() }
            if (rendererRef === renderer) rendererRef = null
        },
    )

    DisposableEffect(track, rendererRef) {
        val renderer = rendererRef
        renderer?.setMirror(mirror)
        if (renderer != null && track != null) runCatching { track.addSink(renderer) }
        onDispose {
            if (renderer != null && track != null) runCatching { track.removeSink(renderer) }
        }
    }
}

/** Compact horizontal treatment — the video-call overlay's only remaining caller, top-left over
 * live video. Deliberately stays small/horizontal rather than matching [PartnerStatusColumn]'s
 * bigger vertical stack below, to avoid covering more of the feed than necessary. The badge's own
 * backdrop is a dark cutout rather than the shared [PresenceAvatar] default (light `surface`),
 * which would otherwise look like a stray light patch over the video. */
@Composable
private fun PartnerStatusRow(name: String, tier: ScreenPresenceTier) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresenceAvatar(
            name = name,
            isSelf = false,
            size = 32.dp,
            badgeSize = 14.dp,
            badgeBackgroundColor = Color.Black.copy(alpha = 0.7f),
            presenceBadge = {
                ScreenPresenceIcon(tier = tier, contentDescription = null, modifier = Modifier.size(10.dp))
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(
                text = presenceTierLabel(tier),
                style = MaterialTheme.typography.labelSmall,
                color = screenPresenceTierColor(tier),
            )
        }
    }
}

/** The idle/ringing/active (non-video) states' partner display — a vertical avatar/name/status
 * stack via the shared [PresenceAvatar], matching Game Room's own `PlayerAvatarColumn` layout
 * exactly (see TODOS.md), rather than this row's old horizontal treatment. Partner-only, by
 * design — unlike Game Room/Live Location, self's own presence isn't a meaningful second thing to
 * show on this screen (you're definitionally "here" while looking at it). */
@Composable
private fun PartnerStatusColumn(name: String, tier: ScreenPresenceTier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PresenceAvatar(
            name = name,
            isSelf = false,
            presenceBadge = {
                ScreenPresenceIcon(tier = tier, contentDescription = null, modifier = Modifier.size(14.dp))
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = presenceTierLabel(tier),
            style = MaterialTheme.typography.labelSmall,
            color = screenPresenceTierColor(tier),
        )
    }
}

@Composable
private fun presenceTierLabel(tier: ScreenPresenceTier): String = when (tier) {
    ScreenPresenceTier.ON_SCREEN -> stringResource(R.string.console_call_room_partner_on_screen)
    ScreenPresenceTier.IN_APP -> stringResource(R.string.console_call_room_partner_in_app)
    ScreenPresenceTier.NOT_IN_APP -> stringResource(R.string.console_call_room_partner_not_in_app)
}

@Composable
private fun CallRoomStateContent(
    state: CallRoomUiState,
    onStartCall: (CallType) -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    isReconnecting: Boolean,
) {
    when (state) {
        CallRoomUiState.Loading -> Unit
        CallRoomUiState.PartnerUnreachable -> Text(
            text = stringResource(R.string.console_call_room_waiting_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        CallRoomUiState.StartAvailable -> StartRow(onStartCall)
        is CallRoomUiState.RingingOutgoing -> RingingOutgoingContent(state.callType, onCancel)
        is CallRoomUiState.RingingIncoming -> RingingIncomingContent(state.callType, onAccept, onDecline)
        is CallRoomUiState.Active -> ActiveCallContent(state, onEnd, isMuted, onToggleMute, isSpeakerOn, onToggleSpeaker, isReconnecting)
    }
}

@Composable
private fun StartRow(onStartCall: (CallType) -> Unit) {
    var debounced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun tap(type: CallType) {
        if (debounced) return
        debounced = true
        scope.launch { delay(START_DEBOUNCE_MILLIS); debounced = false }
        onStartCall(type)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Button(onClick = { tap(CallType.VOICE) }, enabled = !debounced) {
            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.console_call_room_voice_action))
        }
        Button(onClick = { tap(CallType.VIDEO) }, enabled = !debounced) {
            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.console_call_room_video_action))
        }
    }
}

@Composable
private fun RingingOutgoingContent(callType: CallType, onCancel: () -> Unit) {
    // Same debounce shape as StartRow — Cancel is another fire-and-forget WS send with no
    // response to hook a reset off.
    var debounced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    CallTypeIcon(callType)
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.console_call_room_ringing_outgoing), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = {
            if (debounced) return@OutlinedButton
            debounced = true
            scope.launch { delay(START_DEBOUNCE_MILLIS); debounced = false }
            onCancel()
        },
        enabled = !debounced,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(R.string.console_call_room_cancel_action))
    }
}

@Composable
private fun RingingIncomingContent(callType: CallType, onAccept: () -> Unit, onDecline: () -> Unit) {
    // Same debounce shape as StartRow — Accept/Decline are also fire-and-forget WS sends.
    var debounced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun debounce(action: () -> Unit) {
        if (debounced) return
        debounced = true
        scope.launch { delay(START_DEBOUNCE_MILLIS); debounced = false }
        action()
    }
    CallTypeIcon(callType)
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = stringResource(R.string.console_call_room_ringing_incoming), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedButton(
            onClick = { debounce(onDecline) },
            enabled = !debounced,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.console_call_room_decline_action))
        }
        Button(onClick = { debounce(onAccept) }, enabled = !debounced) {
            Text(stringResource(R.string.console_call_room_accept_action))
        }
    }
}

@Composable
private fun ActiveCallContent(
    state: CallRoomUiState.Active,
    onEnd: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    isReconnecting: Boolean,
) {
    var elapsedSeconds by remember(state.startedAtEpochMillis) { mutableStateOf(0L) }
    LaunchedEffect(state.startedAtEpochMillis) {
        val startedAt = state.startedAtEpochMillis ?: return@LaunchedEffect
        while (isActive) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            delay(1_000)
        }
    }

    CallTypeIcon(state.callType)
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(
            if (isReconnecting) R.string.console_call_room_reconnecting else R.string.console_call_room_active_status,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = formatElapsed(elapsedSeconds),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(28.dp))
    ActiveCallControls(
        isMuted = isMuted,
        onToggleMute = onToggleMute,
        onEnd = onEnd,
        isSpeakerOn = isSpeakerOn,
        onToggleSpeaker = onToggleSpeaker,
        isCameraOn = true,
        onToggleCamera = {},
        onSwitchCamera = {},
        showVideoControls = false,
    )
}

/** Shared by the plain (voice) Active layout above and the full-bleed video layout in
 * [CallRoomScreen] itself — [showVideoControls] adds camera on/off + front/back flip alongside
 * the mute/end/speaker row every active call has. Switch-camera sits on its own row below the
 * main control row (per the Stitch-mockup-inspired redesign in TODOS.md), rather than crowding
 * into the same row as mic/camera/end. */
@Composable
private fun ActiveCallControls(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    isCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    showVideoControls: Boolean,
) {
    // Same debounce shape as StartRow — End is another fire-and-forget WS send with no response
    // to hook a reset off. Mute/camera/speaker are plain optimistic toggles, not guarded here —
    // flipping the same state twice is harmless.
    var endDebounced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(checked = isMuted, onCheckedChange = { onToggleMute() }) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = stringResource(
                        if (isMuted) R.string.console_call_room_unmute_action else R.string.console_call_room_mute_action,
                    ),
                )
            }
            if (showVideoControls) {
                FilledTonalIconToggleButton(checked = !isCameraOn, onCheckedChange = { onToggleCamera() }) {
                    Icon(
                        imageVector = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        contentDescription = stringResource(
                            if (isCameraOn) R.string.console_call_room_camera_off_action else R.string.console_call_room_camera_on_action,
                        ),
                    )
                }
            } else {
                FilledTonalIconToggleButton(checked = isSpeakerOn, onCheckedChange = { onToggleSpeaker() }) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        contentDescription = stringResource(
                            if (isSpeakerOn) R.string.console_call_room_speaker_off_action else R.string.console_call_room_speaker_on_action,
                        ),
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    if (endDebounced) return@FilledIconButton
                    endDebounced = true
                    scope.launch { delay(START_DEBOUNCE_MILLIS); endDebounced = false }
                    onEnd()
                },
                enabled = !endDebounced,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = stringResource(R.string.console_call_room_end_action))
            }
        }
        if (showVideoControls) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onSwitchCamera) {
                Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = stringResource(R.string.console_call_room_switch_camera_action),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CallTypeIcon(callType: CallType) {
    Icon(
        imageVector = if (callType == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
    )
}

@Composable
private fun bannerText(reason: CallEndBanner, partnerName: String): String = when (reason) {
    CallEndBanner.PartnerCancelled -> stringResource(R.string.console_call_room_banner_partner_cancelled, partnerName)
    CallEndBanner.PartnerDeclined -> stringResource(R.string.console_call_room_banner_partner_declined, partnerName)
    CallEndBanner.NoResponseFromPartner -> stringResource(R.string.console_call_room_banner_no_response, partnerName)
    CallEndBanner.CallEnded -> stringResource(R.string.console_call_room_banner_ended)
    CallEndBanner.PartnerLeft -> stringResource(R.string.console_call_room_banner_partner_left, partnerName)
    CallEndBanner.CallInterrupted -> stringResource(R.string.console_call_room_banner_interrupted)
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/** The Log tab — refreshed every time it's (re)entered, since [CallLogViewModel] does a plain
 * one-shot fetch rather than tracking a live push (see its own doc comment for why). */
@Composable
private fun CallLogTab(modifier: Modifier = Modifier, viewModel: CallLogViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = uiState) {
            CallLogUiState.Loading -> CircularProgressIndicator()
            CallLogUiState.Error -> Text(
                text = stringResource(R.string.console_call_log_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CallLogUiState.Content -> {
                if (state.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.console_call_log_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.entries, key = { it.id }) { entry -> CallLogRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(entry: CallLogEntry) {
    val isMissedForMe = !entry.isOutgoing && entry.status != CallLogStatus.ANSWERED
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallLogTypeBadge(callType = entry.callType, isMissedForMe = isMissedForMe)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callLogStatusLabel(entry),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMissedForMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    entry.startedAtEpochMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.status == CallLogStatus.ANSWERED && entry.durationSeconds != null) {
            Text(
                text = formatElapsed(entry.durationSeconds.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Replaces the old pair of small icons (a 24dp muted call-type glyph + a separate 16dp direction
 * arrow) with one bigger color-coded circle, per the Stitch-mockup-inspired redesign in
 * TODOS.md — the fill color itself now carries the "was this missed" signal (direction/outcome is
 * still fully covered by [callLogStatusLabel]'s own text, e.g. "Outgoing"/"Missed call"/"No
 * answer", so nothing is lost by dropping the second icon). Priority order: missed-for-me beats
 * everything else, then call type, then a neutral default for a plain answered/outgoing voice
 * call. */
@Composable
private fun CallLogTypeBadge(callType: CallType, isMissedForMe: Boolean) {
    val containerColor: Color
    val contentColor: Color
    when {
        isMissedForMe -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        callType == CallType.VIDEO -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (callType == CallType.VIDEO) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Resolves a raw [CallLogEntry] into the row's status line — the same four raw statuses read
 * differently depending on which side of the call this device was on (e.g. a call *this* device
 * declined reads as "You declined," not just "Declined"; a call the partner never answered reads
 * as "No answer" here but "Missed call" on their own device). See `_record_call_log` in
 * `app/services/call_room_service.py` for how the four raw statuses are derived. */
@Composable
private fun callLogStatusLabel(entry: CallLogEntry): String = when (entry.status) {
    CallLogStatus.ANSWERED -> stringResource(
        if (entry.isOutgoing) R.string.console_call_log_status_outgoing else R.string.console_call_log_status_incoming,
    )
    CallLogStatus.DECLINED -> stringResource(
        if (entry.isOutgoing) R.string.console_call_log_status_declined else R.string.console_call_log_status_you_declined,
    )
    CallLogStatus.CANCELLED -> stringResource(
        if (entry.isOutgoing) R.string.console_call_log_status_cancelled else R.string.console_call_log_status_missed,
    )
    CallLogStatus.MISSED -> stringResource(
        if (entry.isOutgoing) R.string.console_call_log_status_no_answer else R.string.console_call_log_status_missed,
    )
}
