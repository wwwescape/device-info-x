package com.wwwescape.deviceinfox.console.data.network

import java.io.IOException
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Retrofit is built once, against [ConsoleServerConfig.PLACEHOLDER_BASE_URL] — this
 * interceptor is what actually routes every outgoing request to whatever server is currently
 * configured, read fresh (and synchronously — see [ConsoleServerConfig]'s doc comment) on every
 * call. Preserves the original request's path/query, only replacing scheme/host/port/any
 * base-path prefix the configured URL itself has (e.g. a reverse-proxied subpath). */
class BaseUrlInterceptor @Inject constructor(
    private val serverConfig: ConsoleServerConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp interceptors must only ever throw IOException — anything else (e.g. the
        // IllegalArgumentException toHttpUrl() throws for a malformed address like
        // "192.168.1.50:8080" with no scheme) escapes uncaught on OkHttp's dispatcher thread and
        // kills the whole process, bypassing every runCatching this app wraps its network calls
        // in. Wrapping it here converts a malformed/unconfigured server address into a normal
        // caught failure (surfaced as a generic error in AccountSetupDialog etc.) instead.
        val configuredUrl = try {
            val configured = serverConfig.baseUrl.value ?: throw ConsoleServerNotConfiguredException()
            configured.toHttpUrl()
        } catch (e: ConsoleServerNotConfiguredException) {
            throw IOException(e.message, e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid server address", e)
        }
        val original = chain.request()

        val rewritten = configuredUrl.newBuilder()
            .encodedPath(configuredUrl.encodedPath.trimEnd('/') + original.url.encodedPath)
            .encodedQuery(original.url.encodedQuery)
            .fragment(original.url.fragment)
            .build()

        return chain.proceed(original.newBuilder().url(rewritten).build())
    }
}
