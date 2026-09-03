package com.clashfit.desk

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clashfit.MainActivity
import com.clashfit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives the desk timer alarm. Posts a high-priority notification with the exercise.
 * Tapping the notification opens MainActivity and navigates to a 60-second session.
 * "NOT NOW · 2H" action snoozes the alarm.
 */
class DeskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DeskReceiver: firing")

        val graph = (context.applicationContext as? com.clashfit.ClashFitApp)?.graph ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val prefs = graph.prefs
                val settings = prefs.settings.first() ?: return@launch

                // Post notification
                val exerciseId = settings.deskExerciseId
                val reps = settings.deskReps
                postNotification(context, exerciseId, reps)

                // Re-arm the alarm
                DeskScheduler.schedule(context, graph.clock, scope)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, exerciseId: String, reps: Int) {
        ensureNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DESK_EXERCISE_ID, exerciseId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, DeskSnoozeReceiver::class.java)
            .setAction("com.clashfit.desk.SNOOZE")

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, DESK_CHANNEL_ID)
            .setContentTitle("SIXTY SECONDS")
            .setContentText("$exerciseId · $reps reps")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "NOT NOW · 2H", snoozePendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                return
            }
        }
        notificationManager?.notify(DESK_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            DESK_CHANNEL_ID,
            context.getString(R.string.notif_channel_desk),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "Desk timer reminders"
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ClashFit/desk"
        const val EXTRA_DESK_EXERCISE_ID = "desk_exercise_id"
        private const val DESK_CHANNEL_ID = "desk"
        private const val DESK_NOTIFICATION_ID = 43
    }
}
