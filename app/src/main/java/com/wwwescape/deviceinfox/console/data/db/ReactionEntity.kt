package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One reaction per partner per message — a second reaction from the same partner overwrites
 * rather than stacking, matching how most messaging apps treat a single-emoji reaction slot. */
@Entity(
    tableName = "reactions",
    primaryKeys = ["messageId", "partnerId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class ReactionEntity(
    val messageId: String,
    val partnerId: String,
    val emoji: String,
    val reactedAtEpochMillis: Long,
)
