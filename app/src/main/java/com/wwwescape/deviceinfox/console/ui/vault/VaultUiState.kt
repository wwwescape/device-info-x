package com.wwwescape.deviceinfox.console.ui.vault

import com.wwwescape.deviceinfox.console.data.vault.VaultAlbum
import com.wwwescape.deviceinfox.console.data.vault.VaultItem

sealed interface VaultFilter {
    data object All : VaultFilter
    data object Favorites : VaultFilter
    data class Album(val albumId: String) : VaultFilter
}

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val albums: List<VaultAlbum> = emptyList(),
    val selectedFilter: VaultFilter = VaultFilter.All,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val selectedItemIds: Set<String> = emptySet(),
) {
    val visibleItems: List<VaultItem> get() {
        val byFilter = when (val filter = selectedFilter) {
            VaultFilter.All -> items
            VaultFilter.Favorites -> items.filter { it.isFavorite }
            is VaultFilter.Album -> items.filter { it.albumId == filter.albumId }
        }
        return if (isSearching && searchQuery.isNotBlank()) {
            byFilter.filter { it.caption?.contains(searchQuery, ignoreCase = true) == true }
        } else {
            byFilter
        }
    }

    /** Derived, not stored — see `HomeUiState.isSelectionMode`'s identical reasoning: the moment
     * [selectedItemIds] empties out (last item deselected), this flips false on its own. */
    val isSelectionMode: Boolean get() = selectedItemIds.isNotEmpty()

    val selectedItems: List<VaultItem> get() = items.filter { it.id in selectedItemIds }
    val allSelectedAreFavorite: Boolean get() = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite }
}
