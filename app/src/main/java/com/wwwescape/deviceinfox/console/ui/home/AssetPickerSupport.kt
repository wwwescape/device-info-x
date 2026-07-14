package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.console.data.asset.AssetPack
import com.wwwescape.deviceinfox.console.data.asset.AssetTier

// Shared by the Reactions and Stickers tabs, which both browse server-hosted PNGs (standard +
// nsfw folders) with a search box and a "Show mature content" checkbox.

/** Lowercased, with `_`/`-` read as word breaks — so "peach wink" finds `peach_wink`. */
internal fun normalizeForSearch(text: String): String = text.lowercase().replace('_', ' ').replace('-', ' ')

/** Splits a query into lowercase words; a typed `.png` is dropped so "peach_wink.png" finds
 * `peach_wink` (the extension is never part of what's matched — "png" alone would match every
 * file). */
internal fun searchTerms(query: String): List<String> =
    normalizeForSearch(query.replace(".png", "", ignoreCase = true)).split(' ').filter { it.isNotBlank() }

internal fun matchesAllTerms(haystack: String, terms: List<String>): Boolean =
    terms.all { haystack.contains(it) }

/** The file name of an asset id — the part after its `style/` folder, if any. */
private fun assetFileName(id: String): String = id.substringAfterLast('/')

/** The `style` folder of an asset id, or null for a file sitting directly in a tier folder. */
private fun assetFolder(id: String): String? = if ('/' in id) id.substringBefore('/') else null

private val LEADING_SORT_PREFIX = Regex("^\\d+[-_]+")
private val DIGIT_LED_SHORT_WORD = Regex("^\\d+[a-z]{1,2}$")

/** The heading shown above a style folder's items. Built from the folder name: a leading numeric
 * sort prefix is dropped (`10-love` -> "Love", so a prefix can control the order without showing),
 * `-`/`_` become spaces, and every word is capitalized — `teddy-bears` -> "Teddy Bears". A short
 * word that starts with digits (`3d`, `4k`) is upper-cased whole ("3D") since it has no first
 * letter to capitalize. Not translated: it comes from the owner's own folder names. */
internal fun folderLabel(folder: String): String {
    val stripped = folder.replace(LEADING_SORT_PREFIX, "").ifEmpty { folder }
    return stripped
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            if (DIGIT_LED_SHORT_WORD.matches(word)) word.uppercase() else word.replaceFirstChar { it.uppercase() }
        }
}

/** [ids] narrowed to those matching every term (all of them if there are none). A term matches if
 * it appears in the item's folder label *or* its file name — all terms are checked against that
 * combined text, so "anime" finds the whole Anime folder and "anime kiss" narrows it. */
internal fun filterAssetIds(ids: List<String>, terms: List<String>): List<String> =
    if (terms.isEmpty()) {
        ids
    } else {
        ids.filter { id ->
            val text = listOfNotNull(assetFolder(id)?.let(::folderLabel), assetFileName(id)).joinToString(" ")
            matchesAllTerms(normalizeForSearch(text), terms)
        }
    }

/** A readable label for an asset id for screen readers — `cartoon/peach_wink` -> "peach wink,
 * Cartoon" (the file name, then its folder's label now that it's visible). */
internal fun assetLabel(id: String): String {
    val name = assetFileName(id).replace('_', ' ').replace('-', ' ')
    return assetFolder(id)?.let { "$name, ${folderLabel(it)}" } ?: name
}

/** One run of items under one heading: [label] is null for loose files (no folder). */
internal data class AssetGroup(val folder: String?, val label: String?, val ids: List<String>)

/** Groups [ids] by style folder, in the server's order (folder, then file — so each folder's items
 * are already contiguous). Any loose files (none are expected: every file lives in a folder) come
 * first, unlabeled, ahead of the labeled folders. */
internal fun groupAssetIds(ids: List<String>): List<AssetGroup> {
    val loose = ids.filter { '/' !in it }
    val byFolder = LinkedHashMap<String, MutableList<String>>()
    ids.filter { '/' in it }.forEach { id -> byFolder.getOrPut(id.substringBefore('/')) { mutableListOf() }.add(id) }
    return buildList {
        if (loose.isNotEmpty()) add(AssetGroup(folder = null, label = null, ids = loose))
        byFolder.forEach { (folder, folderIds) -> add(AssetGroup(folder, folderLabel(folder), folderIds)) }
    }
}

/** Emits [ids] into a picker grid grouped under their folder headings — the same heading style the
 * Emoji tab uses for its categories (`labelSmall`, `onSurfaceVariant`, a full-width row) — with
 * [itemContent] drawing each item. The same folder name may appear in both tiers (`standard` and
 * `nsfw`); keys include the [tier] so they never collide. */
internal fun LazyGridScope.assetGroupItems(
    tier: AssetTier,
    ids: List<String>,
    itemContent: @Composable LazyGridItemScope.(id: String) -> Unit,
) {
    groupAssetIds(ids).forEach { group ->
        group.label?.let { label ->
            item(key = "label_${tier.path}_${group.folder}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }
        items(group.ids, key = { "${tier.path}_$it" }) { id -> itemContent(id) }
    }
}

/** Loads an asset pack for a picker tab: the standard folder as soon as the tab is shown, and the
 * nsfw folder's ids too once "Show mature content" is ticked. A failed load leaves [pack] as it
 * was (null if nothing has loaded yet) and is retried the next time it's needed, so a transient
 * failure (offline) doesn't stick until the picker is reopened. */
@Stable
internal class AssetPackState(private val load: suspend (mature: Boolean) -> AssetPack?) {
    var pack by mutableStateOf<AssetPack?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    private var matureLoaded = false

    suspend fun ensureLoaded(mature: Boolean) {
        if (pack != null && (!mature || matureLoaded)) return
        isLoading = true
        val result = load(mature)
        if (result != null) {
            pack = result
            if (mature) matureLoaded = true
        }
        isLoading = false
    }
}

@Composable
internal fun rememberAssetPackState(
    showMature: Boolean,
    load: suspend (mature: Boolean) -> AssetPack?,
): AssetPackState {
    val currentLoad by rememberUpdatedState(load)
    val state = remember { AssetPackState { mature -> currentLoad(mature) } }
    LaunchedEffect(showMature) { state.ensureLoaded(showMature) }
    return state
}
