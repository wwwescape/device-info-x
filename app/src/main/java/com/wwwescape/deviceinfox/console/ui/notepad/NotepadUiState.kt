package com.wwwescape.deviceinfox.console.ui.notepad

import com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry

enum class NotepadTab { PRIVATE, SHARED }

data class NotepadUiState(
    val privateEntries: List<NotepadEntry> = emptyList(),
    val sharedEntries: List<NotepadEntry> = emptyList(),
    val selectedTab: NotepadTab = NotepadTab.PRIVATE,
    val selectedEntryIds: Set<String> = emptySet(),
) {
    val visibleEntries: List<NotepadEntry>
        get() = if (selectedTab == NotepadTab.PRIVATE) privateEntries else sharedEntries

    /** Derived, not stored — see `HomeUiState.isSelectionMode`'s identical reasoning: the moment
     * [selectedEntryIds] empties out (last note deselected), this flips false on its own. */
    val isSelectionMode: Boolean get() = selectedEntryIds.isNotEmpty()
}
