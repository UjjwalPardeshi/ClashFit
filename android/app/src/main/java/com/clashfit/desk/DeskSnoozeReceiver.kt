package com.clashfit.desk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the snooze action from the desk timer notification.
 * Snoozes for 2 hours and re-arms the alarm.
 */
class DeskSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DeskSnoozeReceiver: snoozing for 2 hours")

        val graph = (context.applicationContext as? com.clashfit.ClashFitApp)?.graph ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val snoozedUntilMs = graph.clock.nowMs() + 2 * 60 * 60 * 1000 // 2 hours
                graph.prefs.setDeskSnoozedUntilMs(snoozedUntilMs)

                // Re-arm the alarm
                DeskScheduler.schedule(context, graph.clock, scope)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ClashFit/desk"
    }
}
