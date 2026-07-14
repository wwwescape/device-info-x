package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** "Undo Delete (Soft)" — the sender's own "Delete for both" message shown again on this device
 * only, while it stays deleted for the partner (and server-side). Purely local, never synced; a
 * separate table for the same reason as [HiddenMessageEntity] (re-syncs rebuild the whole
 * [MessageEntity] row from server fields). Cleared by a full "Undo Delete". */
@Entity(tableName = "soft_restored_messages")
data class SoftRestoredMessageEntity(
    @PrimaryKey val messageId: String,
)
