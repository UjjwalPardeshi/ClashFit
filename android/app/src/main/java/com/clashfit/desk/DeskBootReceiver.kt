package com.clashfit.desk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the desk timer after boot if it's enabled.
 * Triggered by the boot completion broadcast.
 */
class DeskBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DeskBootReceiver: re-arming on ${intent.action}")

        val graph = (context.applicationContext as? com.clashfit.ClashFitApp)?.graph ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val prefs = graph.prefs
                val settings = prefs.settings.first() ?: return@launch

                if (settings.deskEnabled) {
                    DeskScheduler.schedule(context, graph.clock, scope)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ClashFit/desk"
    }
}
