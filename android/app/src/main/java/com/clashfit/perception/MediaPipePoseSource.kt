package com.clashfit.perception

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.clashfit.core.config.PoseConfig
import com.clashfit.core.model.Landmark
import com.clashfit.core.model.PoseFrame
import com.clashfit.core.pose.CameraFacing
import com.clashfit.core.pose.PoseSource
import com.clashfit.core.util.Clock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Live camera pose source using CameraX and MediaPipe Tasks Vision PoseLandmarker.
 * Captures at 720p, processes at 30fps (or 5fps when low-power is enabled), and emits frames
 * with world and image landmarks.
 */
class MediaPipePoseSource(
    private val context: Context,
    private val poseConfig: PoseConfig,
    private val clock: Clock,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
) : PoseSource {

    private val TAG = "ClashFit/perception"
    private val frameChannel = Channel<PoseFrame>(capacity = 1)
    override val frames: Flow<PoseFrame> = frameChannel.receiveAsFlow()

    private val _fps = MutableStateFlow(0f)
    override val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _facing = MutableStateFlow(CameraFacing.FRONT)
    override val facing: StateFlow<CameraFacing> = _facing.asStateFlow()

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var poseLandmarker: PoseLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lowPowerEnabled = false
    private var frameCount = 0L
    private var lastFpsUpdateMs = 0L
    private var lastFrameTimeMs = 0L

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> startCamera()
            Lifecycle.Event.ON_PAUSE -> stopCamera()
            else -> {}
        }
    }

    override fun start(facing: CameraFacing) {
        _facing.value = facing
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        scope.launch { initPoseLandmarker() }
    }

    override fun stop() {
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        stopCamera()
        poseLandmarker?.close()
        executor.shutdown()
    }

    override fun setLowPower(enabled: Boolean) {
        lowPowerEnabled = enabled
    }

    private suspend fun initPoseLandmarker() {
        try {
            val modelName = poseConfig.detector.model
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("models/$modelName")
                .setDelegate(
                    if (poseConfig.detector.delegate == "GPU") Delegate.GPU else Delegate.CPU
                )
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(poseConfig.detector.minPoseDetectionConfidence)
                    .setMinPosePresenceConfidence(poseConfig.detector.minPosePresenceConfidence)
                    .setMinTrackingConfidence(poseConfig.detector.minTrackingConfidence)
                    .setResultListener { result, _ ->
                        onPoseLandmarkerResult(result, result.timestampMs())
                    }
                    .build()
            )
            startCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PoseLandmarker", e)
        }
    }

    private fun startCamera() {
        scope.launch(Dispatchers.Main) {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider = provider

                val cameraSelector = if (_facing.value == CameraFacing.BACK) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(720, 1280))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
            }
        }
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop camera", e)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = clock.nowMs()

        // Low power mode: skip frames (5fps target)
        if (lowPowerEnabled && frameCount % 6 != 0L) {
            frameCount++
            imageProxy.close()
            return
        }

        try {
            if (poseLandmarker == null) {
                imageProxy.close()
                return
            }

            // Convert ImageProxy to Bitmap (already RGBA_8888 from setOutputImageFormat)
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val mpImage: MPImage = BitmapImageBuilder(bitmap).build()

                // Send to MediaPipe with timestamp
                poseLandmarker?.detectAsync(mpImage, now)
            }
            frameCount++
            lastFrameTimeMs = now
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowPadding = planes[0].rowStride - pixelStride * imageProxy.width

            val bitmap = createBitmap(
                imageProxy.width + rowPadding / pixelStride,
                imageProxy.height,
            )
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    private fun onPoseLandmarkerResult(result: PoseLandmarkerResult, imageTimestampMs: Long) {
        try {
            // Update FPS
            if (frameCount % 10 == 0L) {
                val now = clock.nowMs()
                val elapsed = now - lastFpsUpdateMs
                if (elapsed > 0) {
                    val newFps = (10 * 1000f) / elapsed
                    _fps.value = newFps
                    lastFpsUpdateMs = now
                }
            }

            val worldLandmarks = if (result.landmarks().isNotEmpty()) {
                val lms = result.landmarks()[0].map { lm ->
                    Landmark(
                        x = lm.x(),
                        y = lm.y(),
                        z = lm.z(),
                        visibility = lm.presence().orElse(1f)
                    )
                }
                if (lms.size == 33) lms else null
            } else {
                null
            }

            val imageLandmarks = if (result.landmarks().isNotEmpty()) {
                val lms = result.landmarks()[0].map { lm ->
                    // Image coordinates are normalized 0..1
                    // Front camera: mirror X (left-right flip for display)
                    // Back camera: use as-is
                    val x = if (_facing.value == CameraFacing.FRONT) 1f - lm.x() else lm.x()
                    Landmark(
                        x = x,
                        y = lm.y(),
                        z = lm.z(),
                        visibility = lm.visibility().orElse(1f)
                    )
                }
                if (lms.size == 33) lms else null
            } else {
                null
            }

            val frame = PoseFrame(
                world = worldLandmarks,
                image = imageLandmarks,
                tMs = imageTimestampMs
            )

            scope.launch {
                frameChannel.send(frame)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing landmark result", e)
        }
    }
}
