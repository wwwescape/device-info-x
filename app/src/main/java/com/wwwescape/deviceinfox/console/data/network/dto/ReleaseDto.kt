package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/release.py`'s `ReleaseOut`. Both fields nullable: `latestBuild` is null
 * until the account holder has ever run `set-latest-build`; `downloadUrl` is null until
 * `APK_DOWNLOAD_URL` is configured server-side — neither is an error case, just "not set up yet." */
@Serializable
data class ReleaseOutDto(
    @SerialName("latest_build") val latestBuild: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)
