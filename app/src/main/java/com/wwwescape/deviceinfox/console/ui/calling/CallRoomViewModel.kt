package com.wwwescape.deviceinfox.console.ui.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.calling.CallAudioRouter
import com.wwwescape.deviceinfox.console.data.calling.CallEndReason
import com.wwwescape.deviceinfox.console.data.calling.CameraCaptureSession
import com.wwwescape.deviceinfox.console.data.calling.CallInterruptionDetector
import com.wwwescape.deviceinfox.console.data.calling.CallMediaEngine
import com.wwwescape.deviceinfox.console.data.calling.CallProximityController
import com.wwwescape.deviceinfox.console.data.calling.CallRoomRepository
import com.wwwescape.deviceinfox.console.data.calling.CallSignalingEvent
import com.wwwescape.deviceinfox.console.data.calling.CallStatus
import com.wwwescape.deviceinfox.console.data.calling.CallType
import com.wwwescape.deviceinfox.console.data.calling.CallVibrator
import com.wwwescape.deviceinfox.console.data.calling.TurnCredentialsRepository
import com.wwwescape.deviceinfox.console.data.calling.disposeQuietly
import com.wwwescape.deviceinfox.console.data.calling.setMuted
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.presence.PresenceRepository
import com.wwwescape.deviceinfox.console.ui.components.ScreenPresenceTier
import com.wwwescape.deviceinfox.console.ui.components.screenPresenceTier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

/** What the Call Room screen actually renders — resolved from the raw
 * [com.wwwescape.deviceinfox.console.data.calling.CallRoomState] plus which side is "me," same
 * self/partner resolution shape [com.wwwescape.deviceinfox.console.ui.game.GameRoomViewModel]
 * already uses for Game Room. */
/** How long a transient ICE `DISCONNECTED` is tolerated before this device treats it as a real
 * drop and ends the call (CALLING_PLAN.md §8.2) — confirmed as "a few seconds," not zero-tolerance. */
private const val RECONNECT_GRACE_MILLIS = 8_000L

sealed interface CallRoomUiState {
    data object Loading : CallRoomUiState

    /** Partner isn't online anywhere in the app — general presence, not screen presence (see
     * CALLING_PLAN.md §2/§3: a call can be started toward a partner who isn't in this specific
     * screen, only reachability matters for the Start row). */
    data object PartnerUnreachable : CallRoomUiState
    data object StartAvailable : CallRoomUiState
    data class RingingOutgoing(val callType: CallType) : CallRoomUiState
    data class RingingIncoming(val callType: CallType) : CallRoomUiState
    data class Active(val callType: CallType, val startedAtEpochMillis: Long?) : CallRoomUiState
}

/** A resolved, ready-to-render transient status line — see [CallRoomRepository.callEndedEvent]'s
 * own doc comment for why the raw reason isn't rendered directly. Every case here is already
 * filtered to "only emit when this device should actually show something" — see [callEndedBanner]. */
sealed interface CallEndBanner {
    data object PartnerCancelled : CallEndBanner
    data object PartnerDeclined : CallEndBanner
    data object NoResponseFromPartner : CallEndBanner
    data object CallEnded : CallEndBanner
    data object PartnerLeft : CallEndBanner
    data object CallInterrupted : CallEndBanner
}

