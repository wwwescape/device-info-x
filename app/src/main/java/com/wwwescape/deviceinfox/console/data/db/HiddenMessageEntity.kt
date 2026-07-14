package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** "Delete for me" — purely local, never synced. Deliberately a separate table rather than a
 * column on [MessageEntity]: [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.applyRemoteMessage]
 * upserts a *fresh* [MessageEntity] built only from server fields every time a message is
 * re-synced (reconnect, a reaction, an edit — anything touching that id), which would silently
 * reset a same-table local-only column back to its default. This table is never touched by that
 * path, so hiding survives re-syncs. */
@Entity(tableName = "hidden_messages")
data class HiddenMessageEntity(
    @PrimaryKey val messageId: String,
)
