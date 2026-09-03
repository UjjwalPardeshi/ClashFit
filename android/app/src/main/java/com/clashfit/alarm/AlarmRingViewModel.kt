package com.clashfit.alarm

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clashfit.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** UI state for the alarm ring screen. */
data class AlarmRingUiState(
    val alarmId: Long = 0L,
    val exercise: String = "",
    val exerciseName: String = "",
    val targetReps: Int = 5,
    val snoozeLimitRemaining: Int = 0,
    val repEvent: RepGateEvent = RepGateEvent(0, 5, null, null),
    val isEmergencyHoldActive: Boolean = false,
    val emergencyHoldProgress: Float = 0f,
    /** The ring is over: the reps were verified, or the emergency hold ran its five seconds. */
    val finished: Boolean = false,
) {
    val remainingReps: Int get() = (repEvent.target - repEvent.counted).coerceAtLeast(0)
}

/**
 * View model for the alarm ring screen. It holds no camera and builds no rep gate: the screen owns
 * the camera lifecycle and hands the gate over with [attachGate]. Until then the state reads
 * "0 of `reps`", which is also what a user with no camera permission sees.
 */
class AlarmRingViewModel(private val context: Context, private val alarmId: Long) : ViewModel() {

    private val graph = AppGraph.of(context)
    private val dao = graph.db.alarms()

    private val _uiState = MutableStateFlow<AlarmRingUiState?>(null)
    val uiState: StateFlow<AlarmRingUiState?> = _uiState.asStateFlow()

    private var gate: RepGate? = null
    private var gateJob: Job? = null
    private var holdJob: Job? = null
    private var snoozeCount = 0

    init {
        loadAlarm()
    }

    private fun loadAlarm() {
        viewModelScope.launch(Dispatchers.IO) {
            val alarm = dao.get(alarmId)
            if (alarm == null) {
                Log.e(TAG, "No alarm $alarmId; nothing to ring for")
                return@launch
            }
            _uiState.value = AlarmRingUiState(
                alarmId = alarm.id,
                exercise = alarm.exerciseId,
                exerciseName = graph.config.exercise(alarm.exerciseId)?.name
                    ?: alarm.exerciseId.replace('_', ' '),
                // The alarm's own rep count is the target everywhere; nothing here is a constant.
                targetReps = alarm.reps,
                snoozeLimitRemaining = (alarm.snoozeLimit - snoozeCount).coerceAtLeast(0),
                repEvent = RepGateEvent(0, alarm.reps, null, null),
            )
            subscribe()
        }
    }

    /**
     * Hand the screen's live rep gate to the alarm. Safe to call again — the previous subscription
     * is dropped first — and safe to call before the alarm row has loaded.
     */
    fun attachGate(gate: RepGate) {
        this.gate = gate
        subscribe()
    }

    private fun subscribe() {
        val exercise = _uiState.value?.exercise?.takeIf { it.isNotBlank() } ?: return
        val gate = this.gate ?: return
        gateJob?.cancel()
        gateJob = viewModelScope.launch {
            gate.observe(exercise).collect { onRepCountUpdate(it) }
        }
    }

    fun onRepCountUpdate(event: RepGateEvent) {
        val state = _uiState.value ?: return
        // The gate counts to its own target; the alarm's configured rep count is the truth.
        val counted = event.counted.coerceAtMost(state.targetReps)
        _uiState.value = state.copy(
            repEvent = event.copy(counted = counted, target = state.targetReps),
        )
        if (counted >= state.targetReps) dismissAlarm("reps verified")
    }

    fun onSnoozePressed() {
        val state = _uiState.value ?: return
        if (state.snoozeLimitRemaining <= 0) return

        snoozeCount++
        // Back to zero reps, so a snoozed alarm is never half-dismissed when it comes round again.
        // The ring is deliberately not ended here: snoozing does not buy a rep-free dismissal.
        gate?.reset()
        _uiState.value = state.copy(
            snoozeLimitRemaining = state.snoozeLimitRemaining - 1,
            repEvent = RepGateEvent(0, state.targetReps, null, null),
        )

        // Reschedule the alarm for its next slot.
        viewModelScope.launch(Dispatchers.IO) {
            val alarm = dao.get(state.alarmId)
            if (alarm != null) AlarmScheduler.schedule(context, alarm)
        }
    }

    fun onEmergencyDismissPressedDown() {
        val state = _uiState.value ?: return
        if (state.isEmergencyHoldActive) return
        _uiState.value = state.copy(isEmergencyHoldActive = true, emergencyHoldProgress = 0f)

        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            var elapsedMs = 0L
            while (isActive && elapsedMs < HOLD_DURATION_MS) {
                delay(HOLD_TICK_MS)
                // A released button stops the hold: the five seconds have to be continuous.
                if (_uiState.value?.isEmergencyHoldActive != true) return@launch
                elapsedMs += HOLD_TICK_MS
                _uiState.value = _uiState.value?.copy(
                    emergencyHoldProgress = (elapsedMs.toFloat() / HOLD_DURATION_MS).coerceIn(0f, 1f),
                )
            }
            logEmergencyDismiss()
            dismissAlarm("emergency hold")
        }
    }

    fun onEmergencyDismissPressedUp() {
        val state = _uiState.value ?: return
        if (!state.isEmergencyHoldActive) return
        holdJob?.cancel()
        holdJob = null
        _uiState.value = state.copy(isEmergencyHoldActive = false, emergencyHoldProgress = 0f)
    }

    private fun logEmergencyDismiss() {
        val state = _uiState.value ?: return
        Log.d(TAG, "Emergency dismiss of alarm ${state.alarmId} after ${state.repEvent.counted} reps")
    }

    private fun dismissAlarm(reason: String) {
        val state = _uiState.value ?: return
        if (state.finished) return
        Log.d(TAG, "Alarm ${state.alarmId} dismissed: $reason")
        _uiState.value = state.copy(finished = true, isEmergencyHoldActive = false)
    }

    override fun onCleared() {
        super.onCleared()
        gateJob?.cancel()
        holdJob?.cancel()
    }

    companion object {
        private const val TAG = "ClashFit/alarm"
        private const val HOLD_DURATION_MS = 5_000L
        private const val HOLD_TICK_MS = 100L

        fun factory(context: Context, alarmId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AlarmRingViewModel(context.applicationContext, alarmId) as T
        }
    }
}
