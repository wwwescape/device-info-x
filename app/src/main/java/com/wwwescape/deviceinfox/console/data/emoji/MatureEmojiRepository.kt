package com.wwwescape.deviceinfox.console.data.emoji

import com.wwwescape.deviceinfox.console.data.network.ConsoleServerConfig
import com.wwwescape.deviceinfox.console.data.network.MatureEmojiApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** The Reactions tab's "Show mature content" emojis are server-hosted PNGs (never bundled in the
 * APK — see TODOS/CHANGELOG for why); this only lists their ids. No local persistence: Coil's disk
 * cache keeps the images themselves, and the list is re-fetched each time the checkbox is ticked
 * on a fresh picker. */
@Singleton
class MatureEmojiRepository @Inject constructor(private val api: MatureEmojiApi) {
    suspend fun list(): List<String> = consoleApiCall { api.list() }.items.map { it.id }
}

/** An emoji id is a server filename stem; the server only ever serves `[a-z0-9_-]{1,64}`. */
private val EMOJI_ID_PATTERN = Regex("^[a-z0-9_-]{1,64}$")

fun isValidMatureEmojiId(id: String): Boolean = EMOJI_ID_PATTERN.matches(id)

/** Absolute-path image URL against [ConsoleServerConfig.PLACEHOLDER_BASE_URL]'s placeholder host —
 * `BaseUrlInterceptor` (on the same authenticated `OkHttpClient` Coil's app-wide `ImageLoader`
 * uses) rewrites it to the real configured server, exactly as the GIF picker's thumbnails rely on. */
fun matureEmojiImageUrl(id: String): String =
    ConsoleServerConfig.PLACEHOLDER_BASE_URL + "emojis/nsfw/" + id
