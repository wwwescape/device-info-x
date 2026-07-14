package com.wwwescape.deviceinfox.console.data.presence

/** [isInMessagesScreen] implies [isOnline] (it's only ever set true while connected, and cleared
 * server-side the moment a connection drops — see `app/ws/manager.py`'s `_in_messages_screen`),
 * so `HomeScreen`'s subtitle priority is simply Here > Online > Last seen. [isTyping] is
 * deliberately independent of the other three — it only drives the typing *bubble* in the
 * message list (`HomeScreen.TypingIndicatorBubble`), not the header subtitle; showing "Typing…"
 * in the header too would just be the same information twice. */
data class PartnerPresence(
    val isOnline: Boolean,
    val lastSeenEpochMillis: Long?,
    val isInMessagesScreen: Boolean,
    val isTyping: Boolean,
)
