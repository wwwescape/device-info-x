package com.wwwescape.deviceinfox.console.data.whatsnew

import androidx.annotation.StringRes
import com.wwwescape.deviceinfox.R

data class WhatsNewEntry(val tag: String, @param:StringRes val textRes: Int)

/**
 * The full catalog of "What's New" bullets this app has ever shipped — exact mirror of how
 * `FeatureTourTarget` call sites each carry their own `tourKey` + copy, just centralized here
 * instead of scattered at each target's own call site, since a What's New entry isn't tied to a
 * specific on-screen element the way a coach mark is.
 *
 * Each entry's copy lives here as a string resource (localized × 5 locales), not server content —
 * the server (`whats_new_seen` table) only ever tracks which [tag]s a given account has been
 * shown, never the text itself. This is what the CLI's `enable-whats-new`/`disable-whats-new`
 * commands operate on, per account, per tag — same shape as `enable-tours`/`disable-tours`.
 *
 * **Append-only — never remove or reorder an existing entry's [WhatsNewEntry.tag].** It's the
 * durable per-account seen-state key; changing it would make an already-dismissed entry look
 * unseen again (or vice versa) for every account. Add a new entry for each newly shipped feature
 * worth announcing, alongside its own new string resource.
 */
val WHATS_NEW_ENTRIES: List<WhatsNewEntry> = listOf(
    WhatsNewEntry("game_room_2026_08", R.string.console_whats_new_game_room),
    WhatsNewEntry("duress_code_2026_08", R.string.console_whats_new_duress_code),
    WhatsNewEntry("scheduled_send_2026_08", R.string.console_whats_new_scheduled_send),
    WhatsNewEntry("notepad_2026_08", R.string.console_whats_new_notepad),
    WhatsNewEntry("calling_2026_08", R.string.console_whats_new_calling),
)
