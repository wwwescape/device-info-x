package com.wwwescape.deviceinfox.console.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The self-hosted Device Info X Server's base URL, e.g. `https://yourdomain.duckdns.org/`.
 * Set once during first-run account setup (Phase 11.2) — plain (not Keystore-encrypted)
 * `SharedPreferences` because it's a hostname, not a secret, and because
 * [BaseUrlInterceptor] needs to read it synchronously on every request; a `DataStore`/`Flow`
 * read would require suspending inside an OkHttp interceptor, which isn't possible.
 *
 * Retrofit itself is built once (see `NetworkModule`) against an inert placeholder base URL —
 * [BaseUrlInterceptor] is what actually routes every request to the real host, so changing the
 * configured server later never requires rebuilding the Retrofit/OkHttp singletons.
 */
@Singleton
class ConsoleServerConfig @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_BASE_URL, null))

    /** Null until the user has completed first-run server setup. */
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    fun setBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        prefs.edit { putString(KEY_BASE_URL, normalized) }
        _baseUrl.value = normalized
    }

    fun clear() {
        prefs.edit { remove(KEY_BASE_URL) }
        _baseUrl.value = null
    }

    companion object {
        const val PREFS_NAME = "console_server_config"
        private const val KEY_BASE_URL = "base_url"

        /** Retrofit needs *some* syntactically valid URL to build against before the user has
         * configured a real one — bakes in the `api/v1/` prefix so every `@GET`/`@POST`
         * endpoint annotation only needs its relative sub-path (e.g. `"auth/login"`, never a
         * leading `/`, so Retrofit resolves it against this trailing path rather than
         * replacing it). [BaseUrlInterceptor] swaps in the real scheme/host/port (and any base
         * path the configured URL itself has, e.g. a reverse-proxied subpath) on every request,
         * preserving this `api/v1/...` path unchanged. */
        const val PLACEHOLDER_BASE_URL = "https://unconfigured.invalid/api/v1/"
    }
}
