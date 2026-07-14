package com.wwwescape.deviceinfox.console.data.gif

import com.wwwescape.deviceinfox.console.data.network.GifsApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.GifResultDto
import javax.inject.Inject
import javax.inject.Singleton

data class GifResult(
    val id: String,
    val title: String?,
    val width: Int?,
    val height: Int?,
    val previewUrl: String,
    val fullUrl: String,
)

/** Thin wrapper over [GifsApi]'s search/trending — no local persistence, same as emoji search
 * results not surviving the picker closing. Downloading a chosen result's bytes for sending is
 * [com.wwwescape.deviceinfox.console.data.messaging.MediaStorage.downloadGifToMediaDir], not here,
 * since that needs to live alongside every other "write into private media storage" function. */
@Singleton
class GifRepository @Inject constructor(private val gifsApi: GifsApi) {
    suspend fun search(query: String, mature: Boolean, page: Int = 1): List<GifResult> =
        consoleApiCall { gifsApi.search(query = query, page = page, mature = mature) }.items.map { it.toDomain() }

    suspend fun trending(mature: Boolean, page: Int = 1): List<GifResult> =
        consoleApiCall { gifsApi.trending(page = page, mature = mature) }.items.map { it.toDomain() }

    private fun GifResultDto.toDomain() = GifResult(
        id = id,
        title = title,
        width = width,
        height = height,
        previewUrl = previewUrl,
        fullUrl = fullUrl,
    )
}
