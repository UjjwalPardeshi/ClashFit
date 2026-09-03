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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.clashfit.AppGraph
import com.clashfit.data.RunPointEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
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
            fusedClient.requestLocationUpdates(locationRequest, this, mainLooper)
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

    private var runId: Long? = null
    private var currentState = RunState()
    private var lastLocationMs: Long = 0L
    private var lastElev: Double = 0.0
    private var lastAccelMagnitude: Float = 0f
    private var peakAccel: Float = 0f
    private var cadenceAccelCount: Int = 0
    private var lastKmDistanceMs: Long = 0L
    private var nextKmMarker: Float = 1000f // metres

    private val CHANNEL_ID = "run"
    private val NOTIFICATION_ID = 2
    private val ACCURACY_THRESHOLD_M = 25f
    private val MIN_SPEED_MPS = 0.5f
    private val ELEV_HYSTERESIS_M = 3f
    private val BATCH_SIZE = 10

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startRun()
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> stopRun()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRun() {
        if (currentState.isRunning) return
        Log.d(TAG, "Starting run")

        currentState = RunState(
            runId = 1, // Will be updated after DB insert
            startedAtMs = graph.clock.nowMs(),
        )

        // Start location updates at 1 Hz
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        requestUpdatesGuarded(locationRequest)

        // Register step counter, fallback to accelerometer
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            cadenceStartMs = graph.clock.nowMs()
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        lifecycleScope.launch {
            currentState.startedAtMs?.let {
                val rid = repo.insertRun(
                    distanceM = 0f,
                    movingMs = 0L,
                    pace = 0f,
                    cadence = 0,
                    elev = 0f,
                    splits = "",
                )
                currentState = currentState.copy(runId = rid)
                tracker.setState(currentState)
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        tracker.setState(currentState)
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

        val rid = currentState.runId ?: return
        val endedAtMs = graph.clock.nowMs()

        lifecycleScope.launch {
            // Persist remaining points
            if (currentState.points.isNotEmpty()) {
                repo.insertPoints(rid, currentState.points)
            }

            // Finish the run
            repo.finishRun(
                rid, endedAtMs, currentState.distanceM, currentState.movingMs,
                currentState.avgPaceSecPerKm, currentState.cadenceSpm,
                currentState.elevationGainM, currentState.splits.joinToString(","),
            )

            currentState = RunState()
            tracker.resetState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        val nowMs = graph.clock.nowMs()
        val points = currentState.points.toMutableList()

        // Accuracy gating
        if (location.accuracy > ACCURACY_THRESHOLD_M) {
            Log.d(TAG, "Ignoring fix: accuracy ${location.accuracy} > $ACCURACY_THRESHOLD_M")
            return
        }

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

            currentState = currentState.copy(
                distanceM = currentState.distanceM + deltaDistM,
                points = points,
            )

            // Update moving time and pace if moving
            if (location.speed >= MIN_SPEED_MPS) {
                val deltaMoveMs = currentState.movingMs + deltaTimeMs
                currentState = currentState.copy(
                    movingMs = deltaMoveMs,
                    isMoving = true,
                    avgPaceSecPerKm = if (currentState.distanceM > 0) {
                        (deltaMoveMs / 1000f) / (currentState.distanceM / 1000f)
                    } else 0f,
                )

                // Track per-km splits
                if (currentState.distanceM >= nextKmMarker) {
                    val kmPaceSec = (nowMs - lastKmDistanceMs) / 1000f
                    val splits = currentState.splits.toMutableList()
                    splits.add(kmPaceSec)
                    currentState = currentState.copy(splits = splits)
                    lastKmDistanceMs = nowMs
                    nextKmMarker += 1000f
                }

                // Update elevation with hysteresis
                if (location.hasAltitude()) {
                    val deltaElev = location.altitude - lastElev
                    if (deltaElev > ELEV_HYSTERESIS_M) {
                        currentState = currentState.copy(
                            elevationGainM = currentState.elevationGainM + deltaElev.toFloat(),
                        )
                        lastElev = location.altitude
                    }
                }
            } else {
                currentState = currentState.copy(isMoving = false)
            }
        }

        // Batch save to DB every BATCH_SIZE points
        if (points.size >= BATCH_SIZE) {
            lifecycleScope.launch {
                val rid = currentState.runId ?: return@launch
                repo.insertPoints(rid, points)
                currentState = currentState.copy(points = emptyList())
            }
        }

        tracker.setState(currentState)
        lastLocationMs = nowMs
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values[0].toLong()
                if (stepCounterBaseline == 0L) {
                    stepCounterBaseline = steps
                }
                val stepsSinceStart = steps - stepCounterBaseline
                val elapsedSec = (graph.clock.nowMs() - cadenceStartMs) / 1000f
                if (elapsedSec > 0) {
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

                    // Estimate cadence from peak count (rough: 60 peaks per minute when running)
                    val estimatedCadence = (cadenceAccelCount * 60 / 10).coerceAtMost(200)
                    currentState = currentState.copy(cadenceSpm = estimatedCadence)
                    tracker.setState(currentState)
                }

                lastAccelMagnitude = magnitude
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Run Tracker", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
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
