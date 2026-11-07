package dev.toufik.trailmark.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A camera inside the app, so a photo or a clip can be taken in the middle of a
 * run without handing the session to another application and hoping it comes
 * back. The file lands in the app's private media folder and is attached to the
 * activity at the moment of the tap.
 */
@Composable
internal fun CameraCaptureScreen(
    title: String,
    photoLabel: String,
    videoLabel: String,
    recordingLabel: String,
    closeLabel: String,
    permissionLabel: String,
    errorLabel: String,
    startInVideo: Boolean = false,
    onCaptured: (File, String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    BackHandler(onBack = onClose)

    if (!granted) {
        Surface(color = Color(0xFF0E1113), modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 16.sp)
                Text(permissionLabel, color = Color(0xFFAEB6BF), fontSize = 13.sp)
                TextButton(onClick = onClose) { Text(closeLabel, color = Color(0xFFAEB6BF)) }
            }
        }
        return
    }

    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val recorder = remember {
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var videoMode by remember { mutableStateOf(startInVideo) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(owner, previewView) {
        val provider = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { if (cont.isActive) cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val bound = runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, videoCapture)
        }
        if (bound.isFailure) failed = true
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { recording?.stop() } }
    }

    fun takePhoto() {
        failed = false
        val file = MediaCapture.newPhotoFile(context)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onCaptured(file, "PHOTO")
                }

                override fun onError(exception: ImageCaptureException) {
                    runCatching { file.delete() }
                    failed = true
                }
            },
        )
    }

    fun startVideo() {
        failed = false
        val file = MediaCapture.newVideoFile(context)
        recordingFile = file
        val pending = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                val saved = recordingFile
                recordingFile = null
                if (!event.hasError() && saved != null) {
                    onCaptured(saved, "VIDEO")
                } else {
                    runCatching { saved?.delete() }
                    failed = true
                }
            }
        }
    }

    fun stopVideo() {
        recording?.stop()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onClose) { Text(closeLabel, color = Color.White) }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (failed) {
                Text(errorLabel, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton(onClick = { videoMode = false }) {
                    Text(photoLabel, color = if (!videoMode) Color(0xFFC6FF3D) else Color.White)
                }
                TextButton(onClick = { videoMode = true }) {
                    Text(videoLabel, color = if (videoMode) Color(0xFFC6FF3D) else Color.White)
                }
            }
            Surface(
                onClick = {
                    if (!videoMode) {
                        takePhoto()
                    } else if (recording == null) {
                        startVideo()
                    } else {
                        stopVideo()
                    }
                },
                color = if (recording != null) Color(0xFFFF6B6B) else Color(0xFFC6FF3D),
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (videoMode && recording != null) recordingLabel else title,
                        color = Color(0xFF0E1113),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
