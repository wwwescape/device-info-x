package com.wwwescape.deviceinfox.console.data.network

import kotlinx.serialization.Serializable

/** Every error the server returns is `{"detail": "..."}` — its single `DomainError` FastAPI
 * exception handler (`app/main.py`) always shapes it that way, see `app/core/exceptions.py`. */
@Serializable
data class ApiErrorBodyDto(val detail: String? = null)

/** Thrown by [ConsoleApiCall.execute] for any non-2xx response — [httpStatusCode] lets callers
 * branch on the same status-code taxonomy the server's `DomainError` subclasses use (404/409/
 * 403/401/422) without needing to know their names. */
class ConsoleApiException(
    val httpStatusCode: Int,
    val detail: String?,
) : Exception(detail ?: "Request failed with HTTP $httpStatusCode")

/** [ConsoleServerConfig.baseUrl] is still null — no server has been configured yet (first-run
 * setup, Phase 11.2, hasn't completed). */
class ConsoleServerNotConfiguredException : Exception("No Device Info X Server configured yet")
