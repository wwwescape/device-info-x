package com.wwwescape.deviceinfox.console.data.settings

/** Per-couple choice of how incoming-message notifications are worded, set in Private Settings.
 * Wiring this into an actual notification (Phase 7, when messages exist to notify about) must
 * always resolve GENERIC as the default for a fresh install. */
enum class NotificationTier {
    /** "Device Info X — 1 new update." No content, no fabricated unrelated event. */
    GENERIC,

    /** Worded to look like an unrelated system alert (e.g. "CPU Load Alert"). Opt-in only. */
    DISGUISED,
}
