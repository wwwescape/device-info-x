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

/** The `content://` [Uri] handed onward for an in-app capture — the same FileProvider shape the
 * system camera handoff used to produce, so every caller's existing import/attach step (which
 * makes its own permanent copy) works unchanged. `camera_captures` is already declared in
 * `file_paths.xml`. */
private fun captureFileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Launch callbacks returned by [rememberCameraCapture] — each already does its own runtime
 * permission gating before actually opening the camera. */
class CameraCaptureLaunchers(val launchCamera: () -> Unit, val launchVideoCamera: () -> Unit)

/** Shared camera capture flow — originally built inline for Messages' `AttachPanel`
 * (`HomeScreen.kt`'s `ComposerBar`), pulled out here once Safe Locker's FAB Add menu needed the
 * identical launcher/permission dance a second time rather than copy-pasting it.
 *
 * Opens [InAppCameraDialog] (tap for photo, hold for video) rather than the system camera app, so
 * capture never leaves `ConsoleActivity` — there's no transient-result window to bracket any more,
 * and leaving the app mid-capture exits the console like anywhere else. Both launchers open the
 * same camera; [CameraCaptureLaunchers.launchVideoCamera] just asks for RECORD_AUDIO up front too,
 * since the user clearly intends to record (the camera itself also asks on the first hold). */
@Composable
fun rememberCameraCapture(
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
): CameraCaptureLaunchers {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        InAppCameraDialog(
            onDismiss = { showCamera = false },
            onCaptured = { file, isVideo ->
                showCamera = false
                val uri = captureFileUri(context, file)
                if (isVideo) onVideoCaptured(uri) else onImageCaptured(uri)
            },
        )
    }

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showCamera = true }

    // Opens the camera regardless of the audio answer — photos still work without it, and the
    // camera asks again on the first hold-to-record.
    val recordAudioForVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { showCamera = true }
    val cameraForVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                showCamera = true
            } else {
                recordAudioForVideoPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    return CameraCaptureLaunchers(
        launchCamera = {
            if (hasPermission(Manifest.permission.CAMERA)) {
                showCamera = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        launchVideoCamera = {
            when {
                !hasPermission(Manifest.permission.CAMERA) -> cameraForVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
                !hasPermission(Manifest.permission.RECORD_AUDIO) -> recordAudioForVideoPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else -> showCamera = true
            }
        },
    )
}
