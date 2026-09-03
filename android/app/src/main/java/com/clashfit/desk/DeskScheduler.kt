package com.clashfit.desk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clashfit.core.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Schedules the desk timer using AlarmManager with inexact setAndAllowWhileIdle.
 * Re-arms after each fire. Skips quiet hours and snooze windows.
 */
object DeskScheduler {
    private const val TAG = "ClashFit/desk"
    private const val ACTION = "com.clashfit.desk.DESK_ALARM"

    /**
     * Schedule the desk timer. Call when settings change or on boot.
     */
    fun schedule(context: Context, clock: Clock, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val graph = (context.applicationContext as? com.clashfit.ClashFitApp)?.graph
            val prefs = graph?.prefs ?: return@launch
            val settings = prefs.settings.first() ?: return@launch

            if (!settings.deskEnabled) {
                cancel(context)
                return@launch
            }

            val nextFireMs = DeskSchedule.nextFireMs(
                now = clock.nowMs(),
                intervalMin = settings.deskIntervalMin,
                quietFromHour = settings.deskQuietFromHour,
                quietToHour = settings.deskQuietToHour,
                snoozedUntilMs = settings.deskSnoozedUntilMs,
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = getPendingIntent(context)

            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMs, pendingIntent)
                Log.d(TAG, "Scheduled desk alarm for $nextFireMs (in ${(nextFireMs - clock.nowMs()) / 1000 / 60}m)")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling desk alarm", e)
            }
        }
    }

    /**
     * Cancel the desk timer.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Canceled desk alarm")
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DeskReceiver::class.java)
            .setAction(ACTION)

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
