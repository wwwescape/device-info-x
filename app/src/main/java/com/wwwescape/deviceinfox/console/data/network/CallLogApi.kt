package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.CallLogPageDto
import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/calls.py`. Requires pairing — matches that router's own
 * `Depends(require_paired)`. */
interface CallLogApi {
    @GET("calls/log")
    suspend fun getLog(): CallLogPageDto
}
