package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.RefreshRequestDto
import com.wwwescape.deviceinfox.console.data.network.dto.TokenPairDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401, silently refreshes the access token and retries once — transparent to every
 * repository and to the UI, per the Phase 11 design (PIN gates the console; the server session
 * underneath renews itself for as long as the refresh token stays valid).
 *
 * Calls `/auth/refresh` through a dedicated, `Authenticator`-free [OkHttpClient] — reusing the
 * main client here would recurse into this same `Authenticator`. Still goes through
 * [BaseUrlInterceptor] so it targets whatever server is actually configured.
 */
@Singleton
class AuthAuthenticator @Inject constructor(
    private val tokenStore: ConsoleAuthTokenStore,
    baseUrlInterceptor: BaseUrlInterceptor,
    private val json: Json,
) : Authenticator {

    private val refreshClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) > 1) return null // already retried once — give up

        val failedAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val accessToken = refreshIfStillStale(failedAccessToken) ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
    }

    /** Force-refreshes outside the normal 401-triggered flow — [ConsoleWebSocketClient] uses this
     * when a `/ws` handshake itself gets rejected (a 403 on the upgrade request, since an expired
     * access token fails before `websocket.accept()` server-side — see `app/ws/router.py`'s
     * `_authenticate`). That rejection never reaches OkHttp's [authenticate] hook at all (it only
     * fires on 401), so without this, a stale token sat unrefreshed until some unrelated REST call
     * happened to trigger a 401 — which could be never, if the console is just idling on a screen
     * that only ever talks over the WebSocket (e.g. typing indicators).
     *
     * Routes through the exact same [refreshIfStillStale] choke point [authenticate] uses —
     * calling [refreshSynchronously] directly here (an earlier version of this function did)
     * let this race a concurrent REST-triggered refresh: both would read the same not-yet-rotated
     * refresh token, one would win, and the loser's 401 would call [refreshSynchronously]'s
     * `tokenStore.clear()`, wiping out the winner's freshly stored tokens and killing the session
     * a moment after it was successfully renewed. */
    fun refreshTokenBlocking(): String? = refreshIfStillStale(tokenStore.accessToken)

    /** Single choke point for every refresh, REST- or WS-triggered — `synchronized(this)` means
     * only one actual `/auth/refresh` call is ever in flight at a time; every other caller that
     * shows up while one is running just waits for the lock and then reuses whatever token it
     * produced, rather than racing it with a second call against the same (about-to-be-rotated)
     * refresh token. */
    private fun refreshIfStillStale(knownStaleAccessToken: String?): String? = synchronized(this) {
        val current = tokenStore.accessToken
        if (current != null && current != knownStaleAccessToken) {
            // Someone else already refreshed while we were waiting for the lock.
            return@synchronized current
        }
        val refreshToken = tokenStore.refreshToken ?: return@synchronized null
        refreshSynchronously(refreshToken)
    }

    /** Returns the new access token, having already persisted both tokens — or null if the
     * refresh itself failed (refresh token expired/revoked), in which case the stored session
     * is cleared so the app falls back to prompting for account login again. Only ever called
     * from inside [refreshIfStillStale]'s lock — never call this directly. */
    private fun refreshSynchronously(refreshToken: String): String? {
        val requestBody = json.encodeToString(RefreshRequestDto.serializer(), RefreshRequestDto(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ConsoleServerConfig.PLACEHOLDER_BASE_URL + "auth/refresh")
            .post(requestBody)
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    tokenStore.clear()
                    return null
                }
                val body = resp.body?.string() ?: return null
                val tokenPair = json.decodeFromString(TokenPairDto.serializer(), body)
                tokenStore.updateAccessToken(tokenPair.accessToken, tokenPair.refreshToken)
                tokenPair.accessToken
            }
        }.getOrNull()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
