package com.wwwescape.deviceinfox.console.data.asset

import com.wwwescape.deviceinfox.console.data.network.AssetsApi
import com.wwwescape.deviceinfox.console.data.network.ConsoleServerConfig
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Which server-hosted asset set — mirrors `asset_pack_service.AssetKind`; [path] is the URL
 * segment. */
enum class AssetKind(val path: String) {
    REACTIONS("reactions"),
    STICKERS("stickers"),
}

/** Which folder of a kind — mirrors `asset_pack_service.AssetTier`. [NSFW] is the "mature content"
 * folder, only ever listed/shown behind the picker's "Show mature content" checkbox. */
enum class AssetTier(val path: String) {
    STANDARD("standard"),
    NSFW("nsfw"),
}

/** The ids (server filename stems, already sorted) of one kind's two folders. [nsfw] is always
 * empty unless it was requested with `mature = true`. */
data class AssetPack(val standard: List<String>, val nsfw: List<String>)

/** Thin wrapper over [AssetsApi]'s list call — no local persistence: Coil's disk cache keeps the
 * images themselves, and a list is re-fetched each time a picker tab needs it. These are
 * server-hosted PNGs, never bundled in the APK (see the Device Info X Server changelog). */
@Singleton
class AssetPackRepository @Inject constructor(private val api: AssetsApi) {
    suspend fun list(kind: AssetKind, mature: Boolean): AssetPack {
        val response = consoleApiCall { api.list(kind.path, mature) }
        return AssetPack(standard = response.standard, nsfw = response.nsfw)
    }
}

/** An asset id is the server's path below a tier folder without `.png` — either a file name
 * (`kiss`) or one level of "style" folder plus file name (`cartoon/kiss`); each segment is
 * `[a-z0-9_-]{1,64}`. Anything deeper is never served. */
private val ASSET_ID_PATTERN = Regex("^[a-z0-9_-]{1,64}(/[a-z0-9_-]{1,64})?$")

fun isValidAssetId(id: String): Boolean = ASSET_ID_PATTERN.matches(id)

/** Absolute-path image URL (the id's optional `style/` slash is left as a plain path separator) against [ConsoleServerConfig.PLACEHOLDER_BASE_URL]'s placeholder host —
 * `BaseUrlInterceptor` (on the same authenticated `OkHttpClient` Coil's app-wide `ImageLoader`
 * uses) rewrites it to the real configured server, exactly as the GIF picker's thumbnails rely on. */
fun assetImageUrl(kind: AssetKind, tier: AssetTier, id: String): String =
    ConsoleServerConfig.PLACEHOLDER_BASE_URL + "assets/" + kind.path + "/" + tier.path + "/" + id
