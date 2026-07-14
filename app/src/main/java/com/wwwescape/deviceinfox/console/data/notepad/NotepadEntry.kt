package com.wwwescape.deviceinfox.console.data.notepad

import com.wwwescape.deviceinfox.console.data.db.NotepadEntryType

/** No `isMine`/edit-gating concept, unlike [com.wwwescape.deviceinfox.console.data.vault.VaultItem] —
 * `PRIVATE` entries are always this device's own (the server never sends the partner's private
 * entries down at all, see [NotepadRepository]'s own doc comment), and `SHARED` entries have no
 * owner-only-edits restriction the way Safe Locker's shared items do, so every entry this device
 * ever sees is one it's always allowed to edit. [updatedAtRaw] must be echoed back verbatim on
 * save — see `NotepadEntryEntity.updatedAtRaw`'s own doc comment for why. */
data class NotepadEntry(
    val id: String,
    val type: NotepadEntryType,
    val title: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedAtRaw: String,
    val updatedBy: String?,
) {
    /** First non-blank line of [body] — the list row's fallback title for a note whose [title] is
     * still blank, same "auto-title from the first line" convention Keep/Apple Notes use when no
     * real title has been entered. */
    val preview: String get() = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}