@HiltViewModel
class CallRoomViewModel @Inject constructor(
    private val repository: CallRoomRepository,
    partnerRepository: PartnerRepository,
    presenceRepository: PresenceRepository,
    private val mediaEngine: CallMediaEngine,
    private val turnCredentialsRepository: TurnCredentialsRepository,
    private val audioRouter: CallAudioRouter,
    private val vibrator: CallVibrator,
    private val proximityController: CallProximityController,
    private val interruptionDetector: CallInterruptionDetector,
) : ViewModel() {

    private val selfId: StateFlow<String?> =
        partnerRepository.selfProfile.map { it?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val partnerDisplayName: StateFlow<String?> = partnerRepository.partnerProfile
        .map { it?.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<CallRoomUiState> = combine(
        repository.roomState,
        partnerRepository.selfProfile,
        partnerRepository.partnerProfile,
        presenceRepository.partnerPresence,
    ) { state, self, partner, presence ->
        val myId = self?.id
        val partnerId = partner?.id
        when {
            myId == null || partnerId == null -> CallRoomUiState.Loading
            state.status == CallStatus.RINGING && state.callerId == myId ->
                CallRoomUiState.RingingOutgoing(state.callType ?: CallType.VOICE)
            state.status == CallStatus.RINGING && state.calleeId == myId ->
                CallRoomUiState.RingingIncoming(state.callType ?: CallType.VOICE)
            state.status == CallStatus.ACTIVE ->
                CallRoomUiState.Active(state.callType ?: CallType.VOICE, state.startedAtEpochMillis)
            presence.isOnline -> CallRoomUiState.StartAvailable
            else -> CallRoomUiState.PartnerUnreachable
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallRoomUiState.Loading)

    /** Drives the status row's 3-tier presence dot for the partner (CALLING_PLAN.md §7) — general
     * online presence combined with whether they're specifically on this screen right now. */
    val partnerPresenceTier: StateFlow<ScreenPresenceTier> = combine(
        repository.roomState,
        partnerRepository.partnerProfile,
        presenceRepository.partnerPresence,
    ) { state, partner, presence ->
        val partnerId = partner?.id
        screenPresenceTier(isOnline = presence.isOnline, isOnScreen = partnerId != null && state.present[partnerId] == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenPresenceTier.NOT_IN_APP)

    /** Resolves the raw [CallEndReason] into "should *this* device show something, and what" —
     * per the resolution table (CALLING_PLAN.md §3), several reasons are only ever shown to the
     * side that *didn't* cause them (e.g. the caller who tapped Cancel already knows; only the
     * callee needs telling). [reason.byUserId] equal to [selfId] means this device caused it. */
    val callEndedBanner: Flow<CallEndBanner> = repository.callEndedEvent.mapNotNull { reason ->
        val my = selfId.value
        when (reason) {
            is CallEndReason.Cancelled -> if (reason.byUserId != my) CallEndBanner.PartnerCancelled else null
            is CallEndReason.Declined -> if (reason.byUserId != my) CallEndBanner.PartnerDeclined else null
            is CallEndReason.NoResponse -> if (reason.byUserId != my) CallEndBanner.NoResponseFromPartner else null
            is CallEndReason.Ended -> CallEndBanner.CallEnded
            is CallEndReason.Left -> if (reason.byUserId != my) CallEndBanner.PartnerLeft else null
            is CallEndReason.Interrupted -> CallEndBanner.CallInterrupted
        }
    }

    fun enterRoom() = repository.enterRoom()
    fun leaveRoom() = repository.leaveRoom()
    fun startCall(callType: CallType) = repository.startCall(callType)
    fun acceptCall() = repository.acceptCall()
    fun declineCall() = repository.declineCall()
    fun cancelCall() = repository.cancelCall()
    fun endCall() = repository.endCall()

    // ---- Phase 2: the real media session ----------------------------------------------------

    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var isSessionActive = false

    // Buffers signaling that arrives before this device's own PeerConnection exists yet — the
    // callee in particular can receive `call.offer` before its own (slightly slower, since it
    // waits on a TURN-credentials fetch same as the caller does) local setup finishes. See
    // CallSignalingEvent's own doc comment for why this lives as its own stream rather than
    // folded into roomState.
    private var bufferedOffer: CallSignalingEvent.Offer? = null
    private val bufferedIceCandidates = mutableListOf<CallSignalingEvent.RemoteIceCandidate>()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    val isSpeakerOn: StateFlow<Boolean> = audioRouter.isSpeakerOn

    // ---- Phase 3: video --------------------------------------------------------------------

    private var cameraSession: CameraCaptureSession? = null
    private var isFrontFacing = true

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    val eglBaseContext: EglBase.Context get() = mediaEngine.eglBase.eglBaseContext

    // ---- Phase 4: transient network blips within an already-active call ---------------------

    /** Room *membership* (§3's state machine) stays strict — no grace period, leaving is leaving.
     * This is the one narrow exception (CALLING_PLAN.md §8.2): the underlying WebRTC media
     * connection is a separate layer that briefly drops and recovers on its own on mobile
     * networks (a wifi/cellular handoff, a few seconds of poor signal), so a transient ICE
     * `DISCONNECTED` gets a few seconds of tolerance before this device gives up and ends the call
     * outright — otherwise a normal signal hiccup would look identical to a deliberate hang-up. */
    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()
    private var reconnectGraceJob: Job? = null

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val isRingingOrActive = state is CallRoomUiState.RingingOutgoing ||
                    state is CallRoomUiState.RingingIncoming ||
                    state is CallRoomUiState.Active
                if (isRingingOrActive) {
                    interruptionDetector.start(onRealCallRinging = repository::interruptCall)
                } else {
                    interruptionDetector.stop()
                }
                if (state is CallRoomUiState.RingingIncoming) vibrator.startRinging() else vibrator.stop()

                if (state is CallRoomUiState.Active && !isSessionActive) {
                    isSessionActive = true
                    val isCaller = selfId.value != null && selfId.value == repository.roomState.value.callerId
                    startMediaSession(isCaller = isCaller, callType = state.callType)
                } else if (state !is CallRoomUiState.Active && isSessionActive) {
                    isSessionActive = false
                    teardownMediaSession()
                }
            }
        }
        viewModelScope.launch {
            repository.signalingEvent.collect { event -> onSignalingEvent(event) }
        }
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        localAudioTrack?.setMuted(muted)
        repository.sendControl(muted = muted)
    }

    fun toggleSpeaker() = audioRouter.toggleSpeaker()

    /** Doesn't renegotiate the peer connection or touch the `RtpSender` — just disables the local
     * track and stops feeding it frames, then tells the partner via `call.control` so their UI
     * can stop rendering a frozen frame. Full mid-call voice↔video *type* switching (adding a
     * video track to a call that started as voice-only) would need a real SDP renegotiation cycle
     * on top of this and isn't attempted here — camera on/off within an already-video call covers
     * the practical case without that added risk. */
    fun toggleCamera() {
        val session = cameraSession ?: return
        val on = !_isCameraOn.value
        _isCameraOn.value = on
        session.videoTrack.setEnabled(on)
        repository.sendControl(cameraOn = on)
    }

    fun switchCamera() {
        val session = cameraSession ?: return
        isFrontFacing = !isFrontFacing
        mediaEngine.switchCamera(session)
    }

    private fun startMediaSession(isCaller: Boolean, callType: CallType) {
        audioRouter.start()
        if (callType == CallType.VOICE) proximityController.start()

        viewModelScope.launch {
            val iceServers = runCatching { turnCredentialsRepository.fetchIceServers() }.getOrDefault(emptyList())
            val pc = mediaEngine.createPeerConnection(iceServers, peerConnectionObserver()) ?: return@launch
            peerConnection = pc

            val audioTrack = mediaEngine.createLocalAudioTrack()
            localAudioTrack = audioTrack
            pc.addTrack(audioTrack, listOf(mediaEngine.localStreamId))

            if (callType == CallType.VIDEO) {
                val session = mediaEngine.startLocalVideoCapture(preferFrontFacing = isFrontFacing)
                if (session != null) {
                    cameraSession = session
                    _localVideoTrack.value = session.videoTrack
                    pc.addTrack(session.videoTrack, listOf(mediaEngine.localStreamId))
                }
            }

            bufferedOffer?.let { applySignalingEvent(pc, it) }
            bufferedOffer = null
            bufferedIceCandidates.forEach { applySignalingEvent(pc, it) }
            bufferedIceCandidates.clear()

            if (isCaller) {
                val offer = mediaEngine.createOffer(pc, MediaConstraints())
                mediaEngine.setLocalDescription(pc, offer)
                repository.sendOffer(offer.description)
            }
        }
    }

    private fun teardownMediaSession() {
        peerConnection?.let { pc -> runCatching { pc.close() }; runCatching { pc.dispose() } }
        peerConnection = null
        localAudioTrack?.disposeQuietly()
        localAudioTrack = null
        cameraSession?.let { mediaEngine.stopLocalVideoCapture(it) }
        cameraSession = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _isCameraOn.value = true
        bufferedOffer = null
        bufferedIceCandidates.clear()
        _isMuted.value = false
        audioRouter.stop()
        proximityController.stop()
        reconnectGraceJob?.cancel()
        reconnectGraceJob = null
        _isReconnecting.value = false
    }

    private fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                reconnectGraceJob?.cancel()
                reconnectGraceJob = null
                _isReconnecting.value = false
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (reconnectGraceJob?.isActive == true) return
                _isReconnecting.value = true
                reconnectGraceJob = viewModelScope.launch {
                    delay(RECONNECT_GRACE_MILLIS)
                    // Still disconnected after the grace period — a real drop, not a blip. The
                    // room itself doesn't know the media died, so this device ends the call
                    // outright rather than leaving both sides staring at a dead "Call in
                    // progress" indefinitely.
                    endCall()
                }
            }
            PeerConnection.IceConnectionState.FAILED -> {
                reconnectGraceJob?.cancel()
                reconnectGraceJob = null
                endCall()
            }
            else -> Unit
        }
    }

    private fun onSignalingEvent(event: CallSignalingEvent) {
        val pc = peerConnection
        if (pc == null) {
            when (event) {
                is CallSignalingEvent.Offer -> bufferedOffer = event
                is CallSignalingEvent.RemoteIceCandidate -> bufferedIceCandidates.add(event)
                is CallSignalingEvent.Answer -> Unit // can't arrive before our own pc exists — we'd be the caller, already past this point
            }
            return
        }
        applySignalingEvent(pc, event)
    }

    private fun applySignalingEvent(pc: PeerConnection, event: CallSignalingEvent) {
        viewModelScope.launch {
            when (event) {
                is CallSignalingEvent.Offer -> {
                    mediaEngine.setRemoteDescription(pc, SessionDescription(SessionDescription.Type.OFFER, event.sdp))
                    val answer = mediaEngine.createAnswer(pc, MediaConstraints())
                    mediaEngine.setLocalDescription(pc, answer)
                    repository.sendAnswer(answer.description)
                }
                is CallSignalingEvent.Answer -> {
                    mediaEngine.setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, event.sdp))
                }
                is CallSignalingEvent.RemoteIceCandidate -> {
                    mediaEngine.addIceCandidate(pc, IceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate))
                }
            }
        }
    }

    private fun peerConnectionObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            repository.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = onIceConnectionStateChanged(newState)
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit

        /** Unified Plan's real "a remote track arrived" callback — `onAddStream`/`onAddTrack`
         * above are the legacy Plan B pair, kept only because they're abstract on the interface
         * and must be implemented; this is the one that actually fires (see
         * `createPeerConnection`'s explicit `SdpSemantics.UNIFIED_PLAN`). Only the video case
         * matters here — remote audio plays automatically once the track exists, no explicit
         * rendering needed the way video's `SurfaceViewRenderer` requires. */
        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) _remoteVideoTrack.value = track
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isSessionActive) {
            isSessionActive = false
            teardownMediaSession()
        }
        vibrator.stop()
        interruptionDetector.stop()
    }
}
