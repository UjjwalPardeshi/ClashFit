package com.clashfit.alarm

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clashfit.AppGraph
import com.clashfit.core.pose.CameraFacing
import com.clashfit.data.AlarmDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/** UI state for the alarm ring screen. */
data class AlarmRingUiState(
    val alarmId: Long = 0L,
    val exercise: String = "",
    val targetReps: Int = 5,
    val snoozeLimitRemaining: Int = 0,
    val repEvent: RepGateEvent = RepGateEvent(0, 5, null, null),
    val isEmergencyHoldActive: Boolean = false,
    val emergencyHoldProgress: Float = 0f,
)

/** View model for the alarm ring screen. */
class AlarmRingViewModel(private val context: Context, private val alarmId: Long) : ViewModel() {

    private val graph = AppGraph.of(context)
    private val dao = graph.db.alarms()

    private val _uiState = MutableStateFlow<AlarmRingUiState?>(null)
    val uiState: StateFlow<AlarmRingUiState?> = _uiState.asStateFlow()

    private val _repGate: RepGate
    val repGate: RepGate get() = _repGate

    private var snoozeCount = 0

    init {
        // Use FakeRepGate for now; real implementation would use RealRepGate with MediaPipePoseSource
        _repGate = FakeRepGate(target = 5)
        loadAlarm()
    }

    private fun loadAlarm() {
        viewModelScope.launch(Dispatchers.IO) {
            val alarm = dao.get(alarmId)
            if (alarm != null) {
                _uiState.value = AlarmRingUiState(
                    alarmId = alarm.id,
                    exercise = alarm.exerciseId,
                    targetReps = alarm.reps,
                    snoozeLimitRemaining = alarm.snoozeLimit - snoozeCount,
                    repEvent = RepGateEvent(0, alarm.reps, null, null)
                )

                // Update rep gate to use the correct target
                if (_repGate is FakeRepGate) {
                    // The FakeRepGate is pre-initialized; in a real scenario,
                    // we'd recreate it with the alarm's target reps
                }
            }
        }
    }

    fun onRepCountUpdate(event: RepGateEvent) {
        val state = _uiState.value ?: return
        _uiState.value = state.copy(repEvent = event)

        // If we've reached the target, dismiss the alarm
        if (event.counted >= event.target) {
            dismissAlarm()
        }
    }

    fun onSnoozePressed() {
        val state = _uiState.value ?: return
        if (snoozeCount < state.snoozeLimitRemaining) {
            snoozeCount++
            _uiState.value = state.copy(snoozeLimitRemaining = state.snoozeLimitRemaining - 1)

            // Reset the rep counter and the gate
            _repGate.reset()
            _uiState.value = _uiState.value?.copy(
                repEvent = RepGateEvent(0, state.targetReps, null, null)
            )

            // Reschedule the alarm for the next day
            viewModelScope.launch(Dispatchers.IO) {
                val alarm = _uiState.value?.let { dao.get(it.alarmId) }
                if (alarm != null) {
                    AlarmScheduler.schedule(context, alarm)
                }
            }
        }
    }

    fun onEmergencyDismissPressedDown() {
        val state = _uiState.value ?: return
        _uiState.value = state.copy(isEmergencyHoldActive = true)

        // Simulate the 5-second hold (in a real scenario, this would be called 10 times per second)
        var elapsedMs = 0L
        val holdDurationMs = 5000L

        viewModelScope.launch {
            repeat(50) {
                elapsedMs += 100
                _uiState.value = _uiState.value?.copy(
                    emergencyHoldProgress = (elapsedMs.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                )

                if (elapsedMs >= holdDurationMs) {
                    // Log the emergency dismiss
                    logEmergencyDismiss()
                    dismissAlarm()
                }

                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun onEmergencyDismissPressedUp() {
        val state = _uiState.value ?: return
        if (!state.isEmergencyHoldActive) return

        _uiState.value = state.copy(isEmergencyHoldActive = false, emergencyHoldProgress = 0f)
    }

    private fun logEmergencyDismiss() {
        val state = _uiState.value ?: return
        Log.d("ClashFit/alarm", "Emergency dismiss of alarm ${state.alarmId}")
        viewModelScope.launch(Dispatchers.IO) {
            // Could persist this to a DataStore or the alarm entity if needed
        }
    }

    private fun dismissAlarm() {
        Log.d("ClashFit/alarm", "Alarm dismissed")
        // The activity will handle finishing itself by observing when the rep count reaches target
    }
}
