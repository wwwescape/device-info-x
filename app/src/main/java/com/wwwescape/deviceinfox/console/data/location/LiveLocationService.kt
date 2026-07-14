package com.wwwescape.deviceinfox.console.data.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wwwescape.deviceinfox.MainActivity
import com.wwwescape.deviceinfox.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground service for Live Location Sharing — the one piece of this feature that keeps
 * running while the console itself is backgrounded, deliberately outside
 * [com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient]'s own "nothing runs
 * once it's not open" design (see that class's doc comment): position updates go out over plain
 * REST ([LiveLocationRepository.pushLocationUpdate]), which works regardless of whether the app
 * happens to be foregrounded or the WebSocket is connected right now.
 *
 * Started/stopped via [start]/[stop] from the map screen's own toggle, and stops itself early if
 * [LiveLocationRepository.sharingEndedEvent] fires (the partner disabled it, or the server's own
 * 2-hour sweep did) or this device's own independent 2-hour timer elapses first — the server
 * sweep (`location_service.AUTO_DISABLE_AFTER`) is the authoritative backstop; this is just the
 * fast local path so the notification/UI react instantly instead of waiting on its 60s cadence. */
@AndroidEntryPoint
class LiveLocationService : Service() {

    @Inject lateinit var repository: LiveLocationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationCallback: LocationCallback? = null
    private var watchdogJob: Job? = null

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )

        startLocationUpdates()
        watchForEnd()
        scheduleCheckInReminders()

        // Not START_STICKY — if the process is killed, the whole point (a live, continuously
        // updating share) is already broken; the OS restarting this service with no fresh
        // location request already in flight would just relaunch a silent, stale foreground
        // notification. Re-enabling from the map screen is the correct recovery, same as any
        // other explicit user-initiated session.
        return START_NOT_STICKY
    }

    private fun startLocationUpdates() {
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFinePermission) {
            // Shouldn't happen — the map screen's toggle only calls [start] after confirming both
            // permissions are granted — but a defensive bail-out beats a SecurityException crash
            // if something (a user revoking the permission mid-session from system Settings)
            // invalidates that assumption later.
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MILLIS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val accuracyM = if (location.hasAccuracy()) location.accuracy else null
                // Set locally first, synchronously — this device's own map marker/ETA math
                // shouldn't wait on a network round-trip for a value it already has.
                repository.updateSelfLocationLocally(location.latitude, location.longitude, accuracyM)
                scope.launch {
                    runCatching {
                        repository.pushLocationUpdate(lat = location.latitude, lng = location.longitude, accuracyM = accuracyM)
                    }
                }
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun watchForEnd() {
        scope.launch {
            repository.sharingEndedEvent.collect { stopSelf() }
        }
        watchdogJob = scope.launch {
            delay(MAX_SHARE_DURATION_MILLIS)
            if (isActive) {
                runCatching { repository.disableSharing() }
                stopSelf()
            }
        }
    }

    /** Fires at [CHECK_IN_START_MILLIS], then every [CHECK_IN_INTERVAL_MILLIS] after that, until
     * [watchForEnd]'s own 2-hour watchdog stops this whole service (which cancels [scope] and
     * everything running in it, including this loop — no separate cancellation needed). Both
     * halves of the "reminder" always fire together: [LiveLocationRepository.emitCheckInReminder]
     * for a screen that's currently open to react to, and [showCheckInReminderNotification]
     * unconditionally, since this device's console might not be foregrounded at all right now. */
    private fun scheduleCheckInReminders() {
        scope.launch {
            delay(CHECK_IN_START_MILLIS)
            while (isActive) {
                repository.emitCheckInReminder()
                showCheckInReminderNotification()
                delay(CHECK_IN_INTERVAL_MILLIS)
            }
        }
    }

    private fun showCheckInReminderNotification() {
        val hasPostPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPostPermission) return

        val disableIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.console_live_location_checkin_title))
            .setContentText(getString(R.string.console_live_location_checkin_body))
            .setAutoCancel(true)
            .addAction(0, getString(R.string.console_live_location_checkin_disable_action), disableIntent)
            .build()
        // Distinct id from NOTIFICATION_ID — this is a separate, dismissible nudge, not an update
        // to the ongoing (setOngoing(true), non-dismissible) foreground-service notification.
        runCatching { NotificationManagerCompat.from(this).notify(CHECKIN_NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.console_live_location_notification_title))
            .setContentText(getString(R.string.console_live_location_notification_body))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.console_live_location_notification_stop_action), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW — an ongoing foreground-service notification, not something that should
        // interrupt with a sound/heads-up popup every time it updates (it doesn't visibly change
        // per-update anyway; only its presence/absence is the signal).
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.console_live_location_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        watchdogJob?.cancel()
        scope.cancel()
        NotificationManagerCompat.from(this).cancel(CHECKIN_NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "live_location_sharing"
        private const val NOTIFICATION_ID = 42_001
        private const val CHECKIN_NOTIFICATION_ID = 42_002
        private const val ACTION_STOP = "com.wwwescape.deviceinfox.console.location.STOP"

        // Real-time enough for a "watch them approach on the map" experience without being
        // wasteful — this app's whole live-location usage is rare, short sessions (per the
        // TODOS.md design discussion), so battery cost at this cadence is a non-issue in practice.
        private const val LOCATION_INTERVAL_MILLIS = 15_000L
        private const val LOCATION_MIN_INTERVAL_MILLIS = 10_000L

        private val MAX_SHARE_DURATION_MILLIS = TimeUnit.HOURS.toMillis(2)
        private val CHECK_IN_START_MILLIS = TimeUnit.MINUTES.toMillis(30)
        private val CHECK_IN_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(15)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveLocationService::class.java))
        }
    }
}
