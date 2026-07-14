package com.wwwescape.deviceinfox.data.display

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import com.wwwescape.deviceinfox.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class DisplayRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun collectStatic(): DisplayInfo {
        val windowManager = context.getSystemService(WindowManager::class.java)

        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi

        return DisplayInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            densityBucket = densityBucketName(metrics.densityDpi),
            refreshRateHz = display.refreshRate,
            supportedRefreshRatesHz = display.supportedModes.map { it.refreshRate }.distinct().sorted(),
            screenSizeInches = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble()),
            hdrTypes = display.hdrCapabilities?.supportedHdrTypes?.map { hdrTypeName(context, it) }.orEmpty(),
            isWideColorGamut = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && display.isWideColorGamut,
        )
    }

    /** Reads cutout/notch geometry from the current View's window insets. */
    fun cutoutInfo(insets: WindowInsetsCompat?): CutoutInfo {
        val cutout = insets?.displayCutout
        return CutoutInfo(
            isPresent = cutout != null,
            safeInsetTopPx = cutout?.safeInsetTop ?: 0,
            safeInsetBottomPx = cutout?.safeInsetBottom ?: 0,
            safeInsetLeftPx = cutout?.safeInsetLeft ?: 0,
            safeInsetRightPx = cutout?.safeInsetRight ?: 0,
        )
    }

    private fun densityBucketName(densityDpi: Int): String = when {
        densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
        densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
        densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
        densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
        densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
        else -> "xxxhdpi"
    }

    /** Dolby Vision/HDR10/HLG/HDR10+ are fixed brand/standard names — never translated. */
    private fun hdrTypeName(context: Context, type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> context.getString(R.string.value_unknown)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DisplayRepositoryEntryPoint {
    fun displayRepository(): DisplayRepository
}

fun Context.displayRepositoryEntryPoint(): DisplayRepository =
    EntryPointAccessors.fromApplication(applicationContext, DisplayRepositoryEntryPoint::class.java)
        .displayRepository()
