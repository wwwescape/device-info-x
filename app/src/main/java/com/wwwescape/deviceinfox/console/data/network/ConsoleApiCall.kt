package com.wwwescape.deviceinfox.console.data.network

import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Wraps a Retrofit suspend call, translating Retrofit's [HttpException] (thrown for any
 * non-2xx response) into a [ConsoleApiException] carrying the server's actual `detail` message,
 * so repositories can surface something more useful than a bare status code. Every repository
 * wired to the server (Phase 11.3 onward) routes its API calls through this. */
suspend fun <T> consoleApiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val detail = e.response()?.errorBody()?.string()?.let { body ->
            runCatching { Json.decodeFromString(ApiErrorBodyDto.serializer(), body).detail }.getOrNull()
        }
        throw ConsoleApiException(httpStatusCode = e.code(), detail = detail)
    }
}
