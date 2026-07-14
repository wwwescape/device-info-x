package com.wwwescape.deviceinfox.console.data.messaging

/** A staged text message waiting to be delivered at [scheduledAtEpochMillis] — always this
 * device's own (the server never sends a partner's scheduled messages down at all, see
 * `ScheduledMessageRepository`'s own doc comment), so unlike [com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry]
 * there's no shared/private split or ownership check needed client-side. */
data class ScheduledMessage(
    val id: String,
    val body: String,
    val scheduledAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
)
