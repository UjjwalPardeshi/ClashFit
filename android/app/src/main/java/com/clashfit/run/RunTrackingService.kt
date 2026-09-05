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
import com.clashfit.core.util.Clock
import com.clashfit.data.ActivityKind
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

    // Indoors every GPS fix is wider than the accuracy gate and gets thrown away, so a run in a
    // lab recorded nothing at all. These two carry the activity when the sky is not available:
    // the step counter and the compass say how far and which way, and the fuser decides which
    // source is currently worth believing. See PositionFuser for the handover.
    private val deadReckoning = DeadReckoning()
    private val fuser = PositionFuser()
    private val rotationMatrix = FloatArray(9)
    private val orientationOut = FloatArray(3)

    /**
     * The activity's origin in latitude and longitude.
     *
     * Everything downstream works in metres east and north of it. It is the first accepted fix
     * where there is one, and the last known location where there is not — because a route drawn
     * from steps alone still has to be pinned somewhere to appear on a map.
     */
    private var originLat: Double? = null
    private var originLon: Double? = null
    private var lastPointMs: Long? = null

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
            ACTION_START -> startRun(intent.getStringExtra(EXTRA_KIND) ?: ActivityKind.RUN)
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
    private fun startRun(kind: String) {
        if (currentState.isRunning) return
        Log.d(TAG, "Starting $kind")

        val nowMs = graph.clock.nowMs()
        currentState = RunState(
            runId = null, // Will be set after first valid GPS fix
            startedAtMs = nowMs,
            kind = kind,
        )
        fixFilter.start(nowMs)
        elevation.reset()
        cadence.reset()
        movingTime.reset()
        deadReckoning.reset()
        fuser.reset()
        originLat = null
        originLon = null
        lastPointMs = null
        lastKmMovingMs = 0L
        nextKmMarker = 1000f
        seedOriginFromLastKnown()

        // Acquire wake lock to ensure GPS doesn't get throttled
        // Bounded on purpose. An activity that is started and never finished — the phone is put
        // down, the app is killed — would otherwise hold the CPU awake until the battery went.
        // Four hours is longer than any activity this app expects and short enough to be a floor
        // under that mistake.
        wakeLock?.acquire(WAKELOCK_LIMIT_MS)

        // Start location updates at 1 Hz with no max update delay to prevent batching
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(1500L) // Prevent GPS batching that would reduce accuracy
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        requestUpdatesGuarded(locationRequest)

        // Register step counter, fallback to accelerometer
        registerMotionSensors()

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
        fuser.clearAnchor()
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

        registerMotionSensors()

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

                // The fastest kilometre is computed once, here, from every point the activity
                // stored — a sliding window, so a quick kilometre that straddles a split boundary
                // still counts. Storing it turns every later personal-best check into one indexed
                // query instead of reloading the points of every previous activity.
                val fastestKm = withContext(Dispatchers.IO) {
                    runCatching { fastestKmSec(repo.getRunPoints(rid)) }.getOrNull()
                }

                withContext(Dispatchers.IO) {
                    repo.finishRun(
                        rid, endedAtMs, currentState.distanceM, currentState.movingMs,
                        currentState.avgPaceSecPerKm, currentState.cadenceSpm,
                        currentState.elevationGainM, currentState.splits.joinToString(","),
                        steps = currentState.steps,
                        fastestKmSec = fastestKm,
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
        if (currentState.runId == null) {
            insertRunOnFirstFix()
            return
        }

        if (originLat == null) {
            originLat = fix.lat
            originLon = fix.lon
        }
        val local = metresEastNorth(originLat!!, originLon!!, fix.lat, fix.lon)
        // The satellite measured this stretch, so it also gets to teach the stride that will
        // carry the activity when the next doorway takes the sky away.
        fuser.onGpsFix(local.eastM, local.northM, accepted.distanceM, fix.tMs, deadReckoning)
        commitPosition(fix.tMs, accepted.speedMps, fix.altM, fix.accuracyM)
    }

    /**
     * Records the fused position, whichever sensor produced it.
     *
     * One path for both sources on purpose: a route point, the distance, the moving clock, the
     * splits and the batch flush must behave identically indoors and out, or the two halves of an
     * activity that crosses a doorway would disagree about what happened.
     */
    private fun commitPosition(tMs: Long, speedMps: Float, altM: Double?, accuracyM: Float) {
        val rid = currentState.runId ?: return
        val lat = originLat
        val lon = originLon

        val mPerDegLat = 111_320.0
        val previous = lastPointMs
        val deltaMs = if (previous == null) 0L else (tMs - previous).coerceAtLeast(0L)
        lastPointMs = tMs

        // With no origin there is no coordinate to record — the phone has never seen the sky and
        // has no last known location either. The activity is still real: the distance, the moving
        // clock and the splits all keep counting, and only the map has nothing to draw.
        val points = if (lat == null || lon == null) currentState.points else {
            val mPerDegLon = mPerDegLat * kotlin.math.cos(lat * Math.PI / 180.0)
            currentState.points + RunPointEntity(
                runId = rid,
                tMs = tMs,
                lat = lat + fuser.northM / mPerDegLat,
                lon = lon + fuser.eastM / mPerDegLon,
                altM = altM ?: 0.0,
                accuracyM = accuracyM,
                speedMps = speedMps,
            )
        }

        movingTime.onFix(tMs, speedMps, deltaMs)
        currentState = currentState.copy(
            distanceM = fuser.distanceM,
            points = points,
            movingMs = movingTime.movingMs,
            isMoving = movingTime.isMoving,
            indoors = fuser.indoors,
        )

        recordSplits()

        altM?.let { currentState = currentState.copy(elevationGainM = elevation.onAltitude(it)) }

        if (currentState.distanceM > 0 && currentState.movingMs > 0) {
            currentState = currentState.copy(
                avgPaceSecPerKm = (currentState.movingMs / 1000f) / (currentState.distanceM / 1000f),
            )
        }

        if (points.size >= BATCH_SIZE) {
            lifecycleScope.launch {
                // Use withContext to AWAIT the DB write before clearing buffer
                withContext(Dispatchers.IO) { repo.insertPoints(rid, points) }
                currentState = currentState.copy(points = emptyList())
            }
        }

        tracker.setState(currentState)
    }

    /**
     * Registers the sensors that carry an activity indoors.
     *
     * The step counter is the distance and the rotation vector is the direction. Where there is no
     * hardware step counter the accelerometer stands in for it, counting footfalls; it is worse,
     * and it is far better than a blank screen in a basement.
     */
    private fun registerMotionSensors() {
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            // The estimator takes its baseline from the first reading together with that
            // reading's own timestamp, so the gap until the sensor first fires cannot skew cadence.
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        // Heading. Without it a step has a length but no direction, and dead reckoning cannot run.
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (rotation != null) {
            sensorManager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.w(TAG, "No rotation sensor; indoor tracking will not have a heading.")
        }
    }

    /**
     * Pins the activity to a coordinate before the first fix arrives.
     *
     * An activity that begins indoors may never see a usable fix, and a route with no origin
     * cannot be drawn on a map at all. The last known location is coarse — it may be the last
     * place the phone saw the sky — but it is the right neighbourhood, which is all the origin
     * has to be. If there is not even that, the route is still recorded and simply has no
     * position on a street map.
     */
    @SuppressLint("MissingPermission")
    private fun seedOriginFromLastKnown() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && originLat == null) {
                originLat = location.latitude
                originLon = location.longitude
                Log.d(TAG, "Origin seeded from the last known location.")
            }
        }
    }

    private fun insertRunOnFirstFix() {

        if (runInsertInFlight) return
        runInsertInFlight = true
        lifecycleScope.launch {
            val rid = try {
                withContext(Dispatchers.IO) {
                    repo.insertRun(
                        distanceM = 0f, movingMs = 0L, pace = 0f, cadence = 0, elev = 0f,
                        splits = "", kind = currentState.kind,
                    )
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
        if (currentState.isPaused) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationOut)
                // getOrientation gives azimuth in radians, anticlockwise from north; the dead
                // reckoner wants degrees clockwise, and normalises the wrap itself.
                deadReckoning.setHeading(Math.toDegrees(orientationOut[0].toDouble()).toFloat())
                return
            }
            Sensor.TYPE_STEP_COUNTER -> cadence.onStepCount(event.values[0].toLong(), nowMs)
            Sensor.TYPE_ACCELEROMETER ->
                cadence.onAccelerometer(event.values[0], event.values[1], event.values[2], nowMs)
            else -> return
        }

        val spm = cadence.cadenceSpm
        val steps = cadence.stepsTaken

        // Indoors there may never be a fix to open the run row, so the first counted step does it.
        // Without this an activity that starts in a building is never written down at all.
        if (currentState.runId == null && steps > 0) {
            insertRunOnFirstFix()
            return
        }

        // No fix has ever landed, so this activity began somewhere without a sky. Say so now
        // rather than after the first step happens to move the estimate.
        if (!fuser.hasEverFixed) fuser.seedOrigin()

        val movedIndoors = fuser.onSteps(deadReckoning.onSteps(steps), nowMs)
        if (movedIndoors) {
            // No Doppler speed indoors; the step rate is the honest substitute.
            val speedMps = if (spm > 0) deadReckoning.strideM * spm / 60f else 0f
            commitPosition(nowMs, speedMps, altM = null, accuracyM = INDOOR_ACCURACY_M)
        }

        if (spm != currentState.cadenceSpm || steps != currentState.steps) {
            currentState = currentState.copy(cadenceSpm = spm, steps = steps)
            tracker.setState(currentState)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun buildNotification(): Notification {
        val distKm = (currentState.distanceM / 1000f)
        val content = "%.2f km • %s".format(distKm, formatDuration(currentState.movingMs))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (currentState.isWalk) "Walk in progress" else "Run in progress")
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

        /**
         * The accuracy recorded against a dead-reckoned point.
         *
         * Not a GPS figure and not pretending to be one: it marks the point as an estimate so
         * that anything reading the stored route later can tell the two apart.
         */
        private const val INDOOR_ACCURACY_M = 99f

        /** The longest an activity may hold the CPU awake. */
        private const val WAKELOCK_LIMIT_MS = 4L * 60 * 60 * 1000
        const val ACTION_START = "com.clashfit.run.START"
        const val ACTION_PAUSE = "com.clashfit.run.PAUSE"
        const val ACTION_RESUME = "com.clashfit.run.RESUME"
        const val ACTION_STOP = "com.clashfit.run.STOP"

        /** Extra naming which activity this is; see [ActivityKind]. */
        const val EXTRA_KIND = "com.clashfit.run.KIND"

        fun start(context: Context, kind: String = ActivityKind.RUN) = ContextCompat.startForegroundService(
            context,
            Intent(context, RunTrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_KIND, kind),
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
