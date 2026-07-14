package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import com.wwwescape.deviceinfox.console.data.asset.AssetKind
import com.wwwescape.deviceinfox.console.data.asset.AssetPack
import com.wwwescape.deviceinfox.console.data.asset.AssetTier
import com.wwwescape.deviceinfox.console.data.asset.assetImageUrl

internal const val DISABLED_ALPHA = 0.35f

/** A full-width horizontal rule between two sections of a picker grid. */
internal fun LazyGridScope.sectionDivider(key: String) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** A full-width single line of centered status content (spinner / message) in a picker grid. */
internal fun LazyGridScope.statusRow(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** The Reactions tab's content: a search box, a "Show mature content" checkbox right beneath it
 * (same behavior as the GIF tab's — off by default, plain `rememberSaveable`, not persisted), and
 * a scrollable grid of large emoji buttons, each sending a full-screen effect to both devices (see
 * `ReactionEffectBus`). Buttons are dimmed and non-clickable unless [partnerIsHere], and also for
 * as long as [isEffectPlaying] — from the moment any reaction is queued until the last one has
 * finished its 5 seconds — so reactions can't be spammed into a long queue. The tab itself stays
 * reachable either way, only the buttons gate.
 *
 * The grid is, in order: the built-in reactions; then — each preceded by a horizontal line, and
 * only if it has at least one entry — the server-hosted *standard* reactions
 * (`reactions/standard/`), and, only while "Show mature content" is ticked, the *nsfw* ones
 * (`reactions/nsfw/`). The server-hosted ones sit under a heading per style folder (see
 * [assetGroupItems]). Both kinds of server-hosted reaction play the centered pop animation
 * (`CustomEmojiPop`) rather than the built-ins' effects. The checkbox only controls what this
 * picker lists — and what a search covers; a mature emoji the partner sends always plays here
 * regardless (see `ReactionEffectOverlay`).
 *
 * Search matches every word of the query (case-insensitive) against: for built-in reactions, the
 * official English emoji name plus the same English keyword list the Emoji tab's search uses (by
 * glyph); for server-hosted ones, the folder's label plus the file name without `.png`. */
@Composable
fun ReactionsPanel(
    partnerIsHere: Boolean,
    onReactionSelected: (ReactionEvent) -> Unit,
    onLoadReactionAssets: suspend (mature: Boolean) -> AssetPack?,
    modifier: Modifier = Modifier,
    isEffectPlaying: Boolean = false,
) {
    // Dimmed and non-clickable while the partner is away *or* a reaction is still queued/playing.
    val enabled = partnerIsHere && !isEffectPlaying
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMature by rememberSaveable { mutableStateOf(false) }
    val assets = rememberAssetPackState(showMature, onLoadReactionAssets)

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
    val pack = assets.pack
    val standardMatches = filterAssetIds(pack?.standard.orEmpty(), terms)
    val allNsfw = if (showMature) pack?.nsfw.orEmpty() else emptyList()
    val nsfwMatches = filterAssetIds(allNsfw, terms)
    val hasBuiltInOrStandard = builtInMatches.isNotEmpty() || standardMatches.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PICKER_CONTENT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        PickerSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.console_home_reaction_search_placeholder),
        )
        MatureContentCheckbox(checked = showMature, onCheckedChange = { showMature = it })
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
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    // The glyph itself, even for custom artwork: TalkBack speaks an emoji's name in
                    // the user's language.
                    .semantics { contentDescription = effect.glyph }
                    .then(
                        if (enabled) {
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

            if (standardMatches.isNotEmpty()) {
                if (builtInMatches.isNotEmpty()) sectionDivider("standard_divider")
                customReactionItems(AssetTier.STANDARD, standardMatches, enabled, onReactionSelected)
            }

            if (showMature) {
                when {
                    nsfwMatches.isNotEmpty() -> {
                        if (hasBuiltInOrStandard) sectionDivider("nsfw_divider")
                        customReactionItems(AssetTier.NSFW, nsfwMatches, enabled, onReactionSelected)
                    }
                    allNsfw.isEmpty() && assets.isLoading -> {
                        if (hasBuiltInOrStandard) sectionDivider("nsfw_divider")
                        statusRow("nsfw_status") { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                    allNsfw.isEmpty() && !isSearching -> {
                        if (hasBuiltInOrStandard) sectionDivider("nsfw_divider")
                        statusRow("nsfw_status") {
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

            val stillLoading = showMature && allNsfw.isEmpty() && assets.isLoading
            if (builtInMatches.isEmpty() && standardMatches.isEmpty() && nsfwMatches.isEmpty() && isSearching && !stillLoading) {
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

private fun LazyGridScope.customReactionItems(
    tier: AssetTier,
    ids: List<String>,
    enabled: Boolean,
    onReactionSelected: (ReactionEvent) -> Unit,
) {
    assetGroupItems(tier, ids) { id ->
        AsyncImage(
            model = assetImageUrl(AssetKind.REACTIONS, tier, id),
            contentDescription = assetLabel(id),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(
                    if (enabled) {
                        Modifier.clickable { onReactionSelected(ReactionEvent.Custom(tier, id)) }
                    } else {
                        Modifier
                    },
                )
                .padding(8.dp),
        )
    }
}

/** The search box shared by the Reactions and Stickers tabs — same look as the Emoji tab's, with a
 * clear button once there's text. */
@Composable
internal fun PickerSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
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
}

/** The "Show mature content" checkbox row — the same one the GIF tab has, reused as-is (including
 * its string) by the Reactions and Stickers tabs. */
@Composable
internal fun MatureContentCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(R.string.console_home_gif_mature_toggle), style = MaterialTheme.typography.bodySmall)
    }
}
