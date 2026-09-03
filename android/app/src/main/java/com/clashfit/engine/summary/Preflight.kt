package com.clashfit.engine.summary

/**
 * Pre-demo checklist: verify camera, inference, config, library, offline status, audio,
 * speech, haptics, storage, calibration, and camera-free fallback before a demo.
 */

enum class CheckStatus { PASS, WARN, FAIL, SKIP }

data class Check(
    val id: String,
    val label: String,
    val status: CheckStatus,
    val detail: String = "",
)

data class CheckSummary(
    val pass: Int,
    val warn: Int,
    val fail: Int,
    val skip: Int,
    val ok: Boolean,
    val verdict: String,
)

data class PreflightProbe(
    val running: Boolean = false,
    val fps: Float? = null,
    val inferMs: Float? = null,
    val poseConfig: PoseConfigProbe? = null,
    val exerciseCount: Int = 0,
    val isOnline: Boolean? = null,
    val audioEnabled: Boolean = false,
    val audioState: String? = null,
    val speechEnabled: Boolean = false,
    val speechVoice: String? = null,
    val hapticsAvailable: Boolean = false,
    val hapticsEnabled: Boolean = false,
    val sessionCount: Int = 0,
    val currentStreakDays: Int = 0,
    val calibrationExists: Boolean = false,
    val calibrationTopRef: Float? = null,
    val hasTraces: Boolean = false,
)

data class PoseConfigProbe(
    val version: Int = 1,
    val debugOverlay: Boolean = false,
)

/** Run the pre-demo checklist and return a list of checks. Never throws. */
fun preflight(probe: PreflightProbe): List<Check> {
    val out = mutableListOf<Check>()

    // 1. Camera running
    if (!probe.running) {
        out.add(Check("camera", "Camera running", CheckStatus.FAIL, "Start the camera before demoing."))
    } else if ((probe.fps ?: 0f) < 12) {
        out.add(Check("camera", "Camera running", CheckStatus.WARN, "only ${probe.fps?.toInt() ?: 0} fps"))
    } else {
        out.add(Check("camera", "Camera running", CheckStatus.PASS, "${probe.fps?.toInt() ?: 0} fps"))
    }

    // 2. Inference latency
    val infer = probe.inferMs
    when {
        infer == null -> out.add(Check("infer", "Inference latency", CheckStatus.SKIP, "no frames yet"))
        infer > 40 -> out.add(Check("infer", "Inference latency", CheckStatus.FAIL,
            "${infer.toInt()}ms — likely on CPU, not GPU"))
        infer > 22 -> out.add(Check("infer", "Inference latency", CheckStatus.WARN,
            "${infer.toInt()}ms, budget is 22"))
        else -> out.add(Check("infer", "Inference latency", CheckStatus.PASS, "${infer.toInt()}ms"))
    }

    // 3. Config loaded and debug off
    val poseConfig = probe.poseConfig
    when {
        poseConfig == null -> out.add(Check("config", "Config loaded", CheckStatus.FAIL, "no pose config"))
        poseConfig.debugOverlay -> out.add(Check("config", "Debug overlay OFF", CheckStatus.FAIL,
            "debugOverlay is true in pose.json — a jury will see it"))
        else -> out.add(Check("config", "Config loaded, debug off", CheckStatus.PASS, "v${poseConfig.version}"))
    }

    // 4. Exercise library
    out.add(Check("library", "Exercise library",
        if (probe.exerciseCount > 0) CheckStatus.PASS else CheckStatus.FAIL,
        "${probe.exerciseCount} exercises"))

    // 5. Offline
    when (probe.isOnline) {
        null -> out.add(Check("offline", "Works offline", CheckStatus.SKIP, ""))
        true -> out.add(Check("offline", "Works offline", CheckStatus.WARN,
            "currently online — put it in airplane mode and confirm the fight still runs"))
        false -> out.add(Check("offline", "Works offline", CheckStatus.PASS, "offline and running"))
    }

    // 6. Audio
    if (!probe.audioEnabled) {
        out.add(Check("audio", "Audio ready", CheckStatus.WARN,
            "not unlocked yet — click anything once"))
    } else {
        val state = probe.audioState ?: "unknown"
        val status = if (state == "running") CheckStatus.PASS else CheckStatus.WARN
        out.add(Check("audio", "Audio ready", status, state))
    }

    // 7. Speech
    val speechDetail = if (probe.speechEnabled)
        (probe.speechVoice ?: "default voice")
    else
        "no speech synthesis"
    out.add(Check("speech", "Speech available",
        if (probe.speechEnabled) CheckStatus.PASS else CheckStatus.WARN,
        speechDetail))

    // 8. Haptics
    out.add(Check("haptics", "Haptics",
        if (probe.hapticsAvailable) {
            if (probe.hapticsEnabled) CheckStatus.PASS else CheckStatus.PASS
        } else CheckStatus.SKIP,
        if (probe.hapticsAvailable) {
            if (probe.hapticsEnabled) "on" else "off"
        } else "no vibration motor"))

    // 9. Storage
    out.add(Check("storage", "Local storage", CheckStatus.PASS,
        "${probe.sessionCount} sessions, streak ${probe.currentStreakDays}"))

    // 10. Calibration
    val calDetail = if (probe.calibrationExists) {
        "top ${probe.calibrationTopRef?.toInt() ?: 0}°"
    } else {
        "none saved — the first run will capture it"
    }
    out.add(Check("calibration", "Calibration for this exercise",
        if (probe.calibrationExists) CheckStatus.PASS else CheckStatus.WARN,
        calDetail))

    // 11. Camera-free fallback
    out.add(Check("fallback", "Camera-free fallback",
        if (probe.hasTraces) CheckStatus.PASS else CheckStatus.WARN,
        if (probe.hasTraces) "trace replay available"
        else "record a trace so you can demo without detection"))

    return out
}

/** Summarise check results into verdict and counts. */
fun summariseChecks(results: List<Check>): CheckSummary {
    var pass = 0
    var warn = 0
    var fail = 0
    var skip = 0

    for (r in results) {
        when (r.status) {
            CheckStatus.PASS -> pass++
            CheckStatus.WARN -> warn++
            CheckStatus.FAIL -> fail++
            CheckStatus.SKIP -> skip++
        }
    }

    val ok = fail == 0
    val verdict = when {
        fail > 0 -> "NOT READY"
        warn > 0 -> "READY, WITH WARNINGS"
        else -> "READY"
    }

    return CheckSummary(pass, warn, fail, skip, ok, verdict)
}
