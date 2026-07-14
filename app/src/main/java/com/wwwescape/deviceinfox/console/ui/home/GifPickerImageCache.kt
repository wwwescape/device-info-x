package com.wwwescape.deviceinfox.console.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

private const val MAX_CACHE_BYTES = 24L * 1024 * 1024

/** Escape hatch for grabbing the app's shared, authenticated `OkHttpClient` (same pairing/auth
 * setup [DeviceInfoXApplication]'s Coil singleton already wraps) from inside a plain
 * `@Composable` — see `FeatureTourCoordinatorEntryPoint` for the same pattern. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GifPickerOkHttpEntryPoint {
    fun okHttpClient(): OkHttpClient
}

private fun Context.gifPickerOkHttpClient(): OkHttpClient =
    EntryPointAccessors.fromApplication(applicationContext, GifPickerOkHttpEntryPoint::class.java)
        .okHttpClient()

/** Every GIF the grid shows comes through this server's short-lived signed `/gifs/proxy` link
 * (see [GifPickerPanel]'s `resolveProxyUrl` doc comment), and that response carries no HTTP
 * caching headers at all — deliberately, since the whole point of the proxy is that the client
 * never gets a direct, cacheable line to Klipy's CDN. On top of that, Coil's memory cache only
 * ever stores plain `Bitmap`s; the animated `Drawable` these GIFs actually decode into
 * ([ImageDecoderDecoder]/[GifDecoder]'s output) is never written to it. Put together, scrolling a
 * thumbnail off-screen and back re-runs the *entire* fetch — proxy round trip to Klipy and
 * all — every single time, which is what made the grid feel slow and "forgetful" while scrolling.
 *
 * This interceptor buffers the raw response bytes in memory, keyed by request URL, so a
 * previously-seen thumbnail reloads instantly from RAM instead of the network — without ever
 * touching disk. It's built fresh per picker session by [rememberGifPickerImageLoader] and never
 * installed anywhere the app-wide singleton `ImageLoader` can see it, so nothing here outlives the
 * picker being open: closing it drops the last reference and the cached bytes are GC'd with it. */
private class InMemoryGifResponseCache : Interceptor {
    private data class CachedResponse(val bytes: ByteArray, val contentType: String?)

    private val lock = Any()
    private var totalBytes = 0L
    private val entries = object : LinkedHashMap<String, CachedResponse>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>): Boolean {
            if (totalBytes <= MAX_CACHE_BYTES) return false
            totalBytes -= eldest.value.bytes.size
            return true
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.toString()

        synchronized(lock) { entries[key] }?.let { cached ->
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (picker-session cache)")
                .body(cached.bytes.toResponseBody(cached.contentType?.toMediaTypeOrNull()))
                .build()
        }

        val response = chain.proceed(request)
        val body = response.body
        if (!response.isSuccessful || body == null) return response

        val bytes = body.bytes()
        val contentType = body.contentType()
        synchronized(lock) {
            entries[key] = CachedResponse(bytes, contentType?.toString())
            totalBytes += bytes.size
        }
        return response.newBuilder()
            .body(bytes.toResponseBody(contentType))
            .build()
    }
}

/** Scoped, throwaway `ImageLoader` for [GifPickerPanel]'s grid only — same GIF decoder wiring as
 * the app-wide singleton ([DeviceInfoXApplication.newImageLoader]) plus [InMemoryGifResponseCache].
 * `remember`ed with no keys, so it lives exactly as long as the picker composable does; shut down
 * on dispose to release its own memory cache/dispatcher promptly rather than waiting on GC. */
@Composable
fun rememberGifPickerImageLoader(): ImageLoader {
    val context = LocalContext.current
    val imageLoader = remember {
        val cachingClient = context.gifPickerOkHttpClient().newBuilder()
            .addInterceptor(InMemoryGifResponseCache())
            .build()
        ImageLoader.Builder(context)
            .okHttpClient { cachingClient }
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    DisposableEffect(imageLoader) {
        onDispose { imageLoader.shutdown() }
    }
    return imageLoader
}
