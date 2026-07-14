package com.wwwescape.deviceinfox.console.data.settings

/** Per-couple choice of whether/how often push notifications play sound, set in Private
 * Settings. Independent of [NotificationImportance] — a [MUTED] or [THROTTLED] push still
 * pops up as a heads-up banner under [NotificationImportance.HIGH], it just won't make noise.
 * See [com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager] for how this is
 * implemented as a choice between two fixed channels rather than mutating one channel's sound
 * per push. Defaults to [ALWAYS] — today's implicit behavior — so existing installs see no
 * change until they opt in. */
enum class NotificationSoundMode {
    /** Every notification plays the channel's default sound, same as before this setting existed. */
    ALWAYS,

    /** At most one sound every 15 minutes — extra notifications in that window still show
     * normally (including heads-up, if [NotificationImportance.HIGH]), just silently. */
    THROTTLED,

    /** No notification from this app ever plays a sound. */
    MUTED,
}
