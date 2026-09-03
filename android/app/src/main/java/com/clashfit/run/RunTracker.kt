package com.clashfit.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Global holder for the current run state. Bound service and UI both observe this. */
class RunTracker {
    private val _state = MutableStateFlow(RunState())
    val state = _state.asStateFlow()

    fun setState(newState: RunState) {
        _state.value = newState
    }

    fun resetState() {
        _state.value = RunState()
    }

    companion object {
        private var instance: RunTracker? = null

        fun getInstance(): RunTracker {
            return instance ?: RunTracker().also { instance = it }
        }
    }
}
