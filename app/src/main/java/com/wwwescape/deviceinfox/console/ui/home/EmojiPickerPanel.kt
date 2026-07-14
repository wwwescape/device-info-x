package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import kotlinx.coroutines.launch

private const val EMOJI_GRID_COLUMNS = 8
private val EMOJI_GRID_HEIGHT = 220.dp
private val EMOJI_TAB_STRIP_HEIGHT = 40.dp

private data class EmojiSection(val category: EmojiCategory, val emojis: List<String>)

/** Toggled by the emoji icon in [ComposerBar], replacing the software keyboard rather than
 * stacking above it (see [ComposerBar]'s own focus/IME handling around `showEmojiPicker`).
 * Appends the tapped emoji to the composer's text (the composer holds plain [String] state, not
 * a cursor-aware `TextFieldValue`, so insertion is always at the end rather than at the cursor
 * position) and reports every tap — grid or skin-tone popup alike — to [onEmojiUsed] so it rises
 * to the top of the leading Recently Used section the *next time this panel opens* — not live,
 * mid-session: [sections] below deliberately snapshots [recentEmojis] once, when this composable
 * is first composed, rather than reacting to every write [onEmojiUsed] makes. Reordering live
 * used to mean a second tap aimed at "the same emoji I just used" could land on whatever DataStore
 * write just reshuffled into that grid cell instead — freezing the Recent section for the whole
 * time the panel stays open (this composable is fully disposed and recomposed fresh on each
 * open/close via `ComposerBar`'s `AnimatedVisibility`, so a keyless `remember` re-snapshots
 * correctly every time) fixes that without touching the persistence path at all.
 *
 * Height is fixed at roughly 5 grid rows plus the category tab strip (the search field above them
 * is left to size itself naturally at `OutlinedTextField`'s own single-line minimum rather than a
 * forced height — forcing one smaller than that minimum previously clipped the placeholder/input
 * text), both so the panel reads as a deliberate keyboard replacement rather than an open-ended
 * scroll area, and so swapping it in for the IME doesn't visibly resize the composer when the two
 * heights don't match exactly. */
@Composable
fun EmojiPickerPanel(
    recentEmojis: List<String>,
    onEmojiSelected: (String) -> Unit,
    onEmojiUsed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember {
        buildList {
            if (recentEmojis.isNotEmpty()) add(EmojiSection(EmojiCategory.RECENT, recentEmojis))
            EmojiCategory.entries.filter { it != EmojiCategory.RECENT }.forEach { category ->
                add(EmojiSection(category, EMOJI_CATEGORIES[category].orEmpty()))
            }
        }
    }
    // Flat item index (headers + emoji cells both count as one grid item each) that each
    // category's header lands on — the target for the tab strip's "jump to category" scroll, and
    // (sorted below) also what the tab strip's selected-state highlight is derived from.
    val headerIndexByCategory = remember(sections) {
        var index = 0
        buildMap {
            sections.forEach { section ->
                put(section.category, index)
                index += 1 + section.emojis.size
            }
        }
    }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    // Which category's section the grid is actually scrolled to right now — the last header
    // whose index hasn't scrolled past yet. derivedStateOf (not a plain val) so this only
    // recomputes on the rare frames firstVisibleItemIndex actually crosses a header boundary,
    // not on every recomposition the scroll causes.
    val sortedHeaders = remember(headerIndexByCategory) { headerIndexByCategory.entries.sortedBy { it.value } }
    val currentCategory by remember(sortedHeaders) {
        derivedStateOf { sortedHeaders.lastOrNull { it.value <= gridState.firstVisibleItemIndex }?.key }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isSearching = searchQuery.isNotBlank()
    val searchResults = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val seen = LinkedHashSet<String>()
            EmojiCategory.entries.filter { it != EmojiCategory.RECENT }.forEach { category ->
                EMOJI_CATEGORIES[category].orEmpty().forEach { emoji ->
                    val keyword = EMOJI_KEYWORDS[emoji]
                    if (keyword != null && keyword.contains(searchQuery, ignoreCase = true)) {
                        seen.add(emoji)
                    }
                }
            }
            seen.toList()
        }
    }

    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.console_home_emoji_search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (isSearching) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.console_home_emoji_search_clear),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(EMOJI_GRID_COLUMNS),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(EMOJI_GRID_HEIGHT)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (isSearching) {
                if (searchResults.isEmpty()) {
                    item(key = "search_no_results", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.console_home_emoji_search_no_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }
                } else {
                    items(searchResults, key = { "search_$it" }) { emoji ->
                        EmojiCell(emoji = emoji, onEmojiSelected = onEmojiSelected, onEmojiUsed = onEmojiUsed)
                    }
                }
            } else {
                sections.forEach { section ->
                    item(key = "header_${section.category.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(section.category.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(section.emojis, key = { "${section.category.name}_$it" }) { emoji ->
                        EmojiCell(emoji = emoji, onEmojiSelected = onEmojiSelected, onEmojiUsed = onEmojiUsed)
                    }
                }
            }
        }
        // Below the grid, WhatsApp-style — was above it before; the jump-to-category scroll
        // itself is unchanged, only this Row's position in the Column moved. Always rendered,
        // searching or not, so the panel's total height never changes (see this composable's
        // doc comment on why that matters). Each icon gets an equal-width `weight(1f)` slot
        // spanning the full row rather than packing left with a horizontal scroll — every
        // category always fits (there's a small, fixed number of them), so evenly dividing the
        // width reads as a deliberate, neat tab strip instead of a scrollable overflow list.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EMOJI_TAB_STRIP_HEIGHT)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEach { section ->
                // No highlight while searching — the grid shows a flat, dedup'd result list then,
                // with no category boundaries in it for currentCategory to mean anything against.
                val isSelected = !isSearching && section.category == currentCategory
                Icon(
                    imageVector = section.category.icon,
                    contentDescription = stringResource(section.category.labelRes),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            searchQuery = ""
                            val targetIndex = headerIndexByCategory[section.category] ?: return@clickable
                            coroutineScope.launch { gridState.animateScrollToItem(targetIndex) }
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** One grid cell. A tap sends [emoji] straight to the composer; a long-press on a
 * modifier-eligible emoji (one with an [EMOJI_SKIN_TONE_VARIANTS] entry) instead opens a small
 * row of its 5 skin-tone variants — same [DropdownMenu]-anchored-to-a-`Box` mechanism this
 * screen's message reaction picker already uses, so it costs no extra position-tracking code. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(emoji: String, onEmojiSelected: (String) -> Unit, onEmojiUsed: (String) -> Unit) {
    var showToneVariants by remember { mutableStateOf(false) }
    val variants = EMOJI_SKIN_TONE_VARIANTS[emoji]
    Box {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        onEmojiSelected(emoji)
                        onEmojiUsed(emoji)
                    },
                    onLongClick = if (variants != null) {
                        { showToneVariants = true }
                    } else {
                        null
                    },
                )
                .padding(vertical = 6.dp),
        )
        if (variants != null) {
            DropdownMenu(expanded = showToneVariants, onDismissRequest = { showToneVariants = false }) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    variants.forEach { variant ->
                        Text(
                            text = variant,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable {
                                    onEmojiSelected(variant)
                                    onEmojiUsed(variant)
                                    showToneVariants = false
                                },
                        )
                    }
                }
            }
        }
    }
}
