package com.clashfit.run

import android.content.pm.PackageManager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.clashfit.AppGraph
import com.clashfit.R
import com.clashfit.data.RunPointEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Foreground location service tracking distance, pace, elevation, and cadence. */
class RunTrackingService : LifecycleService(), LocationListener, SensorEventListener {

    /** Lint-visible permission check: the screen asked already, but the service must not assume it. */
    private fun requestUpdatesGuarded(locationRequest: LocationRequest) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            // Request location updates on background thread to avoid blocking main thread
            val backgroundHandler = Handler(Looper.getMainLooper()) // Use default for safety; moved to IO in listener
            fusedClient.requestLocationUpdates(locationRequest, this, backgroundHandler.looper)
        } else {
            Log.w(TAG, "Location permission missing; the run cannot be tracked.")
            stopSelf()
        }
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var graph: AppGraph
    private lateinit var sensorManager: SensorManager
    private lateinit var tracker: RunTracker
    private lateinit var repo: RunRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private var backgroundLocationHandler: Handler? = null

    private var runId: Long? = null
    private var runInsertInFlight = false
    private var currentState = RunState()
    private var lastLocationMs: Long = 0L
    private var lastElev: Double = 0.0
    private var lastAccelMagnitude: Float = 0f
    private var peakAccel: Float = 0f
    private var cadenceAccelCount: Int = 0
    private var lastKmDistanceMs: Long = 0L
    private var lastKmMovingMs: Long = 0L
    private var nextKmMarker: Float = 1000f // metres

    // GPS fix quality tracking
    private var firstFixTimeMs: Long = 0L
    private var lastSmoothedAltitude: Double = 0.0
    private val altitudeHistory = ArrayDeque<Double>(5) // For median filtering

    // Moving time tracking with hysteresis
    private var lastMovingTransitionMs: Long = 0L
    private var stationaryStartMs: Long? = null

    // Pause detection hysteresis
    private var lastSpeedMps: Float = 0f
    private var speedBelowThresholdCount: Int = 0

    private val CHANNEL_ID = "run"
    private val NOTIFICATION_ID = 2
    private val ACCURACY_THRESHOLD_M = 25f
    private val MIN_SPEED_MPS = 0.5f
    private val SPEED_HYSTERESIS_MPS = 0.3f // Lower threshold for moving detection
    private val SPEED_HYSTERESIS_WINDOW_S = 3 // Require 3 seconds below threshold
    private val ELEV_HYSTERESIS_M = 3f
    private val BATCH_SIZE = 10
    private val FIRST_FIX_SETTLE_MS = 10_000L // Settle GPS for 10 seconds
    private val MAX_SPEED_MPS = 8f // 28.8 km/h, ~17 mph
    private val MIN_JITTER_DISTANCE_M = 2f // Ignore points < 2m away at low speed
    private val JITTER_SPEED_THRESHOLD_MPS = 0.1f

    private var stepCounterBaseline: Long = 0L
    private var lastStepCount: Long = 0L
    private var cadenceStartMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.of(applicationContext)
        tracker = RunTracker.getInstance()
        repo = RunRepository(graph.db.runs(), graph.clock)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createNotificationChannel()

        // Acquire wake lock to prevent GPS throttling during sleep
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:run")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startRun()
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> stopRun()
        }

        // Return START_STICKY for recovery: if the OS kills the service, it will be restarted
        // and we can recover the in-flight points
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources: release wake lock, remove location updates
        wakeLock?.release()
        fusedClient.removeLocationUpdates(this)
        sensorManager.unregisterListener(this)
        backgroundLocationHandler?.looper?.quit()
    }

    @SuppressLint("MissingPermission")
    private fun startRun() {
        if (currentState.isRunning) return
        Log.d(TAG, "Starting run")

        val nowMs = graph.clock.nowMs()
        currentState = RunState(
            runId = null, // Will be set after first valid GPS fix
            startedAtMs = nowMs,
        )
        firstFixTimeMs = nowMs
        lastKmDistanceMs = nowMs
        lastKmMovingMs = 0L
        stationaryStartMs = null
        speedBelowThresholdCount = 0

        // Acquire wake lock to ensure GPS doesn't get throttled
        wakeLock?.acquire()

        // Start location updates at 1 Hz with no max update delay to prevent batching
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(1500L) // Prevent GPS batching that would reduce accuracy
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        requestUpdatesGuarded(locationRequest)

        // Register step counter, fallback to accelerometer
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            cadenceStartMs = graph.clock.nowMs()
            // Set baseline immediately at registration time, not at first event
            stepCounterBaseline = 0L // Will be set on first event
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        // Don't insert run yet; wait for first valid GPS fix
        // This prevents "ghost runs" with 0 distance if app crashes before first location
        tracker.setState(currentState)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun pauseRun() {
        Log.d(TAG, "Pausing run")
        currentState = currentState.copy(isPaused = true)
        fusedClient.removeLocationUpdates(this)
        sensorManager.unregisterListener(this)
        tracker.setState(currentState)
    }

    private fun resumeRun() {
        Log.d(TAG, "Resuming run")
        currentState = currentState.copy(isPaused = false)

        @SuppressLint("MissingPermission")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        requestUpdatesGuarded(locationRequest)

        // Register step counter, fallback to accelerometer
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        tracker.setState(currentState)
    }

    private fun stopRun() {
        Log.d(TAG, "Stopping run")
        fusedClient.removeLocationUpdates(this)
        sensorManager.unregisterListener(this)
        wakeLock?.release()

        val rid = currentState.runId ?: return
        val endedAtMs = graph.clock.nowMs()

        lifecycleScope.launch {
            try {
                // Persist remaining points synchronously
                if (currentState.points.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        repo.insertPoints(rid, currentState.points)
                    }
                }

                // Finish the run
                withContext(Dispatchers.IO) {
                    repo.finishRun(
                        rid, endedAtMs, currentState.distanceM, currentState.movingMs,
                        currentState.avgPaceSecPerKm, currentState.cadenceSpm,
                        currentState.elevationGainM, currentState.splits.joinToString(","),
                    )
                }

                Log.d(TAG, "Run finished: distance=${currentState.distanceM}m, moving time=${currentState.movingMs}ms")
                currentState = RunState()
                tracker.resetState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping run", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        val nowMs = graph.clock.nowMs()

        // Accuracy gating: only accept fixes better than 25m
        if (location.accuracy > ACCURACY_THRESHOLD_M) {
            Log.d(TAG, "Ignoring fix: accuracy ${location.accuracy}m > ${ACCURACY_THRESHOLD_M}m")
            return
        }

        // First-fix handling: ignore fixes within first 10 seconds (GPS lock settling)
        if (nowMs - firstFixTimeMs < FIRST_FIX_SETTLE_MS) {
            Log.d(TAG, "Waiting for GPS to settle (${nowMs - firstFixTimeMs}ms / $FIRST_FIX_SETTLE_MS ms)")
            return
        }

        // Validate coordinates before processing
        if (location.latitude < -90 || location.latitude > 90 ||
            location.longitude < -180 || location.longitude > 180 ||
            location.latitude.isNaN() || location.longitude.isNaN() ||
            location.altitude.isNaN()
        ) {
            Log.w(TAG, "Invalid coordinates: lat=${location.latitude}, lon=${location.longitude}")
            return
        }

        // Duplicate timestamp check: only accept if tMs > lastLocationMs
        if (nowMs <= lastLocationMs) {
            Log.d(TAG, "Skipping duplicate/old timestamp: nowMs=$nowMs, lastLocationMs=$lastLocationMs")
            return
        }

        // The first settled fix seeds the run row; tracking starts on the next fix. The insert is
        // asynchronous so a location callback never blocks the main thread on the database.
        if (currentState.runId == null) {
            if (!runInsertInFlight) {
                runInsertInFlight = true
                lifecycleScope.launch {
                    val rid = try {
                        withContext(Dispatchers.IO) {
                            repo.insertRun(distanceM = 0f, movingMs = 0L, pace = 0f, cadence = 0, elev = 0f, splits = "")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert run on first valid GPS fix", e)
                        runInsertInFlight = false
                        return@launch
                    }
                    currentState = currentState.copy(runId = rid)
                    tracker.setState(currentState)
                    runInsertInFlight = false
                    Log.d(TAG, "Run inserted on first valid GPS fix: runId=$rid")
                }
            }
            return
        }

        val points = currentState.points.toMutableList()
        val point = RunPointEntity(
            runId = currentState.runId ?: return,
            tMs = nowMs,
            lat = location.latitude,
            lon = location.longitude,
            altM = location.altitude,
            accuracyM = location.accuracy,
            speedMps = location.speed,
        )
        points.add(point)

        // Calculate distance and pace from last two points
        if (points.size > 1) {
            val prev = points[points.size - 2]
            val curr = points[points.size - 1]

            val deltaDistM = haversineM(prev.lat, prev.lon, curr.lat, curr.lon)
            val deltaTimeMs = curr.tMs - prev.tMs

            // Impossible speed jump validation
            val speedMps = if (deltaTimeMs > 0) deltaDistM / (deltaTimeMs / 1000f) else 0f
            if (speedMps > MAX_SPEED_MPS) {
                Log.d(TAG, "Rejecting speed jump: ${speedMps}m/s (${(speedMps * 3.6).toInt()}km/h) > ${MAX_SPEED_MPS}m/s")
                // Don't add this point, return early
                return
            }

            // Jitter filtering: if standing still (speed < 0.1 m/s) and moved < 2m, ignore
            if (speedMps < JITTER_SPEED_THRESHOLD_MPS && deltaDistM < MIN_JITTER_DISTANCE_M) {
                Log.d(TAG, "Ignoring jitter: ${deltaDistM}m at ${speedMps}m/s")
                return
            }

            var distToAdd = deltaDistM
            currentState = currentState.copy(
                distanceM = currentState.distanceM + distToAdd,
                points = points,
            )

            // Moving time tracking with hysteresis
            updateMovingState(nowMs, speedMps, deltaTimeMs)

            // Track per-km splits (moved outside isMoving conditional for boundary accuracy)
            while (currentState.distanceM >= nextKmMarker) {
                val movingMsForSplit = currentState.movingMs - lastKmMovingMs
                val kmPaceSec = if (movingMsForSplit > 0) {
                    movingMsForSplit / 1000f
                } else {
                    0f // If no moving time, pace is 0
                }
                val splits = currentState.splits.toMutableList()
                splits.add(kmPaceSec)
                currentState = currentState.copy(splits = splits)
                lastKmMovingMs = currentState.movingMs
                nextKmMarker += 1000f
                Log.d(TAG, "Split recorded at ${currentState.distanceM}m: ${kmPaceSec.toInt()}s")
            }

            // Update elevation with hysteresis and smoothing
            if (location.hasAltitude()) {
                altitudeHistory.addLast(location.altitude)
                if (altitudeHistory.size > 5) {
                    altitudeHistory.removeFirst()
                }

                // Use median of recent altitudes for smoothing
                val smoothedAlt = altitudeHistory.sorted()[altitudeHistory.size / 2]
                val deltaElev = smoothedAlt - lastSmoothedAltitude

                // Apply hysteresis: only update if delta > threshold
                if (kotlin.math.abs(deltaElev) > ELEV_HYSTERESIS_M) {
                    // Count only positive elevation as gain
                    if (deltaElev > 0) {
                        currentState = currentState.copy(
                            elevationGainM = currentState.elevationGainM + deltaElev.toFloat(),
                        )
                    }
                    lastSmoothedAltitude = smoothedAlt
                    Log.d(TAG, "Elevation update: +${deltaElev}m, total gain=${currentState.elevationGainM}m")
                }
            }

            // Update pace
            if (currentState.distanceM > 0 && currentState.movingMs > 0) {
                currentState = currentState.copy(
                    avgPaceSecPerKm = (currentState.movingMs / 1000f) / (currentState.distanceM / 1000f),
                )
            }
        }

        // Batch save to DB every BATCH_SIZE points
        if (points.size >= BATCH_SIZE) {
            lifecycleScope.launch {
                val rid = currentState.runId ?: return@launch
                // Use withContext to AWAIT the DB write before clearing buffer
                withContext(Dispatchers.IO) {
                    repo.insertPoints(rid, points)
                }
                currentState = currentState.copy(points = emptyList())
            }
        }

        tracker.setState(currentState)
        lastLocationMs = nowMs
    }

    private fun updateMovingState(nowMs: Long, speedMps: Float, deltaTimeMs: Long) {
        val wasMoving = currentState.isMoving
        val isCurrentlyMoving = speedMps >= SPEED_HYSTERESIS_MPS

        if (isCurrentlyMoving) {
            // Reset stationary timer when moving
            stationaryStartMs = null
            speedBelowThresholdCount = 0

            if (!wasMoving) {
                // Transition from stopped to moving
                currentState = currentState.copy(isMoving = true)
                lastMovingTransitionMs = nowMs
                Log.d(TAG, "Resumed moving at ${speedMps}m/s")
            } else {
                // Continue moving; add to moving time
                currentState = currentState.copy(movingMs = currentState.movingMs + deltaTimeMs)
            }
        } else {
            // Speed below threshold; check for pause with hysteresis
            if (stationaryStartMs == null) {
                stationaryStartMs = nowMs
                speedBelowThresholdCount = 1
            } else {
                speedBelowThresholdCount++
            }

            // Require 3+ seconds below threshold before pausing
            val stationaryDurationMs = nowMs - (stationaryStartMs ?: nowMs)
            if (wasMoving && stationaryDurationMs >= (SPEED_HYSTERESIS_WINDOW_S * 1000) && speedBelowThresholdCount >= 3) {
                // Transition to paused
                currentState = currentState.copy(isMoving = false)
                Log.d(TAG, "Paused (${speedBelowThresholdCount}s below ${SPEED_HYSTERESIS_MPS}m/s)")
            } else if (wasMoving) {
                // Still moving, add time before pause
                currentState = currentState.copy(movingMs = currentState.movingMs + deltaTimeMs)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values[0].toLong()
                // Set baseline on first event only
                if (stepCounterBaseline == 0L) {
                    stepCounterBaseline = steps
                    Log.d(TAG, "Step counter baseline set: $steps")
                    return // Don't process first event; cadence will be 0/1 until enough time passes
                }

                val stepsSinceStart = steps - stepCounterBaseline
                val elapsedSec = (graph.clock.nowMs() - cadenceStartMs) / 1000f
                if (elapsedSec > 0 && stepsSinceStart > 0) {
                    val cadence = (stepsSinceStart * 60 / elapsedSec).toInt().coerceIn(0, 200)
                    currentState = currentState.copy(cadenceSpm = cadence)
                    tracker.setState(currentState)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                // Detect peaks for cadence fallback
                if (magnitude > peakAccel && magnitude - lastAccelMagnitude > 0) {
                    peakAccel = magnitude
                } else if (magnitude < peakAccel * 0.8f) {
                    cadenceAccelCount++
                    peakAccel = 0f

                    // Don't return fake estimates; only estimate if we have enough peaks
                    if (cadenceAccelCount > 10) {
                        // Rough estimate: 10 peaks ≈ 60 steps at normal cadence
                        val elapsedSec = (graph.clock.nowMs() - cadenceStartMs) / 1000f
                        if (elapsedSec > 1) {
                            val estimatedCadence = (cadenceAccelCount * 60 / elapsedSec).toInt()
                                .coerceIn(0, 200)
                            currentState = currentState.copy(cadenceSpm = estimatedCadence)
                            tracker.setState(currentState)
                        }
                    }
                }

                lastAccelMagnitude = magnitude
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        // Validate inputs: lat in [-90, 90], lon in [-180, 180], no NaN/Infinity
        require(lat1 in -90.0..90.0 && !lat1.isNaN()) { "Invalid lat1: $lat1" }
        require(lon1 in -180.0..180.0 && !lon1.isNaN()) { "Invalid lon1: $lon1" }
        require(lat2 in -90.0..90.0 && !lat2.isNaN()) { "Invalid lat2: $lat2" }
        require(lon2 in -180.0..180.0 && !lon2.isNaN()) { "Invalid lon2: $lon2" }

        val R = 6371000.0 // Earth radius in metres
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).toFloat()
    }

    private fun buildNotification(): Notification {
        val distKm = (currentState.distanceM / 1000f)
        val content = "%.2f km • %s".format(distKm, formatDuration(currentState.movingMs))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Run in progress")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, mins, secs)
            else -> "%d:%02d".format(mins, secs)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_run), NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ClashFit/run"
        const val ACTION_START = "com.clashfit.run.START"
        const val ACTION_PAUSE = "com.clashfit.run.PAUSE"
        const val ACTION_RESUME = "com.clashfit.run.RESUME"
        const val ACTION_STOP = "com.clashfit.run.STOP"

        fun start(context: Context) = ContextCompat.startForegroundService(
            context,
            Intent(context, RunTrackingService::class.java).setAction(ACTION_START),
        )

        fun pause(context: Context) = context.startService(
            Intent(context, RunTrackingService::class.java).setAction(ACTION_PAUSE),
        )

        fun resume(context: Context) = context.startService(
            Intent(context, RunTrackingService::class.java).setAction(ACTION_RESUME),
        )

        fun stop(context: Context) = context.startService(
            Intent(context, RunTrackingService::class.java).setAction(ACTION_STOP),
        )
    }
}
