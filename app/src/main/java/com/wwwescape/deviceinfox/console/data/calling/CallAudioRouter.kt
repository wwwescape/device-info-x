package com.wwwescape.deviceinfox.console.data.calling

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Routes call audio through the device's voice-call audio path — `AudioManager` has no usage
 * anywhere else in this app (confirmed while planning Phase 2), so this is greenfield. Uses the
 * plain pre-API-26 `requestAudioFocus`/`isSpeakerphoneOn` calls rather than the newer
 * `AudioFocusRequest`/`setCommunicationDevice` APIs (added API 26/31) — this app's `minSdk` is 24,
 * and the older calls, while deprecated, still function correctly on every supported version;
 * branching on SDK level for a cosmetic API upgrade wasn't worth the complexity here. */
@Singleton
class CallAudioRouter @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var previousMode = AudioManager.MODE_NORMAL
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    /** Called once when a call becomes active — sets up the voice-call audio path and defaults
     * to earpiece (speaker off), matching how a real phone call starts. */
    @Suppress("DEPRECATION")
    fun start() {
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val listener = AudioManager.OnAudioFocusChangeListener { }
        focusListener = listener
        audioManager.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        setSpeakerOn(false)
    }

    fun setSpeakerOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
        _isSpeakerOn.value = on
    }

    fun toggleSpeaker() = setSpeakerOn(!_isSpeakerOn.value)

    /** Called once when the call ends — restores whatever audio mode/focus existed before this
     * call started, rather than assuming `MODE_NORMAL`, in case something else already had the
     * audio system in a non-default state. */
    @Suppress("DEPRECATION")
    fun stop() {
        audioManager.isSpeakerphoneOn = false
        _isSpeakerOn.value = false
        focusListener?.let { audioManager.abandonAudioFocus(it) }
        focusListener = null
        audioManager.mode = previousMode
    }
}
