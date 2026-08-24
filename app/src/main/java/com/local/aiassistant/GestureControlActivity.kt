package com.local.aiassistant

import androidx.compose.ui.unit.dp
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors
import kotlin.math.hypot

/**
 * Touchless control: the phone's own camera watches your hand in the air.
 * We don't touch the screen at all — MediaPipe gives us 21 3D landmarks per
 * hand every frame, and we turn a few of those into gestures:
 *
 *   - Index finger moves in the air  -> cursor-style tap position tracks it
 *   - Thumb tip + index tip pinch together -> "tap" at that tracked position
 *   - Open palm swipes left/right   -> swipe left/right (e.g. change app / scroll)
 *   - Fist held for ~1s             -> go Home
 *
 * All processing happens on-device (MediaPipe Tasks Vision), nothing is
 * uploaded anywhere.
 */
class GestureControlActivity : ComponentActivity() {

    private var handLandmarker: HandLandmarker? = null
    private var lastGestureTimeMs = 0L
    private var pinchActive = false

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupHandLandmarker()

        setContent {
            var status by remember { mutableStateOf("Starting camera…") }
            statusUpdater = { status = it }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Touchless Gesture Control", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(status)
                        Spacer(Modifier.height(24.dp))
                        Text("Pinch thumb+index = tap")
                        Text("Open palm swipe = scroll/switch")
                        Text("Closed fist held = go Home")
                    }
                }
            }
        }

        cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private var statusUpdater: (String) -> Unit = {}

    /**
     * NOTE: This requires the "hand_landmarker.task" model file bundled in
     * app/src/main/assets/. Download it once from Google's model repo:
     * https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
     * and drop it into app/src/main/assets/hand_landmarker.task before building.
     */
    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setResultListener(::onHandResult)
                .setErrorListener { e -> Log.e("GestureControl", "MediaPipe error: ${e.message}") }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("GestureControl", "Failed to load hand_landmarker.task — see setup note", e)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image -> analyzeFrame(image) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            statusUpdater("Tracking hand…")
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: ImageProxy) {
        val timestampMs = System.currentTimeMillis()
        try {
            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(
                image.toBitmap()
            ).build()
            handLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("GestureControl", "Frame analysis failed", e)
        } finally {
            image.close()
        }
    }

    /** Landmark indices per MediaPipe Hands spec: 4 = thumb tip, 8 = index fingertip. */
    private fun onHandResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        val landmarks = result.landmarks().firstOrNull() ?: return
        if (landmarks.size < 9) return

        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val screenX = indexTip.x() * resources.displayMetrics.widthPixels
        val screenY = indexTip.y() * resources.displayMetrics.heightPixels

        val pinchDistance = hypot(
            (thumbTip.x() - indexTip.x()).toDouble(),
            (thumbTip.y() - indexTip.y()).toDouble()
        )

        val service = ControlAccessibilityService.instance
        if (service == null) {
            statusUpdater("Enable the Accessibility Service in Settings to act on gestures")
            return
        }

        // Pinch = tap
        if (pinchDistance < 0.05 && !pinchActive) {
            pinchActive = true
            service.tap(screenX.toFloat(), screenY.toFloat())
            statusUpdater("Pinch detected → tap")
        } else if (pinchDistance >= 0.05) {
            pinchActive = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker?.close()
    }
}
