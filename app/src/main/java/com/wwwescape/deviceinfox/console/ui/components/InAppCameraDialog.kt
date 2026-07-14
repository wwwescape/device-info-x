package com.wwwescape.deviceinfox.console.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

/** A finished capture awaiting Retake/Use on the review step. */
private class CameraCaptureResult(val file: File, val isVideo: Boolean)

/** Full-screen in-app camera, WhatsApp-style: tap the shutter for a photo, hold it to record a
 * video (release to stop), then a Retake/Use review step — the same confirm step the system camera
 * app used to show. Built on CameraX's [LifecycleCameraController] rather than handing off to the
 * system camera app, so the camera lives *inside* `ConsoleActivity`: leaving the app from here
 * stops `ConsoleActivity` and exits the console like anywhere else, with no transient-result
 * carve-out needed (see `ConsoleSessionManager.isExpectingTransientResult`).
 *
 * Shown as a [Dialog], whose window inherits the console's FLAG_SECURE (the default
 * `SecureFlagPolicy.Inherit`), so the viewfinder and review never show up in screenshots/Recents.
 *
 * [onCaptured] gets the captured file (in `cacheDir/camera_captures`, a disposable handoff the
 * caller makes its own copy of) and whether it's a video. CAMERA permission must already be
 * granted before this is shown; RECORD_AUDIO is requested on the first hold-to-record if missing. */
@Composable
fun InAppCameraDialog(
    onDismiss: () -> Unit,
    onCaptured: (file: File, isVideo: Boolean) -> Unit,
) {
    var pending by remember { mutableStateOf<CameraCaptureResult?>(null) }

    fun discardPending() {
        pending?.file?.delete()
        pending = null
    }

    Dialog(
        onDismissRequest = {
            discardPending()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val result = pending
            if (result == null) {
                CameraViewfinder(
                    onClose = onDismiss,
                    onCaptured = { pending = it },
                )
            } else {
                CaptureReview(
                    result = result,
                    onRetake = ::discardPending,
                    onUse = {
                        pending = null
                        onCaptured(result.file, result.isVideo)
                    },
                )
            }
        }
    }
}

private enum class FlashSetting(val mode: Int) {
    OFF(ImageCapture.FLASH_MODE_OFF),
    AUTO(ImageCapture.FLASH_MODE_AUTO),
    ON(ImageCapture.FLASH_MODE_ON),
    ;

    fun next(): FlashSetting = entries[(ordinal + 1) % entries.size]
}

private fun newCaptureFile(context: Context, extension: String): File {
    val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    return File(captureDir, "${UUID.randomUUID()}.$extension")
}

@SuppressLint("MissingPermission") // RECORD_AUDIO is checked right before startRecording.
@Composable
private fun CameraViewfinder(onClose: () -> Unit, onCaptured: (CameraCaptureResult) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureErrorMessage = stringResource(R.string.console_camera_capture_error)
    val openErrorMessage = stringResource(R.string.console_camera_open_error)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        }
    }
    DisposableEffect(lifecycleOwner) {
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (e: IllegalStateException) {
            Toast.makeText(context, openErrorMessage, Toast.LENGTH_LONG).show()
            onClose()
        }
        onDispose { controller.unbind() }
    }

    var flash by remember { mutableStateOf(FlashSetting.OFF) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val isRecording = activeRecording != null

    // Never leave a recording running if the dialog goes away mid-hold (e.g. the console exits).
    DisposableEffect(Unit) { onDispose { activeRecording?.stop() } }

    LaunchedEffect(isRecording) {
        recordingSeconds = 0
        while (isRecording) {
            delay(1_000)
            recordingSeconds++
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Granted or not, the user holds the shutter again to record. */ }

    fun takePhoto() {
        if (isCapturingPhoto || isRecording) return
        isCapturingPhoto = true
        val file = newCaptureFile(context, "jpg")
        controller.imageCaptureFlashMode = flash.mode
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isCapturingPhoto = false
                    onCaptured(CameraCaptureResult(file, isVideo = false))
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturingPhoto = false
                    file.delete()
                    Toast.makeText(context, captureErrorMessage, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    fun startRecording() {
        if (isCapturingPhoto || isRecording) return
        val hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val file = newCaptureFile(context, "mp4")
        activeRecording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(context),
        ) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                // A too-short hold can finalize with ERROR_NO_VALID_DATA — treat any error as "no
                // video" rather than handing a broken file onward.
                if (event.hasError()) {
                    file.delete()
                    Toast.makeText(context, captureErrorMessage, Toast.LENGTH_LONG).show()
                } else {
                    onCaptured(CameraCaptureResult(file, isVideo = true))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                IconButton(onClick = onClose, enabled = !isRecording) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_camera_close), tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isRecording) {
                    val recordingLabel = stringResource(R.string.console_camera_recording)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { contentDescription = recordingLabel },
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "%d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { flash = flash.next() }, enabled = !isRecording) {
                    val (icon, label) = when (flash) {
                        FlashSetting.OFF -> Icons.Rounded.FlashOff to R.string.console_camera_flash_off
                        FlashSetting.AUTO -> Icons.Rounded.FlashAuto to R.string.console_camera_flash_auto
                        FlashSetting.ON -> Icons.Rounded.FlashOn to R.string.console_camera_flash_on
                    }
                    Icon(icon, contentDescription = stringResource(label), tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isRecording) {
                Text(
                    text = stringResource(R.string.console_camera_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                ShutterButton(
                    isRecording = isRecording,
                    contentDescription = stringResource(R.string.console_camera_shutter),
                    onTap = ::takePhoto,
                    onHoldStart = ::startRecording,
                    onHoldEnd = { activeRecording?.stop() },
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = {
                        isFrontCamera = !isFrontCamera
                        controller.cameraSelector =
                            if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    },
                    enabled = !isRecording && !isCapturingPhoto,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                ) {
                    Icon(
                        Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.console_call_room_switch_camera_action),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** White ring that turns into a larger red record button while a hold-to-record is in progress.
 * One gesture detector handles both: a quick tap is a photo, a long-press starts recording and
 * releasing that same press stops it. */
@Composable
private fun ShutterButton(
    isRecording: Boolean,
    contentDescription: String,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size by animateDpAsState(if (isRecording) 92.dp else 76.dp, label = "shutterSize")
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)
    Box(
        modifier = modifier
            .size(size)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (isRecording) Color.Red else Color.White.copy(alpha = 0.85f))
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onLongPress = { currentOnHoldStart() },
                    onPress = {
                        tryAwaitRelease()
                        currentOnHoldEnd()
                    },
                )
            },
    )
}

@Composable
private fun CaptureReview(result: CameraCaptureResult, onRetake: () -> Unit, onUse: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (result.isVideo) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VideoPlayerPage(
                    parentId = result.file.path,
                    initialLocalPath = result.file.path,
                    ensureDownloaded = { result.file.path },
                )
            }
        } else {
            AsyncImage(
                model = result.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            FilledIconButton(
                onClick = onRetake,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.console_camera_retake), tint = Color.White)
            }
            FilledIconButton(onClick = onUse, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.console_camera_use))
            }
        }
    }
}
