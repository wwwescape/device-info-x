package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.asset.AssetKind
import com.wwwescape.deviceinfox.console.data.asset.AssetPack
import com.wwwescape.deviceinfox.console.data.asset.AssetTier
import com.wwwescape.deviceinfox.console.data.asset.assetImageUrl

/** The Stickers tab: a search box, a "Show mature content" checkbox right beneath it (same
 * behavior as the GIF/Reactions tabs' — off by default, plain `rememberSaveable`), and a grid of
 * server-hosted sticker PNGs. Tapping one sends it immediately as an ordinary image message (see
 * `HomeViewModel.sendSticker`), like picking a GIF.
 *
 * The grid is the *standard* stickers (`stickers/standard/`), then — only while "Show mature
 * content" is ticked — the *nsfw* ones (`stickers/nsfw/`). A horizontal line separates the two
 * only when both have at least one sticker showing. Within each, stickers sit under a heading per
 * style folder (see [assetGroupItems]). With the checkbox off, search covers only the standard
 * stickers. Search matches every word of the query against the folder's label plus the file name
 * without `.png`. */
@Composable
fun StickersPanel(
    onLoadStickers: suspend (mature: Boolean) -> AssetPack?,
    onStickerSelected: (AssetTier, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMature by rememberSaveable { mutableStateOf(false) }
    val assets = rememberAssetPackState(showMature, onLoadStickers)

    val terms = searchTerms(searchQuery)
    val isSearching = terms.isNotEmpty()
    val pack = assets.pack
    val standardMatches = filterAssetIds(pack?.standard.orEmpty(), terms)
    val allNsfw = if (showMature) pack?.nsfw.orEmpty() else emptyList()
    val nsfwMatches = filterAssetIds(allNsfw, terms)
    val stillLoading = assets.isLoading && (pack == null || (showMature && allNsfw.isEmpty()))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PICKER_CONTENT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        PickerSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.console_home_sticker_search_placeholder),
        )
        MatureContentCheckbox(checked = showMature, onCheckedChange = { showMature = it })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stickerItems(AssetTier.STANDARD, standardMatches, onStickerSelected)
            if (nsfwMatches.isNotEmpty()) {
                // Only when both folders have something showing.
                if (standardMatches.isNotEmpty()) sectionDivider("nsfw_divider")
                stickerItems(AssetTier.NSFW, nsfwMatches, onStickerSelected)
            }
            when {
                stillLoading -> statusRow("status") { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                standardMatches.isEmpty() && nsfwMatches.isEmpty() -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(
                                if (isSearching) R.string.console_home_emoji_search_no_results else R.string.console_home_sticker_empty,
                            ),
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
}

private fun LazyGridScope.stickerItems(
    tier: AssetTier,
    ids: List<String>,
    onStickerSelected: (AssetTier, String) -> Unit,
) {
    assetGroupItems(tier, ids) { id ->
        AsyncImage(
            model = assetImageUrl(AssetKind.STICKERS, tier, id),
            contentDescription = assetLabel(id),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable { onStickerSelected(tier, id) }
                .padding(4.dp),
        )
    }
}
