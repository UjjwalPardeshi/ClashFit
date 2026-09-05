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

    private var runInsertInFlight = false
    private var currentState = RunState()
    private var lastKmMovingMs: Long = 0L
    private var nextKmMarker: Float = 1000f // metres

    // Fix quality, elevation, cadence and moving time are decided by these four. They hold no
    // Android types, so they can be tested on the JVM; the service keeps only the plumbing.
    private val fixFilter = FixFilter()
    private val elevation = ElevationTracker()
    private val cadence = CadenceEstimator()
    private val movingTime = MovingTimeTracker()

    private val CHANNEL_ID = "run"
    private val NOTIFICATION_ID = 2
    private val BATCH_SIZE = 10

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

    /**
     * Release the wake lock, but only if it is actually held.
     *
     * Finishing a run releases it in stopRun, and Android then stops the service, which calls
     * onDestroy, which released it a second time. PowerManager treats that as a programming error
     * and throws "WakeLock under-locked" — out of onDestroy, where it becomes "Unable to stop
     * service" and takes the whole app down with it. So every run ended by finishing it crashed
     * the app at the moment the summary should have appeared.
     */
    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
                .onFailure { Log.w(TAG, "releasing the run wake lock failed", it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources: release wake lock, remove location updates
        releaseWakeLock()
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
        fixFilter.start(nowMs)
        elevation.reset()
        cadence.reset()
        movingTime.reset()
        lastKmMovingMs = 0L
        nextKmMarker = 1000f

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
            // The estimator takes its baseline from the first reading together with that
            // reading's own timestamp, so the gap until the sensor first fires cannot skew cadence.
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
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
        // The next fix after a resume starts a new leg; the distance covered while paused is not ours.
        fixFilter.clearAnchor()
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
        releaseWakeLock()

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
        val fix = Fix(
            tMs = graph.clock.nowMs(),
            lat = location.latitude,
            lon = location.longitude,
            altM = if (location.hasAltitude()) location.altitude else null,
            accuracyM = location.accuracy,
            reportedSpeedMps = if (location.hasSpeed()) location.speed else null,
        )

        when (val result = fixFilter.accept(fix)) {
            is FixResult.Rejected -> Log.d(TAG, "Fix dropped: ${result.reason}")
            is FixResult.Accepted -> onAcceptedFix(result)
        }
    }

    /** A fix that cleared every quality gate. Everything downstream trusts it. */
    private fun onAcceptedFix(accepted: FixResult.Accepted) {
        val fix = accepted.fix

        // The first settled fix seeds the run row; tracking starts on the next one. The insert is
        // asynchronous so a location callback never blocks the main thread on the database.
        val rid = currentState.runId
        if (rid == null) {
            insertRunOnFirstFix()
            return
        }

        val points = currentState.points + RunPointEntity(
            runId = rid,
            tMs = fix.tMs,
            lat = fix.lat,
            lon = fix.lon,
            altM = fix.altM ?: 0.0,
            accuracyM = fix.accuracyM,
            speedMps = accepted.speedMps,
        )

        movingTime.onFix(fix.tMs, accepted.speedMps, accepted.deltaMs)
        currentState = currentState.copy(
            distanceM = currentState.distanceM + accepted.distanceM,
            points = points,
            movingMs = movingTime.movingMs,
            isMoving = movingTime.isMoving,
        )

        recordSplits()

        fix.altM?.let { altM ->
            currentState = currentState.copy(elevationGainM = elevation.onAltitude(altM))
        }

        if (currentState.distanceM > 0 && currentState.movingMs > 0) {
            currentState = currentState.copy(
                avgPaceSecPerKm = (currentState.movingMs / 1000f) / (currentState.distanceM / 1000f),
            )
        }

        // Batch save to DB every BATCH_SIZE points
        if (points.size >= BATCH_SIZE) {
            lifecycleScope.launch {
                // Use withContext to AWAIT the DB write before clearing buffer
                withContext(Dispatchers.IO) {
                    repo.insertPoints(rid, points)
                }
                currentState = currentState.copy(points = emptyList())
            }
        }

        tracker.setState(currentState)
    }

    private fun insertRunOnFirstFix() {
        if (runInsertInFlight) return
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

    /** Splits are cut on the km boundary itself, and priced in moving time, never elapsed time. */
    private fun recordSplits() {
        while (currentState.distanceM >= nextKmMarker) {
            val movingMsForSplit = currentState.movingMs - lastKmMovingMs
            val kmPaceSec = if (movingMsForSplit > 0) movingMsForSplit / 1000f else 0f
            currentState = currentState.copy(splits = currentState.splits + kmPaceSec)
            lastKmMovingMs = currentState.movingMs
            nextKmMarker += 1000f
            Log.d(TAG, "Split recorded at ${currentState.distanceM}m: ${kmPaceSec.toInt()}s")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val nowMs = graph.clock.nowMs()
        val spm = when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> cadence.onStepCount(event.values[0].toLong(), nowMs)
            Sensor.TYPE_ACCELEROMETER ->
                cadence.onAccelerometer(event.values[0], event.values[1], event.values[2], nowMs)
            else -> return
        }
        if (spm != currentState.cadenceSpm) {
            currentState = currentState.copy(cadenceSpm = spm)
            tracker.setState(currentState)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

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
