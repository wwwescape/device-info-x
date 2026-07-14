package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** Reactions/attachment/voice-note/reply-target all loaded as `List` (even the at-most-one
 * ones) and reduced with `.firstOrNull()` at the mapping site — more portable across Room
 * versions than relying on `@Relation` binding directly to a nullable single entity. */
data class MessageWithDetails(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val reactions: List<ReactionEntity>,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val voiceNotes: List<VoiceNoteEntity>,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val pollOptions: List<PollOptionEntity>,
    @Relation(parentColumn = "replyToId", entityColumn = "id")
    val repliedToMessages: List<MessageEntity>,
    // Only enough to tell "media, no text" apart from "actually deleted" in the reply preview
    // (ConsoleMessage.toConsoleMessage's ReplyPreview.hasMedia) — the replied-to message's own
    // attachment/voice-note details aren't otherwise needed there.
    @Relation(parentColumn = "replyToId", entityColumn = "messageId")
    val repliedToAttachments: List<AttachmentEntity>,
    @Relation(parentColumn = "replyToId", entityColumn = "messageId")
    val repliedToVoiceNotes: List<VoiceNoteEntity>,
)
