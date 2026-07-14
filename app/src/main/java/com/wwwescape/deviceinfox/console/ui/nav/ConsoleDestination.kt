package com.wwwescape.deviceinfox.console.ui.nav

/** A separate route set from the public app's `Destination` enum — never merged, never shares a
 * NavHost with it. Messaging/Calendar/Period Tracker/Safe Locker are sibling bottom tabs now
 * (see [com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTab]'s own nested `NavHost`), not outer
 * routes — this outer graph only ever has one screen showing at a time: the tabs themselves, or
 * Settings pushed on top. */
sealed class ConsoleDestination(val route: String) {
    data object Tabs : ConsoleDestination("console_tabs")
    data object Settings : ConsoleDestination("console_settings")
    data object StarredMessages : ConsoleDestination("console_starred_messages")
    data object PinnedMessages : ConsoleDestination("console_pinned_messages")
    data object ConversationMedia : ConsoleDestination("console_conversation_media")

    /** The only parameterized route in this graph — every other destination here is a fixed
     * singleton screen, but "Message info" is inherently about one specific message, so it needs
     * an id along for the ride. [route] is the pattern registered with `NavHost`; [routeFor]
     * builds the literal, navigable route for a given message. */
    data object MessageInfo : ConsoleDestination("console_message_info/{messageId}") {
        fun routeFor(messageId: String) = "console_message_info/$messageId"
    }
}
