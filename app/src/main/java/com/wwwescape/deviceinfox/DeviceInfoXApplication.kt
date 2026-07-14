package com.wwwescape.deviceinfox

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class DeviceInfoXApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var channelManager: ConsolePushChannelManager

    // The same authenticated (Bearer token + BaseUrlInterceptor host-rewriting) client every
    // Retrofit API call already uses — needed here because the GIF picker is the first feature to
    // load a live network image (this server's own `/gifs/proxy` route) via a plain `AsyncImage`
    // rather than a pre-downloaded local file, and that route requires pairing/auth like any other
    // endpoint. Every other `AsyncImage` in the app renders a local file path, which never touches
    // OkHttp at all, so wiring this in changes nothing for them.
    @Inject lateinit var okHttpClient: OkHttpClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Created unconditionally at startup (matching the OS's own recommendation — cheap and
        // idempotent if already up to date) rather than lazily right before the first
        // notification, at whichever NotificationImportance/NotificationSoundTone was last
        // persisted (HIGH/DEFAULT for a fresh install). The channels' name/description (visible
        // in system Settings) stay neutral regardless of NotificationTier — that's where GENERIC
        // vs DISGUISED wording actually differs, not here.
        scope.launch { channelManager.syncChannels() }
    }

    /** Coil's own singleton `ImageLoader` (what every plain `AsyncImage` uses) checks whether the
     * `Application` implements this before falling back to a bare default — without it, GIF bytes
     * (a sent Klipy GIF, or any future one) would just decode as a frozen first frame. Mime-driven,
     * not tied to any particular message type: any `image/gif` content animates automatically
     * wherever `AsyncImage` already renders it today. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttpClient }
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
