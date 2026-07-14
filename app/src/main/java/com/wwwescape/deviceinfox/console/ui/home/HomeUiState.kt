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

    val visibleMessages: List<ConsoleMessage> get() =
        if (isSearching && searchQuery.isNotBlank()) {
            messages.filter { !it.isDeleted && it.body?.contains(searchQuery, ignoreCase = true) == true }
        } else {
            messages
        }

    /** Derived, not stored — the moment [selectedMessageIds] empties out (last item deselected),
     * this flips false on its own. */
    val isSelectionMode: Boolean get() = selectedMessageIds.isNotEmpty()

    val selectedMessages: List<ConsoleMessage> get() = messages.filter { it.id in selectedMessageIds }
    val allSelectedAreStarred: Boolean get() = selectedMessages.isNotEmpty() && selectedMessages.all { it.isStarred }
    val allSelectedArePinned: Boolean get() = selectedMessages.isNotEmpty() && selectedMessages.all { it.isPinned }
}
