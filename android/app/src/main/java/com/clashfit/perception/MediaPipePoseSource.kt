package com.clashfit.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
import kotlinx.coroutines.CancellationException

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
) : PoseSource, CameraPreviewSource {

    private val TAG = "ClashFit/perception"
    /**
     * Frames reach the engine newest-first and in order.
     *
     * They used to be handed to a coroutine on a multi-threaded dispatcher, which let two results
     * race into a one-slot channel and arrive swapped. Every window in the engine is a
     * `now - then > n` test, and a negative difference passes none of them, so a single swap could
     * quietly switch off the rep counter, the cadence window and the hold timer at once. Conflating
     * and sending straight from the detector's own callback thread keeps the detector's order,
     * which is the order the frames were taken in.
     */
    private val frameChannel = Channel<PoseFrame>(Channel.CONFLATED)
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
     * The upright analysis image's shape, as width over height, once it is on screen.
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

    // Bitmaps are reused rather than allocated per frame: at 30 fps a fresh 720p ARGB bitmap every
    // frame is 100 MB a second through the collector. `stage` holds the buffer exactly as the plane
    // stores it, padding included; the pool holds the upright copies handed to the detector.
    private val bitmapPool = mutableListOf<Bitmap>()
    private var bitmapPoolIndex = 0
    private val BITMAP_POOL_SIZE = 3
    private var stage: Bitmap? = null
    private var lastGeometry: FrameGeometry? = null
    private var lastEmittedMs = Long.MIN_VALUE

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
        // Release bitmap pool
        bitmapPool.forEach { it.recycle() }
        bitmapPool.clear()
        stage?.recycle()
        stage = null
        lastGeometry = null
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

            val plane = imageProxy.planes[0]
            val geometry = FrameGeometry.of(
                width = imageProxy.width,
                height = imageProxy.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                rotationDeg = imageProxy.imageInfo.rotationDegrees,
            )
            if (geometry != lastGeometry) {
                // The shape of the frame decides whether the overlay lines up at all, and it cannot
                // be read off anything but a real camera. Logged whenever it changes.
                Log.i(TAG, "analysis frame $geometry")
                lastGeometry = geometry
                _sourceAspect.value = geometry.aspect
                bitmapPool.forEach { it.recycle() }
                bitmapPool.clear()
            }

            // Turned upright and unpadded (already RGBA_8888 from setOutputImageFormat)
            val bitmap = uprightBitmap(imageProxy, geometry)
            if (bitmap != null) {
                // detectAsync copies the pixels into a packet before it returns, so the pooled
                // bitmap can be reused on the next frame. Never call mpImage.close() here: the
                // bitmap container's close() recycles the Bitmap, which would poison the pool.
                val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
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

    /**
     * The frame as an upright, unpadded bitmap, ready for the detector.
     *
     * Both steps here were missing, and together they are why the skeleton was drawn beside the
     * player rather than on them. The plane's rows are padded out to its row stride, so copying the
     * buffer into a bitmap of the image's width sheared every row after the first. And the camera's
     * own rotation was read and logged and never applied, so the detector was shown a body lying on
     * its side and returned landmarks in a frame turned a quarter turn from the preview.
     */
    private fun uprightBitmap(imageProxy: ImageProxy, g: FrameGeometry): Bitmap? {
        return try {
            val src = stageBitmap(g)
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            src.copyPixelsFromBuffer(buffer)
            if (g.readyAsIs) return src

            val out = pooledUpright(g)
            // Rotating about the origin puts the frame outside the rectangle; the translation
            // brings its visible corner back to (0, 0), which also leaves the padding columns
            // outside the destination, where they are clipped away.
            val matrix = Matrix().apply {
                postRotate(g.rotationDeg.toFloat())
                postTranslate(g.translateX, g.translateY)
            }
            Canvas(out).apply {
                drawColor(Color.BLACK)
                drawBitmap(src, matrix, null)
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    /** The scratch bitmap the plane is copied into, sized to the buffer's real width. */
    private fun stageBitmap(g: FrameGeometry): Bitmap {
        val current = stage
        if (current != null && !current.isRecycled &&
            current.width == g.bufferWidth && current.height == g.sourceHeight
        ) {
            return current
        }
        current?.recycle()
        return createBitmap(g.bufferWidth, g.sourceHeight).also { stage = it }
    }

    /**
     * The next upright bitmap from the pool. detectAsync copies the pixels into a packet before it
     * returns, so a small ring is enough to keep the one in flight from being drawn over.
     */
    private fun pooledUpright(g: FrameGeometry): Bitmap {
        if (bitmapPool.isEmpty()) {
            repeat(BITMAP_POOL_SIZE) { bitmapPool.add(createBitmap(g.width, g.height)) }
        }
        bitmapPoolIndex = (bitmapPoolIndex + 1) % bitmapPool.size
        return bitmapPool[bitmapPoolIndex]
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

            val normalized = result.landmarks().firstOrNull()?.takeIf { it.size == 33 }
            val metric = result.worldLandmarks().firstOrNull()?.takeIf { it.size == 33 }

            // visibility(), not presence(). MediaPipe's presence says the joint is in frame;
            // visibility says it is also not hidden behind something. The engine gates a frame on
            // visibility for exactly this reason: a wrist behind your back is present and
            // invisible, and measuring an elbow angle through it is measuring a guess. It is read
            // off the normalised landmarks, which always carry it, rather than defaulted to fully
            // visible when the metric list happens to leave it out.
            val seen = normalized?.map { it.visibility().orElse(1f) }

            // worldLandmarks(), not landmarks(). The two are different things: landmarks() is
            // normalised to the picture, so an angle read off it changes when you step nearer the
            // camera, and a distance read off it is in fractions of a frame rather than metres.
            // worldLandmarks() is metric and hip-centred, which is what every angle, every
            // threshold and the jump-height scale are written against. Feeding the picture
            // coordinates into this field made all of them meaningless.
            val worldLandmarks = metric?.mapIndexed { i, lm ->
                Landmark(x = lm.x(), y = lm.y(), z = lm.z(), visibility = seen?.getOrNull(i) ?: lm.visibility().orElse(1f))
            }

            val imageLandmarks = normalized?.mapIndexed { i, lm ->
                // Normalised 0..1 against the upright frame. The front camera's preview is
                // mirrored, so mirror x to match it; the metric landmarks above are left alone, so
                // the model's left arm stays the player's left arm.
                Landmark(
                    x = if (_facing.value == CameraFacing.FRONT) 1f - lm.x() else lm.x(),
                    y = lm.y(),
                    z = lm.z(),
                    visibility = seen?.getOrNull(i) ?: 1f,
                )
            }

            val frame = PoseFrame(
                world = worldLandmarks,
                image = imageLandmarks,
                tMs = imageTimestampMs
            )

            // Straight from the detector's callback thread, in the detector's own order. See the
            // note on frameChannel: a coroutine here could deliver two frames swapped.
            //
            // A frame no newer than the last one is dropped rather than delivered. Everything
            // downstream measures time as `now - then > n`, and one frame out of order makes that
            // difference negative, which passes no window and no dwell gate: a single swap can
            // switch off the rep counter, the cadence window and the hold timer at once. Dropping
            // here means no consumer has to defend itself.
            if (imageTimestampMs > lastEmittedMs) {
                lastEmittedMs = imageTimestampMs
                frameChannel.trySend(frame)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing landmark result", e)
        }
    }
}
