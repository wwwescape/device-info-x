package com.wwwescape.deviceinfox.console.data.network

import javax.inject.Inject
import javax.inject.Singleton

/** Backs Settings' "Server status" row. Deliberately not wrapped in [consoleApiCall] — that only
 * exists to translate a non-2xx response into [ConsoleApiException] with the server's `detail`
 * message, which nothing here needs; a plain network failure (timeout, DNS, connection refused)
 * must count as "offline" exactly the same as any other failure, so a bare [runCatching] around
 * the raw call is the right amount of handling. */
@Singleton
class ServerStatusRepository @Inject constructor(
    private val healthApi: HealthApi,
) {
    suspend fun isOnline(): Boolean = runCatching { healthApi.ready() }.isSuccess
}
