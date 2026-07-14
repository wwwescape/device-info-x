package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per note. [type] `PRIVATE` entries only ever exist locally for their own owner — the
 * server never sends the partner's private entries down to this device at all (see
 * `NotepadRepository`'s own doc comment), so [ownerId] on a locally-cached `PRIVATE` row is
 * always this device's own self id; `SHARED` entries have a null [ownerId] (matches the server's
 * own `NotepadEntry.owner_id`, which is only ever set for `PRIVATE`). [ownerId]/[updatedBy] are
 * plain indexed strings, not `ForeignKey`s onto [PartnerEntity] — same treatment
 * [MessageEntity.senderId] already gets, since a self/partner id here is just a reference value
 * to compare against, not a relation Room needs to enforce or cascade.
 *
 * [updatedAtRaw] is the server's own ISO-8601 `updated_at` string, kept verbatim rather than only
 * as [updatedAtEpochMillis] — it's the optimistic-concurrency token a save must round-trip back
 * to the server exactly (see `NotepadRepository.saveEntry`), and Postgres's `TIMESTAMPTZ`
 * carries microsecond precision that [updatedAtEpochMillis] (millisecond) would silently drop;
 * echoing back a millis-truncated reconstruction would make the server's own equality check fail
 * spuriously on *every* save, not just a genuine conflict. [updatedAtEpochMillis] still exists
 * alongside it purely for display (sorting, relative-time formatting), where sub-millisecond
 * precision has never mattered anywhere else in this app either. */
@Entity(
    tableName = "notepad_entries",
    indices = [Index("type")],
)
data class NotepadEntryEntity(
    @PrimaryKey val id: String,
    val type: NotepadEntryType,
    val ownerId: String?,
    val title: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedAtRaw: String,
    val updatedBy: String?,
)
