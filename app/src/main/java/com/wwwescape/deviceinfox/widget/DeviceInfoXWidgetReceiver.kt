package com.wwwescape.deviceinfox.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class DeviceInfoXWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeviceInfoXWidget()

    /** First widget instance placed. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleWidgetRefreshWork(context)
    }

    /** Last widget instance removed — nothing left to keep fresh. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelWidgetRefreshWork(context)
    }
}
