package com.clashfit.engine.summary

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreflightTest {

    @Test
    fun `preflight - a healthy setup reads READY`() {
        val probe = PreflightProbe(
            running = true,
            fps = 30f,
            inferMs = 14f,
            poseConfig = PoseConfigProbe(version = 1, debugOverlay = false),
            exerciseCount = 20,
            isOnline = false,
            audioEnabled = true,
            audioState = "running",
            speechEnabled = true,
            speechVoice = "en-IN",
            hapticsAvailable = true,
            hapticsEnabled = true,
            sessionCount = 5,
            currentStreakDays = 3,
            calibrationExists = true,
            calibrationTopRef = 170f,
            hasTraces = true,
        )

        val results = preflight(probe)
        val summary = summariseChecks(results)
        assertEquals(0, summary.fail)
        assertTrue(summary.ok)
    }

    @Test
    fun `preflight - a debug overlay left on is a hard FAIL`() {
        val probe = PreflightProbe(
            poseConfig = PoseConfigProbe(version = 1, debugOverlay = true),
        )

        val results = preflight(probe)
        val configCheck = results.find { it.id == "config" }!!
        assertEquals(CheckStatus.FAIL, configCheck.status)
        assertTrue(configCheck.detail.contains("debugOverlay"))
    }

    @Test
    fun `preflight - a stopped camera fails, a slow one warns`() {
        val stoppedProbe = PreflightProbe(running = false)
        val stoppedResults = preflight(stoppedProbe)
        val stoppedCamera = stoppedResults.find { it.id == "camera" }!!
        assertEquals(CheckStatus.FAIL, stoppedCamera.status)

        val slowProbe = PreflightProbe(running = true, fps = 8f)
        val slowResults = preflight(slowProbe)
        val slowCamera = slowResults.find { it.id == "camera" }!!
        assertEquals(CheckStatus.WARN, slowCamera.status)
    }

    @Test
    fun `preflight - CPU-speed inference fails, over-budget warns`() {
        val failProbe = PreflightProbe(inferMs = 90f)
        val failResults = preflight(failProbe)
        val inferCheck = failResults.find { it.id == "infer" }!!
        assertEquals(CheckStatus.FAIL, inferCheck.status)

        val warnProbe = PreflightProbe(inferMs = 30f)
        val warnResults = preflight(warnProbe)
        val warnInfer = warnResults.find { it.id == "infer" }!!
        assertEquals(CheckStatus.WARN, warnInfer.status)

        val passProbe = PreflightProbe(inferMs = 14f)
        val passResults = preflight(passProbe)
        val passInfer = passResults.find { it.id == "infer" }!!
        assertEquals(CheckStatus.PASS, passInfer.status)
    }

    @Test
    fun `preflight - no camera-free fallback is a warning, not silence`() {
        val probe = PreflightProbe(hasTraces = false)
        val results = preflight(probe)
        val fallback = results.find { it.id == "fallback" }!!
        assertEquals(CheckStatus.WARN, fallback.status)
        assertTrue(fallback.detail.lowercase().contains("trace"))
    }

    @Test
    fun `preflight - never throws, however broken the context`() {
        val results1 = preflight(PreflightProbe())
        assertTrue(results1.isNotEmpty())
        assertTrue(results1.all { it.status in setOf(CheckStatus.PASS, CheckStatus.WARN, CheckStatus.FAIL, CheckStatus.SKIP) })

        val results2 = preflight(PreflightProbe(poseConfig = null))
        assertTrue(results2.isNotEmpty())

        val results3 = preflight(PreflightProbe(isOnline = null))
        assertTrue(results3.isNotEmpty())
    }

    @Test
    fun `preflight - covers every item in the pre-demo ritual`() {
        val results = preflight(PreflightProbe(
            running = true,
            fps = 30f,
            inferMs = 14f,
            poseConfig = PoseConfigProbe(debugOverlay = false),
            exerciseCount = 10,
            isOnline = false,
            audioEnabled = true,
            audioState = "running",
            speechEnabled = true,
            hapticsAvailable = true,
            hasTraces = true,
        ))
        val ids = results.map { it.id }.toSet()
        val required = setOf("camera", "infer", "config", "library", "offline",
            "audio", "speech", "haptics", "storage", "calibration", "fallback")
        for (id in required) {
            assertTrue(ids.contains(id), "missing: $id")
        }
    }

    @Test
    fun `summarise - verdict reflects fail count`() {
        val checks = listOf(
            Check("a", "A", CheckStatus.PASS),
            Check("b", "B", CheckStatus.FAIL),
        )
        val summary = summariseChecks(checks)
        assertEquals(1, summary.fail)
        assertFalse(summary.ok)
        assertEquals("NOT READY", summary.verdict)
    }

    @Test
    fun `summarise - ready with warnings when no failures`() {
        val checks = listOf(
            Check("a", "A", CheckStatus.PASS),
            Check("b", "B", CheckStatus.WARN),
        )
        val summary = summariseChecks(checks)
        assertEquals(0, summary.fail)
        assertEquals(1, summary.warn)
        assertTrue(summary.ok)
        assertEquals("READY, WITH WARNINGS", summary.verdict)
    }

    @Test
    fun `summarise - ready when all pass`() {
        val checks = listOf(
            Check("a", "A", CheckStatus.PASS),
            Check("b", "B", CheckStatus.PASS),
        )
        val summary = summariseChecks(checks)
        assertEquals(0, summary.fail)
        assertEquals(0, summary.warn)
        assertTrue(summary.ok)
        assertEquals("READY", summary.verdict)
    }

    @Test
    fun `preflight - exercise library`() {
        val probe = PreflightProbe(exerciseCount = 20)
        val results = preflight(probe)
        val library = results.find { it.id == "library" }!!
        assertEquals(CheckStatus.PASS, library.status)
        assertTrue(library.detail.contains("20"))
    }

    @Test
    fun `preflight - offline status`() {
        val onlineProbe = PreflightProbe(isOnline = true)
        val onlineResults = preflight(onlineProbe)
        val onlineCheck = onlineResults.find { it.id == "offline" }!!
        assertEquals(CheckStatus.WARN, onlineCheck.status)

        val offlineProbe = PreflightProbe(isOnline = false)
        val offlineResults = preflight(offlineProbe)
        val offlineCheck = offlineResults.find { it.id == "offline" }!!
        assertEquals(CheckStatus.PASS, offlineCheck.status)
    }
}
