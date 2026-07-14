package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.gif.GifResult
import com.wwwescape.deviceinfox.console.data.network.ConsoleServerConfig
import kotlinx.coroutines.delay

private const val GIF_GRID_COLUMNS = 3
private const val SEARCH_DEBOUNCE_MILLIS = 400L

/** The GIF tab's content, mounted alongside [EmojiPickerPanel]'s emoji grid inside the shared
 * tab strip added around both (see that file's own tab-switching doc comment). No local
 * persistence — a picker close forgets the last search, same as emoji search results do. */
@Composable
fun GifPickerPanel(
    onSearch: suspend (query: String, mature: Boolean) -> List<GifResult>,
    onTrending: suspend (mature: Boolean) -> List<GifResult>,
    onGifSelected: (GifResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var mature by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<GifResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Trending fires immediately (matches WhatsApp/Signal showing a trending strip before the
    // user types anything); a real search query is debounced so every keystroke doesn't spend one
    // of Klipy's rate-limited API calls.
    LaunchedEffect(searchQuery, mature) {
        isLoading = true
        results = if (searchQuery.isBlank()) {
            onTrending(mature)
        } else {
            delay(SEARCH_DEBOUNCE_MILLIS)
            onSearch(searchQuery, mature)
        }
        isLoading = false
    }

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
            placeholder = { Text(stringResource(R.string.console_home_gif_search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { mature = !mature }
                .padding(horizontal = 12.dp),
        ) {
            Checkbox(checked = mature, onCheckedChange = { mature = it })
            Text(stringResource(R.string.console_home_gif_mature_toggle), style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isLoading && results.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))
                }
                results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.console_home_gif_search_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(GIF_GRID_COLUMNS),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.id }) { gif ->
                            AsyncImage(
                                model = resolveProxyUrl(gif.previewUrl),
                                contentDescription = gif.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onGifSelected(gif) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Every URL a [GifResult] carries is a relative path this server issued (e.g.
 * `/api/v1/gifs/proxy?...`), not an absolute URL — same reason Retrofit itself is built against
 * [ConsoleServerConfig.PLACEHOLDER_BASE_URL] rather than a real host: `BaseUrlInterceptor` (wired
 * into the same [okhttp3.OkHttpClient] Coil's app-wide `ImageLoader` uses, see
 * `DeviceInfoXApplication.newImageLoader`) rewrites scheme/host/port to the real configured server
 * on every request regardless of what placeholder host the request started with. */
private fun resolveProxyUrl(relativePath: String): String {
    val placeholderHost = ConsoleServerConfig.PLACEHOLDER_BASE_URL.substringBefore("/api/v1/")
    return placeholderHost + relativePath
}
