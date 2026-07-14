package com.wwwescape.deviceinfox.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Refreshes every placed widget with a fresh snapshot; called when the app is opened. */
object WidgetUpdater {
    suspend fun refreshAll(context: Context) {
        DeviceInfoXWidget().updateAll(context)
    }
}
