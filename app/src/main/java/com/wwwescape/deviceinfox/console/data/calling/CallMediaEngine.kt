package com.wwwescape.deviceinfox.console.data.calling

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

private const val AUDIO_TRACK_ID = "call_audio_0"
private const val VIDEO_TRACK_ID = "call_video_0"
private const val LOCAL_STREAM_ID = "call_stream_0"
private const val VIDEO_WIDTH = 1280
private const val VIDEO_HEIGHT = 720
private const val VIDEO_FPS = 30

/** Thin wrapper around the plain `org.webrtc` callback-based API (`PeerConnectionFactory`,
 * `PeerConnection`, `SdpObserver`), turning the SDP negotiation calls into `suspend` functions —
 * written by hand against the stable, long-established classic WebRTC Android API rather than a
 * specific `stream-webrtc-android-ktx` extension name, since that library's own convenience
 * wrappers aren't part of its documented public surface. One instance per call (created fresh by
 * `CallRoomViewModel` when a call becomes active, disposed when it ends) — this class itself only
 * owns the process-wide `PeerConnectionFactory`/`EglBase`, which are safe and cheap to keep alive
 * for the app's whole lifetime.
 *
 * Audio is always captured (every call has a voice leg). Video capture (`startLocalVideoCapture`)
 * is separate and only used for video calls — audio and video share one `PeerConnection`, not
 * two. */
/** A running camera capture, bundled with everything [CallMediaEngine.stopLocalVideoCapture] needs
 * to tear it back down cleanly — one instance per active video call, owned by `CallRoomViewModel`. */
class CameraCaptureSession(
    val capturer: CameraVideoCapturer,
    val surfaceTextureHelper: SurfaceTextureHelper,
    val videoSource: VideoSource,
    val videoTrack: VideoTrack,
)

@Singleton
class CallMediaEngine @Inject constructor(@param:ApplicationContext private val context: Context) {

    val eglBase: EglBase = EglBase.create()

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>, observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return factory.createPeerConnection(rtcConfig, observer)
    }

    fun createLocalAudioTrack(): AudioTrack {
        val source: AudioSource = factory.createAudioSource(MediaConstraints())
        return factory.createAudioTrack(AUDIO_TRACK_ID, source)
    }

    val localStreamId: String get() = LOCAL_STREAM_ID

    /** Starts the camera and returns everything needed to both add a track to a [PeerConnection]
     * and render a local preview — `null` if the device has no camera matching the request (or no
     * camera at all). Uses `Camera2Enumerator` (the modern API) rather than the legacy
     * `Camera1Enumerator` — this app's `minSdk` (24) is already well above Camera2's own
     * requirement (21), so there's no fallback path to maintain. */
    fun startLocalVideoCapture(preferFrontFacing: Boolean): CameraCaptureSession? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val deviceName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) == preferFrontFacing }
            ?: deviceNames.firstOrNull()
            ?: return null
        val capturer = enumerator.createCapturer(deviceName, null) ?: return null

        val surfaceTextureHelper = SurfaceTextureHelper.create("CallVideoCapture", eglBase.eglBaseContext)
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        val videoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)

        return CameraCaptureSession(capturer, surfaceTextureHelper, videoSource, videoTrack)
    }

    fun stopLocalVideoCapture(session: CameraCaptureSession) {
        runCatching { session.capturer.stopCapture() }
        runCatching { session.capturer.dispose() }
        session.videoTrack.disposeQuietly()
        runCatching { session.videoSource.dispose() }
        runCatching { session.surfaceTextureHelper.dispose() }
    }

    /** `null` target — same "let the platform pick the next camera" behavior
     * `CameraVideoCapturer.switchCamera` already has when you don't care which one specifically. */
    fun switchCamera(session: CameraCaptureSession) {
        runCatching { session.capturer.switchCamera(null) }
    }

    suspend fun createOffer(peerConnection: PeerConnection, constraints: MediaConstraints): SessionDescription =
        suspendCancellableCoroutine { cont ->
            peerConnection.createOffer(createSdpObserver(cont), constraints)
        }

    suspend fun createAnswer(peerConnection: PeerConnection, constraints: MediaConstraints): SessionDescription =
        suspendCancellableCoroutine { cont ->
            peerConnection.createAnswer(createSdpObserver(cont), constraints)
        }

    suspend fun setLocalDescription(peerConnection: PeerConnection, description: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            peerConnection.setLocalDescription(setSdpObserver(cont), description)
        }

    suspend fun setRemoteDescription(peerConnection: PeerConnection, description: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            peerConnection.setRemoteDescription(setSdpObserver(cont), description)
        }

    fun addIceCandidate(peerConnection: PeerConnection, candidate: IceCandidate) {
        peerConnection.addIceCandidate(candidate)
    }

    /** Adapts the offer/answer-creation callbacks (`onCreateSuccess`/`onCreateFailure` — the only
     * two of [SdpObserver]'s four methods that ever actually fire for `createOffer`/`createAnswer`). */
    private fun createSdpObserver(cont: Continuation<SessionDescription>): SdpObserver =
        object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = cont.resume(description)
            override fun onCreateFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }

    /** Adapts the local/remote-description-setting callbacks (`onSetSuccess`/`onSetFailure` — the
     * only two that ever actually fire for `setLocalDescription`/`setRemoteDescription`), which
     * carry no payload at all, unlike the create pair above. */
    private fun setSdpObserver(cont: Continuation<Unit>): SdpObserver =
        object : SdpObserver {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String?) = Unit
        }
}

/** True for either direction of audio muting — this app never distinguishes "can't hear them" vs
 * "they can't hear me" beyond the one mic-mute control this feature has. */
fun AudioTrack.setMuted(muted: Boolean) {
    setEnabled(!muted)
}

fun MediaStreamTrack.disposeQuietly() {
    runCatching { dispose() }
}
