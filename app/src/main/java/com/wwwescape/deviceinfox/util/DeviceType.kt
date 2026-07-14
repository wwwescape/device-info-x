package com.wwwescape.deviceinfox.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** True on Android TV / Google TV devices (leanback UI mode or the leanback system feature). */
fun Context.isTelevision(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** True on tablets: a smallest screen width of 600dp or more. Foldable phones cross that line
 * when unfolded, so a device with a hinge sensor is always treated as a phone. */
fun Context.isTablet(): Boolean =
    resources.configuration.smallestScreenWidthDp >= 600 &&
        !packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

/** The private console is phone-only: never on tablets or TVs (and the app doesn't run on watches). */
fun Context.isConsoleSupported(): Boolean = !isTelevision() && !isTablet()
