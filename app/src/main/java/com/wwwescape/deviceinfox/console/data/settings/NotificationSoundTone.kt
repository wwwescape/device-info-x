package com.wwwescape.deviceinfox.console.data.settings

/** Per-couple choice of *which* sound plays when a notification does make noise — independent of
 * [NotificationSoundMode], which controls *whether/how often* it plays at all. App-wide, not
 * per-category: every notification shares the one audible channel
 * ([com.wwwescape.deviceinfox.console.push.ConsoleFcmService.CHANNEL_ID]), so there's only ever
 * one tone to choose. Defaults to [DEFAULT] — today's implicit behavior — so existing installs
 * see no change until they opt in. See [com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager]
 * for how a change here gets applied: a channel's sound can only be set at creation and can't be
 * changed reliably even via delete+recreate (confirmed on a real device), so switching tones
 * switches which of several permanent, tone-specific channel ids a push posts to instead. */
enum class NotificationSoundTone {
    /** The device's own default notification sound. */
    DEFAULT,

    /** This app's bundled custom tone (`res/raw/chime.mp3`). */
    CHIME,
}
