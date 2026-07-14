package com.wwwescape.deviceinfox.console.ui.home

import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.presence.PartnerPresence

data class HomeUiState(
    val messages: List<ConsoleMessage> = emptyList(),
    val partnerDisplayName: String? = null,
    val partnerPresence: PartnerPresence? = null,
    val replyTarget: ConsoleMessage? = null,
    val editingMessage: ConsoleMessage? = null,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val selectedMessageIds: Set<String> = emptySet(),
) {
    val pinnedMessage: ConsoleMessage? get() = messages.lastOrNull { it.isPinned && !it.isDeleted }

    /** Every non-deleted message whose body contains [searchQuery] (case-insensitive), oldest
     * first — same chronological order as [messages] itself. Unlike the filtered-list approach
     * this replaced, [messages] is never swapped out while searching: the full conversation stays
     * rendered for context, and `HomeScreen` scrolls/jumps to each id in this list in turn
     * (WhatsApp-style) rather than hiding everything that isn't a match. Empty whenever not
     * searching or the query is blank. */
    val searchMatchIds: List<String> get() =
        if (isSearching && searchQuery.isNotBlank()) {
            messages.filter { !it.isDeleted && it.body?.contains(searchQuery, ignoreCase = true) == true }.map { it.id }
        } else {
            emptyList()
        }

    /** Derived, not stored — the moment [selectedMessageIds] empties out (last item deselected),
     * this flips false on its own. */
    val isSelectionMode: Boolean get() = selectedMessageIds.isNotEmpty()

    val selectedMessages: List<ConsoleMessage> get() = messages.filter { it.id in selectedMessageIds }
    val allSelectedAreStarred: Boolean get() = selectedMessages.isNotEmpty() && selectedMessages.all { it.isStarred }
    val allSelectedArePinned: Boolean get() = selectedMessages.isNotEmpty() && selectedMessages.all { it.isPinned }
}
