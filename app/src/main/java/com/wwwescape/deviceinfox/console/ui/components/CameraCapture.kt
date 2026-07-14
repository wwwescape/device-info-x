package com.wwwescape.deviceinfox.console.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** A fresh, writable `content://` [Uri] for `ActivityResultContracts.TakePicture()` to capture
 * into — the system Camera app writes JPEG bytes to whatever Uri it's handed, it doesn't return
 * one itself, unlike the Gallery picker. Named with a `.jpg` extension so [FileProvider]'s own
 * extension-based mime lookup resolves to `image/jpeg` rather than falling through to a default.
 * Written to the cache dir, not private files storage — this is a disposable capture handoff, not
 * where the image actually lives; the caller's own import/attach step makes the real copy. */
private fun createCameraCaptureUri(context: Context): Uri {
    val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(captureDir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Same reasoning as [createCameraCaptureUri] — `ActivityResultContracts.CaptureVideo()` needs a
 * writable destination Uri handed to it, just with a `.mp4` extension. Reuses the same
 * `camera_captures` cache subdir/`file_paths.xml` entry — [FileProvider]'s path matching is
 * prefix-based on the declared subdir, not per-extension, so no separate provider path is needed
 * for video. */
private fun createVideoCaptureUri(context: Context): Uri {
    val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File(captureDir, "${UUID.randomUUID()}.mp4")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Launch callbacks returned by [rememberCameraCapture] — each already does its own runtime
 * permission gating before actually opening the system camera/video-recorder. */
class CameraCaptureLaunchers(val launchCamera: () -> Unit, val launchVideoCamera: () -> Unit)

/** Shared system-camera capture flow — originally built inline for Messages' `AttachPanel`
 * (`HomeScreen.kt`'s `ComposerBar`), pulled out here once Safe Locker's FAB Add menu needed the
 * identical launcher/permission dance a second time rather than copy-pasting it. [onPickerLaunch]/
 * [onPickerResult] bracket the transient-result window the same way every other system-picker
 * launch in this app already does (see `ConsoleSessionManager.isExpectingTransientResult`). */
@Composable
fun rememberCameraCapture(
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
): CameraCaptureLaunchers {
    val context = LocalContext.current

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        onPickerResult()
        if (success) pendingImageUri?.let(onImageCaptured)
        pendingImageUri = null
    }
    fun launchCameraNow() {
        val uri = createCameraCaptureUri(context)
        pendingImageUri = uri
        onPickerLaunch()
        takePictureLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCameraNow() }

    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success ->
        onPickerResult()
        if (success) pendingVideoUri?.let(onVideoCaptured)
        pendingVideoUri = null
    }
    fun launchVideoCameraNow() {
        val uri = createVideoCaptureUri(context)
        pendingVideoUri = uri
        onPickerLaunch()
        captureVideoLauncher.launch(uri)
    }
    // Video capture needs both permissions before it can launch — the system Camera app records
    // audio by default, so a video taken without RECORD_AUDIO granted would silently come back
    // muted (or fail outright), unlike TakePicture()'s single-permission CAMERA-only case above.
    val recordAudioForVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchVideoCameraNow() }
    val cameraForVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasAudioPermission) {
                launchVideoCameraNow()
            } else {
                recordAudioForVideoPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    return CameraCaptureLaunchers(
        launchCamera = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) launchCameraNow() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        launchVideoCamera = {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            when {
                !hasCameraPermission -> cameraForVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
                !hasAudioPermission -> recordAudioForVideoPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else -> launchVideoCameraNow()
            }
        },
    )
}
