package com.wwwescape.deviceinfox

import android.app.Application
import com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class DeviceInfoXApplication : Application() {

    @Inject lateinit var channelManager: ConsolePushChannelManager

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
}
