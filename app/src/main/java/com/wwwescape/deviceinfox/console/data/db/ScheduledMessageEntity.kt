package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per staged message this device's own account is waiting to send — always this
 * device's own (the server never sends a partner's scheduled messages down at all, see
 * `ScheduledMessageRepository`'s own doc comment), so unlike [NotepadEntryEntity] there's no
 * `type`/`ownerId` split to store. */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey val id: String,
    val body: String,
    val scheduledAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
)
