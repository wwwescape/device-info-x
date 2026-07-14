package com.wwwescape.deviceinfox.data.deviceos

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reads device/OS identity from [android.os.Build]. These fields never change during a
 * process lifetime, so they're collected once rather than exposed as a Flow.
 */
@Singleton
class DeviceOsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    // DEPRECATION: Build.getRadioVersion() has no non-deprecated replacement.
    // HardwareIds: ANDROID_ID is read only to display it back to the user on the Device & OS
    // screen (see the Privacy Policy linked from Settings) — never for tracking, analytics,
    // or transmitted anywhere.
    @Suppress("DEPRECATION", "HardwareIds")
    fun collectStatic(): DeviceOsInfo = DeviceOsInfo(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        model = Build.MODEL,
        marketingName = DeviceNameResolver.marketingNameFor(context, Build.MODEL),
        codename = Build.DEVICE,
        board = Build.BOARD,
        hardware = Build.HARDWARE,
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        buildId = Build.ID,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        kernelVersion = System.getProperty("os.version") ?: "Unknown",
        bootloader = Build.BOOTLOADER,
        androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown",
        baseband = Build.getRadioVersion(),
    )

    /** Emits milliseconds since boot, ticking once a second while collected. */
    fun uptimeMillis(): Flow<Long> = flow {
        while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(1_000)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeviceOsRepositoryEntryPoint {
    fun deviceOsRepository(): DeviceOsRepository
}

fun Context.deviceOsRepositoryEntryPoint(): DeviceOsRepository =
    EntryPointAccessors.fromApplication(applicationContext, DeviceOsRepositoryEntryPoint::class.java)
        .deviceOsRepository()
