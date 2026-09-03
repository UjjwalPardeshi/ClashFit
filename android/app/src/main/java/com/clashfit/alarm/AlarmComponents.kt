package com.clashfit.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.clashfit.data.AlarmEntity
import com.clashfit.data.AlarmDao
import com.clashfit.ui.theme.ClashFitTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val Context.alarmDataStore by preferencesDataStore("alarm_prefs")

/** Receives the broadcast when an alarm fires. Starts AlarmService. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        Log.d("ClashFit/alarm", "AlarmReceiver: firing alarm $alarmId")
        AlarmService.start(context, alarmId)
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION = "com.clashfit.alarm.ALARM_BROADCAST"
    }
}

/** Reschedules enabled alarms after reboot or time change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ClashFit/alarm", "BootReceiver: rescheduling on ${intent.action}")
        AlarmScheduler.rescheduleAll(context)
    }
}

/** Foreground service that plays the alarm tone and shows a persistent notification. */
class AlarmService : Service() {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, 0L) ?: 0L
        Log.d("ClashFit/alarm", "AlarmService.onStartCommand: $alarmId")

        ensureNotificationChannel(this)

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            Intent(this, AlarmRingActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("Alarm")
            .setContentText("Wake up!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(false)
            .build()

        startForeground(ALARM_NOTIFICATION_ID, notification)

        // Start playing the alarm tone
        startAlarmTone()

        // Open the ring activity (it will dismiss the service when the alarm is handled)
        startActivity(
            Intent(this, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        )

        return START_STICKY
    }

    private fun startAlarmTone() {
        if (isPlaying) return
        isPlaying = true

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 44100
                val duration = 60000 // 60 seconds loop
                val frequency = 800f // Hz

                val audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    android.media.AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    sampleRate * 2, // 2 seconds buffer
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                this@AlarmService.audioTrack = audioTrack
                audioTrack.play()

                val chunk = ShortArray(sampleRate / 10) // 100ms chunks
                val durationMs = duration
                val startTime = System.currentTimeMillis()

                while (isPlaying && (System.currentTimeMillis() - startTime) < durationMs) {
                    val elapsedMs = System.currentTimeMillis() - startTime
                    for (i in chunk.indices) {
                        val t = (elapsedMs + i * 1000L / sampleRate) / 1000.0
                        val value = (sin(2 * PI * frequency * t) * 32767).toInt().toShort()
                        chunk[i] = value
                    }
                    audioTrack.write(chunk, 0, chunk.size)
                }

                audioTrack.stop()
                audioTrack.release()
                this@AlarmService.audioTrack = null
            } catch (e: Exception) {
                Log.e("ClashFit/alarm", "Error playing alarm tone", e)
            }
        }
    }

    fun stopAlarm() {
        isPlaying = false
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e("ClashFit/alarm", "Error stopping audio", e)
            }
        }
        audioTrack = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    companion object {
        private const val ALARM_CHANNEL_ID = "alarm"
        private const val ALARM_NOTIFICATION_ID = 42

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Wake-up alarm notifications"
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }
}

/** The activity that rings and dismisses the alarm through reps. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, 0L)

        setContent {
            ClashFitTheme {
                AlarmRingScreen(
                    alarmId = alarmId,
                    onDismissed = {
                        // Stop the foreground service
                        stopService(Intent(this, AlarmService::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

/** Schedules and manages alarms. */
object AlarmScheduler {
    private const val TAG = "ClashFit/alarm"

    /** Schedule all enabled alarms. Call on boot and after time change. */
    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = getAlarmDao(context) ?: return@launch
            val enabled = dao.enabled()
            Log.d(TAG, "Rescheduling ${enabled.size} enabled alarms")
            enabled.forEach { alarm ->
                scheduleAlarm(context, alarm)
            }
        }
    }

    /** Schedule a single alarm from its entity. */
    fun schedule(context: Context, alarm: AlarmEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            scheduleAlarm(context, alarm)
        }
    }

    /** Cancel an alarm. */
    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context, alarmId)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Canceled alarm $alarmId")
    }

    private suspend fun scheduleAlarm(context: Context, alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we can schedule exact alarms
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (!canScheduleExact) {
            Log.w(TAG, "Cannot schedule exact alarm for ${alarm.id}; opening settings")
            openExactAlarmSettings(context)
            return
        }

        val nextTime = getNextTriggerTime(alarm)
        val pendingIntent = getPendingIntent(context, alarm.id)

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(nextTime, pendingIntent),
                pendingIntent
            )
            Log.d(TAG, "Scheduled alarm ${alarm.id} for $nextTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm ${alarm.id}", e)
            // Fall back to setExactAndAllowWhileIdle
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled alarm ${alarm.id} (fallback) for $nextTime")
            } catch (e2: SecurityException) {
                Log.e(TAG, "Also failed to setExactAndAllowWhileIdle for ${alarm.id}", e2)
            }
        }
    }

    private fun getPendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)

        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNextTriggerTime(alarm: AlarmEntity): Long {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now

        // If daysMask is 0, it's a one-shot alarm
        if (alarm.daysMask == 0) {
            val target = java.util.Calendar.getInstance()
            target.timeInMillis = now
            target.set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
            target.set(java.util.Calendar.MINUTE, alarm.minute)
            target.set(java.util.Calendar.SECOND, 0)

            return if (target.timeInMillis <= now) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
                target.timeInMillis
            } else {
                target.timeInMillis
            }
        }

        // Recurring alarm
        while (true) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(java.util.Calendar.MINUTE, alarm.minute)
            cal.set(java.util.Calendar.SECOND, 0)

            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val dayBit = when (dayOfWeek) {
                java.util.Calendar.MONDAY -> 0
                java.util.Calendar.TUESDAY -> 1
                java.util.Calendar.WEDNESDAY -> 2
                java.util.Calendar.THURSDAY -> 3
                java.util.Calendar.FRIDAY -> 4
                java.util.Calendar.SATURDAY -> 5
                java.util.Calendar.SUNDAY -> 6
                else -> -1
            }

            if (dayBit >= 0 && (alarm.daysMask and (1 shl dayBit)) != 0) {
                if (cal.timeInMillis > now) {
                    return cal.timeInMillis
                }
            }
        }
    }

    private fun openExactAlarmSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            } else {
                Intent(android.provider.Settings.ACTION_SETTINGS)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings", e)
        }
    }

    private fun getAlarmDao(context: Context): AlarmDao? = try {
        val app = context.applicationContext as? android.app.Application
        val graph = (app as? com.clashfit.ClashFitApp)?.graph
        graph?.db?.alarms()
    } catch (e: Exception) {
        Log.e(TAG, "Could not get AlarmDao", e)
        null
    }
}
