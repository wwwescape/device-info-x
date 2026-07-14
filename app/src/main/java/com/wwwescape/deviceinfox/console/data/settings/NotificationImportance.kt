package com.wwwescape.deviceinfox.console.data.settings

/** Per-couple choice of how prominently push notifications display, set in Private Settings.
 * Defaults to [HIGH] for a fresh install, matching WhatsApp/Signal's heads-up behavior — see
 * [com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager] for why switching between
 * these two switches which permanent, importance-specific channel id a push posts to, rather than
 * changing an existing channel's importance in place (confirmed unreliable on a real device, the
 * same as trying to change a channel's sound in place). Can still be a no-op if the user has
 * separately customized the channel's importance in Android's own Settings — that always wins
 * over anything this app tries to configure. */
enum class NotificationImportance {
    /** Heads-up banner + sound, same as a normal high-priority chat app notification. */
    HIGH,

    /** Shade-only — no heads-up banner. The app's original behavior before this setting existed. */
    DEFAULT,
}
