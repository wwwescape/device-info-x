package com.wwwescape.deviceinfox.console.data.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** One event off the console's realtime connection — `data` is left as an untyped [JsonObject]
 * (mirroring the server's own `ClientEnvelope`/`build_envelope`, see `app/ws/protocol.py`) since
 * every event type has a different shape; callers decode the fields they need. */
data class WsEvent(val type: String, val data: JsonObject)

/**
 * The console's single WebSocket connection to `/ws` — bare, not under the `api/v1/` prefix
 * (see `app/main.py`'s router mounting), so this doesn't go through [BaseUrlInterceptor]/
 * [NetworkModule]'s Retrofit-oriented `OkHttpClient`; it builds its own minimal client and
 * request instead.
 *
 * Per the Phase 11 design, [connect] is only ever called while `ConsoleActivity` is foregrounded
 * and the session is authenticated + paired (see `ConsoleActivity`) — there's no foreground
 * service keeping this alive in the background, matching the console's existing "nothing runs
 * once it's not open" philosophy from Phase 3. A dropped connection retries on a fixed delay
 * (not exponential backoff — this connection's whole lifetime is a few minutes at most, so the
 * extra complexity isn't worth it) for as long as [connect] hasn't been followed by [disconnect].
 */
@Singleton
class ConsoleWebSocketClient @Inject constructor(
    private val tokenStore: ConsoleAuthTokenStore,
    private val serverConfig: ConsoleServerConfig,
    private val authAuthenticator: AuthAuthenticator,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    /** Fires every time the socket finishes a handshake — including every reconnect, not just
     * the first. This is what lets [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository]
     * (and anything else with the same gap) catch up via REST on whatever was missed while
     * disconnected: a message sent while this device's console was closed arrives over neither
     * channel otherwise — not [events] (nothing was connected to receive it) and not any one-time
     * startup sync (this device's connection could have dropped and reconnected any number of
     * times since). */
    private val _connected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connected: SharedFlow<Unit> = _connected.asSharedFlow()

    fun connect() {
        shouldStayConnected = true
        connectInternal()
    }

    fun disconnect() {
        shouldStayConnected = false
        reconnectJob?.cancel()
        stopHeartbeat()
        webSocket?.close(NORMAL_CLOSURE_CODE, null)
        webSocket = null
    }

    fun send(type: String, data: JsonObject = JsonObject(emptyMap())) {
        val envelope = JsonObject(mapOf("type" to JsonPrimitive(type), "data" to data))
        webSocket?.send(json.encodeToString(JsonObject.serializer(), envelope))
    }

    private fun connectInternal() {
        if (webSocket != null) return
        val baseUrl = serverConfig.baseUrl.value ?: return
        val accessToken = tokenStore.accessToken ?: return

        val request = Request.Builder()
            .url(toWebSocketUrl(baseUrl))
            .header("Authorization", "Bearer $accessToken")
            .build()
        webSocket = client.newWebSocket(request, listener)
    }

    private fun toWebSocketUrl(httpBaseUrl: String): String {
        val httpUrl = httpBaseUrl.toHttpUrlOrNull() ?: return httpBaseUrl
        val scheme = if (httpUrl.isHttps) "wss" else "ws"
        val port = if ((httpUrl.isHttps && httpUrl.port == 443) || (!httpUrl.isHttps && httpUrl.port == 80)) {
            ""
        } else {
            ":${httpUrl.port}"
        }
        return "$scheme://${httpUrl.host}$port/ws"
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                send("ping")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MILLIS)
            if (shouldStayConnected) connectInternal()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            startHeartbeat()
            _connected.tryEmit(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val envelope = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
            val type = envelope["type"]?.jsonPrimitive?.content ?: return
            if (type == "pong") return
            val data = envelope["data"] as? JsonObject ?: JsonObject(emptyMap())
            _events.tryEmit(WsEvent(type, data))
        }

        /** A 403 here means the handshake itself was rejected — server-side auth failed before
         * `websocket.accept()` ever ran, almost always because the cached access token expired.
         * That never reaches OkHttp's [okhttp3.Authenticator] hook (401-only), so without an
         * explicit refresh here [scheduleReconnect] would just keep retrying with the exact same
         * stale token every 5 seconds, forever. */
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopHeartbeat()
            this@ConsoleWebSocketClient.webSocket = null
            if (response?.code == 403) {
                scope.launch { authAuthenticator.refreshTokenBlocking() }
            }
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopHeartbeat()
            this@ConsoleWebSocketClient.webSocket = null
            if (code != NORMAL_CLOSURE_CODE) scheduleReconnect()
        }
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MILLIS = 30_000L
        const val RECONNECT_DELAY_MILLIS = 5_000L
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
