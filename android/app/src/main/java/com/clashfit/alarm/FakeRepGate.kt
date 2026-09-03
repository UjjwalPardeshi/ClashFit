package com.clashfit.alarm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Button-driven fake rep gate for testing and emulator use.
 * Manually increment reps via buttonPressed().
 */
class FakeRepGate(private val target: Int = 5) : RepGate {

    private val _state = MutableStateFlow(RepGateEvent(counted = 0, target = target, lastVerdict = null, cue = null))

    override fun observe(exerciseId: String): Flow<RepGateEvent> = _state.asStateFlow()

    override fun reset() {
        _state.value = RepGateEvent(counted = 0, target = target, lastVerdict = null, cue = null)
    }

    /** Simulate a rep being counted. */
    fun simulateRep(verdict: String = "CLEAN", cue: String? = null) {
        val current = _state.value
        if (current.counted < current.target) {
            _state.value = RepGateEvent(
                counted = current.counted + 1,
                target = current.target,
                lastVerdict = verdict,
                cue = cue
            )
        }
    }

    /** Simulates the alarm being snoozed. */
    fun snooze() {
        reset()
    }
}
