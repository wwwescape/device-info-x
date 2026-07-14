package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** Mirrors [MessageWithDetails]'s shape. */
data class CalendarEventWithAttachments(
    @Embedded val event: CalendarEventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val attachments: List<CalendarAttachmentEntity>,
)
