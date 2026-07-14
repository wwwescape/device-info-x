package com.wwwescape.deviceinfox.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Refreshes all three widgets in the background. Android's `updatePeriodMillis` (declared in
 * each widget's XML) has a hard 30-minute floor the OS enforces regardless of what's set there;
 * WorkManager's own periodic floor is 15 minutes, so this is the fastest a plain background app
 * (no foreground service) can keep widget data from drifting stale between app opens. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }
}

private const val WIDGET_REFRESH_WORK_NAME = "widget_periodic_refresh"

/** Idempotent — safe to call on every app launch; [ExistingPeriodicWorkPolicy.KEEP] leaves an
 * already-scheduled job alone rather than restarting its interval. */
fun scheduleWidgetRefreshWork(context: Context) {
    val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WIDGET_REFRESH_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
