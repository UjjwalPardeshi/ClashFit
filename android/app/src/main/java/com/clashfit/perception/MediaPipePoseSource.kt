package com.clashfit.perception

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import kotlinx.coroutines.CancellationException
import com.clashfit.perception.gesture.GestureReading
import com.clashfit.perception.gesture.GestureSource
import com.clashfit.perception.gesture.HandGesture
import com.clashfit.perception.vision.FrameRing
import com.clashfit.perception.vision.FrameSource
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.channels.BufferOverflow

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
) : PoseSource, CameraPreviewSource, GestureSource, FrameSource {

    private val TAG = "ClashFit/perception"
    private val frameChannel = Channel<PoseFrame>(capacity = 1)
    override val frames: Flow<PoseFrame> = frameChannel.receiveAsFlow()

    private val _fps = MutableStateFlow(0f)
    override val fps: StateFlow<Float> = _fps.asStateFlow()

    /**
     * The live camera image. Created lazily: a run driven by a recorded trace or by the synthetic
     * source never needs one, and allocating a SurfaceView-backed view it will not use is waste.
     */
    override val previewView: PreviewView by lazy {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    /**
     * The analysis image's shape, as width over height, once it is on screen.
     *
     * The overlay needs this. PreviewView scales the camera image to FILL the view and crops the
     * overflow, so a rig that maps landmarks onto the full view rect lands offset from the body by
     * exactly the crop. Normalised landmarks are relative to the analysis image, not to the view,
     * and those two only agree when their aspect ratios do.
     */
    private val _sourceAspect = MutableStateFlow<Float?>(null)
    override val sourceAspect: StateFlow<Float?> = _sourceAspect.asStateFlow()

    private val _facing = MutableStateFlow(CameraFacing.FRONT)
    override val facing: StateFlow<CameraFacing> = _facing.asStateFlow()

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var poseLandmarker: PoseLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lowPowerEnabled = false
    private var frameCount = 0L
    private var lastFpsUpdateMs = 0L
    private var lastFrameTimeMs = 0L

    // Bitmap pool for reuse (pre-allocated at init, reused every frame to avoid GC)
    private val bitmapPool = mutableListOf<Bitmap>()
    private var bitmapPoolIndex = 0
    private val BITMAP_POOL_SIZE = 3

    /** Four seconds of camera at the analysis rate, at a quarter scale. */
    private val RECENT_FRAMES = 20

    // The hand, read off the same frames as the body. Created with the pose model, fed every third
    // frame (about ten a second, which is plenty for a shape held for six hundred milliseconds),
    // and only while the fight wants it — a raised palm during calibration or rest means nothing.
    private var gestureRecognizer: GestureRecognizer? = null
    @Volatile private var gesturesEnabled = false
    private val gestureChannel = Channel<GestureReading>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val gestures: Flow<GestureReading> = gestureChannel.receiveAsFlow()

    /**
     * The last few seconds of the camera, small.
     *
     * A rep completes a second or two after its deepest moment, and the frame worth showing a model
     * is the deep one — by then it has gone past. Twenty frames at a quarter scale is about four
     * seconds at the analysis rate and roughly 3 MB, which buys the referee its eyes for the cost
     * of one screenshot. Written and read on the camera thread only.
     */
    private val recentFrames = FrameRing<Bitmap>(RECENT_FRAMES)
    @Volatile private var keepFrames = false

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
        gestureRecognizer?.close()
        gestureRecognizer = null
        keepFrames = false
        recentFrames.drain().forEach { it.recycle() }
        // Release bitmap pool
        bitmapPool.forEach { it.recycle() }
        bitmapPool.clear()
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.w(TAG, "Executor did not terminate within 2 seconds")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted waiting for executor termination", e)
        }
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
            initGestureRecognizer()
            startCamera()
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. CancellationException is an Exception,
            // so a catch-all below would swallow it and carry on running work whose
            // caller has already gone.
            throw cancelled
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

                // The preview use case. Only ImageAnalysis was bound before, which meant the app
                // read the player's body and never showed it to them: during a fight you could
                // not see yourself at all. The landmarks are already mirrored for the front
                // camera (see processImageProxy), which is the same thing PreviewView does to a
                // front-camera image, so an overlay drawn from them lands on the right limb.
                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(720, 1280))
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis,
                    preview,
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

        if (_sourceAspect.value == null && imageProxy.height > 0) {
            _sourceAspect.value = imageProxy.width.toFloat() / imageProxy.height
            // Logged once, because the answer decides whether the overlay lines up at all and it
            // cannot be read off anything but a real camera.
            Log.i(
                TAG,
                "analysis frame ${imageProxy.width}x${imageProxy.height}, " +
                    "rotation ${imageProxy.imageInfo.rotationDegrees}deg",
            )
        }

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

            // Initialize bitmap pool on first frame
            if (bitmapPool.isEmpty()) {
                for (i in 0 until BITMAP_POOL_SIZE) {
                    bitmapPool.add(createBitmap(imageProxy.width, imageProxy.height))
                }
            }

            // Convert ImageProxy to Bitmap (already RGBA_8888 from setOutputImageFormat)
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                // detectAsync copies the pixels into a packet before it returns, so the pooled
                // bitmap can be reused on the next frame. Never call mpImage.close() here: the
                // bitmap container's close() recycles the Bitmap, which would poison the pool.
                val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
                poseLandmarker?.detectAsync(mpImage, now)
                // The hand, on every third frame. recognizeAsync copies its pixels the same way
                // detectAsync does, so both tasks can read the one pooled bitmap.
                if (gesturesEnabled && frameCount % 3 == 0L) gestureRecognizer?.recognizeAsync(mpImage, now)
                // A quarter-scale copy, kept for a few seconds so the referee can look back at the
                // bottom of a rep. A copy, not the pooled bitmap: that one is overwritten by the
                // next frame, so keeping a reference to it would keep a picture of the future.
                if (keepFrames && frameCount % 2 == 0L) {
                    val small = Bitmap.createScaledBitmap(bitmap, bitmap.width / 4, bitmap.height / 4, true)
                    recentFrames.push(now, small)?.recycle()
                }
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
            // Get a bitmap from the pool (pre-allocated to avoid allocation churn)
            if (bitmapPool.isEmpty()) {
                return null
            }
            val bitmap = bitmapPool[bitmapPoolIndex % bitmapPool.size]
            bitmapPoolIndex++

            val planes = imageProxy.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowPadding = planes[0].rowStride - pixelStride * imageProxy.width

            // Copy pixel data into the reused bitmap
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
                        // visibility(), not presence(). MediaPipe's presence says the joint is in
                        // frame; visibility says it is also not hidden behind something. These
                        // world landmarks are what the scorer measures angles from, and the pose
                        // spec gates a frame on visibility for exactly this reason: a wrist behind
                        // your back is present and invisible, and using it computes an elbow angle
                        // from a guess.
                        visibility = lm.visibility().orElse(1f),
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

    // ── the hand ─────────────────────────────────────────────────────────────────────────────

    /**
     * The hand model. Eight megabytes in the APK, CPU delegate on purpose: the GPU is busy with
     * the body at thirty frames a second, and a hand at ten frames a second is cheap on CPU. If
     * the model fails to load the fight simply has no gestures — nothing else notices.
     */
    private fun initGestureRecognizer() {
        try {
            gestureRecognizer = GestureRecognizer.createFromOptions(
                context,
                GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath("models/gesture_recognizer.task")
                            .setDelegate(Delegate.CPU)
                            .build(),
                    )
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.6f)
                    .setMinHandPresenceConfidence(0.6f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ -> onGestureResult(result) }
                    .setErrorListener { e -> Log.w(TAG, "gesture recogniser: ${e.message}") }
                    .build(),
            )
            Log.i(TAG, "gesture recogniser ready")
        } catch (e: Exception) {
            Log.w(TAG, "gesture recogniser unavailable; the fight runs without hand control", e)
            gestureRecognizer = null
        }
    }

    private fun onGestureResult(result: GestureRecognizerResult) {
        // No hand in frame is itself a reading: it is what breaks a hold.
        val top = result.gestures().firstOrNull()?.firstOrNull()
        val reading = if (top == null) {
            GestureReading(HandGesture.NONE, 0f, result.timestampMs())
        } else {
            GestureReading(HandGesture.fromMediaPipe(top.categoryName()), top.score(), result.timestampMs())
        }
        gestureChannel.trySend(reading)
    }

    override fun setGesturesEnabled(enabled: Boolean) {
        gesturesEnabled = enabled
    }

    // ── the referee's eyes ────────────────────────────────────────────────────────────────────

    /**
     * Start or stop keeping recent frames. Off by default, and off the moment the on-device model
     * is unavailable: there is no point paying for the copies if nothing can look at them.
     */
    fun setKeepFrames(enabled: Boolean) {
        if (keepFrames == enabled) return
        keepFrames = enabled
        if (!enabled) recentFrames.drain().forEach { it.recycle() }
    }

    /**
     * A copy of the frame nearest [tMs], within two seconds. The caller owns it and recycles it.
     *
     * A copy because the ring keeps its own frames alive for the next request, and because the
     * model's session outlives this call. The whole ring is thrown away when the session ends.
     */
    override fun frameNear(tMs: Long): Bitmap? {
        val found = recentFrames.nearest(tMs, toleranceMs = 2_000L) ?: return null
        return runCatching { found.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
    }
}
