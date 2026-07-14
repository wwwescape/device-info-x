package com.wwwescape.deviceinfox

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import com.wwwescape.deviceinfox.console.push.ConsoleFcmService
import com.wwwescape.deviceinfox.console.push.PushCategory
import com.wwwescape.deviceinfox.console.push.resolveTitleAndBody
import com.wwwescape.deviceinfox.data.notifications.FakeNotificationCardState
import com.wwwescape.deviceinfox.data.settings.AppSettings
import javax.inject.Inject
import com.wwwescape.deviceinfox.data.settings.SettingsRepository
import com.wwwescape.deviceinfox.data.settings.THEME_MODE_PREF_KEY
import com.wwwescape.deviceinfox.data.settings.THEME_PREFS_NAME
import com.wwwescape.deviceinfox.data.settings.ThemeMode
import com.wwwescape.deviceinfox.ui.DeviceInfoXApp
import com.wwwescape.deviceinfox.ui.theme.DeviceInfoXTheme
import com.wwwescape.deviceinfox.widget.EXTRA_DESTINATION_ROUTE
import com.wwwescape.deviceinfox.widget.WidgetUpdater
import com.wwwescape.deviceinfox.widget.scheduleWidgetRefreshWork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Used by [handleNotificationTap] both to reset a tapped category's unseen count (the same
     * way [com.wwwescape.deviceinfox.console.push.NotificationDismissReceiver] resets it on swipe)
     * and to look up the current [com.wwwescape.deviceinfox.console.data.settings.NotificationTier]
     * for regenerating the fake card's wording — this is the decoy activity every push
     * notification's `contentIntent` opens (see [ConsoleFcmService]'s own doc comment on why), so
     * it's the natural place for both. */
    @Inject
    lateinit var consoleSettingsRepository: ConsoleSettingsRepository

    /** Populated by [handleNotificationTap] from whatever [PushCategory] the tapped notification
     * carried — see that function's own doc comment for why tap time, not push-receive time.
     * Also closed by [onStop] the moment this app is backgrounded, same boundary
     * [com.wwwescape.deviceinfox.console.ui.nav.ConsoleActivity] already uses to enforce its own
     * auto-exit. See [FakeNotificationCardState]'s own doc comment for the other two close
     * triggers (its own DISMISS button, and the console being entered). */
    @Inject
    lateinit var fakeNotificationCardState: FakeNotificationCardState

    private val pendingRoute = mutableStateOf<String?>(null)

    /** Runs before any DataStore/Compose code can — reads the synchronous SharedPreferences
     * mirror of [ThemeMode] (see [SettingsRepository.setThemeMode]) and forces the base context's
     * night-mode configuration to match, so the window's pre-Compose theme resolution (and, on
     * API <31 where the splash is drawn in-process, the splash itself) honors the user's explicit
     * Light/Dark choice instead of defaulting to the raw system setting. On API 31+ the native
     * splash is drawn by the system before this process starts, so it can't be reached this way —
     * a brief system-colored flash there is an Android platform constraint, not fixable in-app. */
    override fun attachBaseContext(newBase: Context) {
        val stored = newBase.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(THEME_MODE_PREF_KEY, null)
        val mode = stored?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        val forcedNightBit = when (mode) {
            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
            ThemeMode.SYSTEM -> null
        }
        val context = if (forcedNightBit == null) {
            newBase
        } else {
            val overridden = Configuration(newBase.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or forcedNightBit
            }
            newBase.createConfigurationContext(overridden)
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_DESTINATION_ROUTE)
        handleNotificationTap(intent)
        scheduleWidgetRefreshWork(applicationContext)

        setContent {
            val settings by remember { settingsRepository.settingsFlow }
                .collectAsState(initial = AppSettings())
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            val route by pendingRoute

            DeviceInfoXTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor,
                colorTheme = settings.colorTheme,
                themeContrast = settings.themeContrast,
                pureDark = settings.pureDark,
                absoluteDark = settings.absoluteDark,
            ) {
                DeviceInfoXApp(
                    startDestinationRoute = route,
                    onStartDestinationConsumed = { pendingRoute.value = null },
                )
            }
        }
    }

    /** A widget tap while the app is already running arrives here instead of a fresh [onCreate]
     * — requires `launchMode="singleTop"` on this activity in the manifest. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_DESTINATION_ROUTE)
        handleNotificationTap(intent)
    }

    /** No deep linking exists anywhere in this app — a notification tap only ever opens this
     * decoy activity (see [ConsoleFcmService]'s own doc comment on why), carrying nothing but
     * [ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY]. That category is the *only* signal a tap
     * has to work with, so it's used for two independent things: resetting that category's unseen
     * count (existing behavior, unchanged), and — new — regenerating the Dashboard's fake
     * notification card on the spot via [resolveTitleAndBody], the same pure (category, tier) →
     * (title, body) function [ConsoleFcmService] itself used to word the real notification. This
     * replaces the old push-receive-time capture (`ConsoleFcmService` calling
     * `fakeNotificationCardState.show(...)` directly): that approach depended on this
     * background-woken service's process surviving until the user got around to tapping, which
     * often silently failed. Computing the card here instead needs nothing to survive — by the
     * time this runs, the process is already alive and this activity is already resuming. */
    private fun handleNotificationTap(intent: Intent?) {
        val categoryName = intent?.getStringExtra(ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY) ?: return
        lifecycleScope.launch {
            consoleSettingsRepository.resetNotificationCount(categoryName)
            val category = runCatching { PushCategory.valueOf(categoryName) }.getOrNull() ?: return@launch
            val tier = consoleSettingsRepository.notificationTier.first()
            val (title, body) = resolveTitleAndBody(this@MainActivity, category, tier)
            fakeNotificationCardState.show(title, body)
        }
    }

    /** Widgets otherwise only refresh every ~15 minutes in the background (Android's WorkManager
     * floor) — this covers the common case of switching back to the app (from recents, or a
     * fresh/singleTop launch) so the widgets are current the next time they're glanced at,
     * without needing a persistent foreground service. */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { WidgetUpdater.refreshAll(applicationContext) }
    }

    /** "The app is closed" per the fake card's own spec — onStop rather than onPause, matching
     * ConsoleActivity's own established "onStop is the real exit boundary, a transient overlap
     * like a system dialog only pauses" precedent, so this app backgrounding means the same thing
     * here it already means for the console. */
    override fun onStop() {
        super.onStop()
        fakeNotificationCardState.dismiss()
    }
}
