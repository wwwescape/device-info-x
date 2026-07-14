package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.emoji.matureEmojiImageUrl

private const val DISABLED_ALPHA = 0.35f

/** Lowercased, with `_`/`-` read as word breaks — so "peach wink" finds `peach_wink`. */
private fun normalizeForSearch(text: String): String = text.lowercase().replace('_', ' ').replace('-', ' ')

/** Splits a query into lowercase words; a typed `.png` is dropped so "peach_wink.png" finds
 * `peach_wink` (the extension is never part of what's matched — "png" alone would match every
 * mature emoji). */
private fun searchTerms(query: String): List<String> =
    normalizeForSearch(query.replace(".png", "", ignoreCase = true)).split(' ').filter { it.isNotBlank() }

private fun matchesAllTerms(haystack: String, terms: List<String>): Boolean =
    terms.all { haystack.contains(it) }

/** The Reactions tab's content: a search box, a "Show mature content" checkbox right beneath it
 * (same behavior as the GIF tab's — off by default, plain `rememberSaveable`, not persisted), and
 * a scrollable grid of large emoji buttons, each sending a full-screen effect to both devices (see
 * `ReactionEffectBus`). Buttons are dimmed and non-clickable unless [partnerIsHere] — the tab
 * itself stays reachable either way, only the buttons gate on presence.
 *
 * Ticking the checkbox appends a divider and then server-hosted custom emojis below the built-in
 * ones. It only controls what this picker *lists* — and what a search covers: with it off, a
 * search only ever looks at the built-in reactions. A mature emoji the partner sends always plays
 * here regardless (see `ReactionEffectOverlay`).
 *
 * Search matches every word of the query (case-insensitive) against: for built-in reactions, the
 * official English emoji name plus the same English keyword list the Emoji tab's search uses
 * (by glyph); for mature emojis, the file name without
 * `.png`. */
@Composable
fun ReactionsPanel(
    partnerIsHere: Boolean,
    onReactionSelected: (ReactionEvent) -> Unit,
    onLoadMatureEmojis: suspend () -> List<String>,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMature by rememberSaveable { mutableStateOf(false) }
    // null = not loaded yet. An empty result is treated as "try again next time the box is
    // ticked", so a transient failure (offline) doesn't stick until the picker is reopened.
    var matureIds by remember { mutableStateOf<List<String>?>(null) }
    var isLoadingMature by remember { mutableStateOf(false) }

    LaunchedEffect(showMature) {
        if (showMature && matureIds.isNullOrEmpty()) {
            isLoadingMature = true
            matureIds = onLoadMatureEmojis()
            isLoadingMature = false
        }
    }

    // Emoji names never change at runtime, so this table is built once.
    val searchableText = remember {
        ReactionEffect.entries.associateWith { effect ->
            normalizeForSearch(listOfNotNull(effect.emojiName, EMOJI_KEYWORDS[effect.glyph]).joinToString(" "))
        }
    }
    val terms = searchTerms(searchQuery)
    val isSearching = terms.isNotEmpty()
    val builtInMatches = if (isSearching) {
        ReactionEffect.entries.filter { matchesAllTerms(searchableText.getValue(it), terms) }
    } else {
        ReactionEffect.entries
    }
    val allMatureIds = matureIds.orEmpty()
    val matureMatches = when {
        !showMature -> emptyList()
        isSearching -> allMatureIds.filter { matchesAllTerms(normalizeForSearch(it), terms) }
        else -> allMatureIds
    }
    // Loading or failed to load — the grid shows a status row in place of the mature emojis.
    val matureUnavailable = showMature && allMatureIds.isEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PICKER_CONTENT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.console_home_reaction_search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMature = !showMature }
                .padding(horizontal = 12.dp),
        ) {
            Checkbox(checked = showMature, onCheckedChange = { showMature = it })
            Text(stringResource(R.string.console_home_gif_mature_toggle), style = MaterialTheme.typography.bodySmall)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 56.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(builtInMatches, key = { it.wireValue }) { effect ->
                val tileModifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (partnerIsHere) 1f else DISABLED_ALPHA)
                    // The glyph itself, even for custom artwork: TalkBack speaks an emoji's name in
                    // the user's language.
                    .semantics { contentDescription = effect.glyph }
                    .then(
                        if (partnerIsHere) {
                            Modifier.clickable { onReactionSelected(ReactionEvent.Builtin(effect)) }
                        } else {
                            Modifier
                        },
                    )
                val iconRes = effect.iconRes
                if (iconRes != null) {
                    // Custom artwork sized off the same text style the emoji glyphs use — the tile
                    // is exactly as tall as a glyph tile (line height + the same 20dp of padding)
                    // and the image is ~1.15x the font size, roughly how large an emoji glyph
                    // actually draws — so it matches its neighbours and scales with font size.
                    val textStyle = MaterialTheme.typography.headlineLarge
                    val density = LocalDensity.current
                    val tileHeight = with(density) { textStyle.lineHeight.toDp() } + 20.dp
                    val iconSize = with(density) { (textStyle.fontSize * 1.15f).toDp() }
                    Box(modifier = tileModifier.height(tileHeight), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                } else {
                    Text(
                        text = effect.glyph,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = tileModifier.padding(vertical = 10.dp),
                    )
                }
            }
            if (showMature) {
                when {
                    matureUnavailable -> {
                        if (builtInMatches.isNotEmpty()) {
                            item(key = "mature_divider", span = { GridItemSpan(maxLineSpan) }) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        item(key = "mature_status", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingMature) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text(
                                        text = stringResource(R.string.console_home_reaction_mature_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    matureMatches.isNotEmpty() -> {
                        if (builtInMatches.isNotEmpty()) {
                            item(key = "mature_divider", span = { GridItemSpan(maxLineSpan) }) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        items(matureMatches, key = { "mature_$it" }) { id ->
                            AsyncImage(
                                model = matureEmojiImageUrl(id),
                                contentDescription = id,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .alpha(if (partnerIsHere) 1f else DISABLED_ALPHA)
                                    .then(
                                        if (partnerIsHere) {
                                            Modifier.clickable { onReactionSelected(ReactionEvent.Mature(id)) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
            if (builtInMatches.isEmpty() && matureMatches.isEmpty() && !matureUnavailable) {
                item(key = "no_results", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.console_home_emoji_search_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }
    }
}
