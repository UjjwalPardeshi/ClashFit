package com.clashfit.engine.core

import com.clashfit.core.model.Landmark

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/** Synthetic rep generator for testing. Generates angle sequences at 30fps (33ms per frame). */
object Synth {
    /** Generate one rep at 30fps, top -> bottom -> top with given phase durations. */
    fun rep(
        top: Float,
        bottom: Float,
        eccSec: Float = 1.0f,
        pauseSec: Float = 0.3f,
        conSec: Float = 0.8f,
        fps: Int = 30,
        t0: Long = 0L,
    ): Pair<List<Pair<Float, Long>>, Long> {
        val samples = mutableListOf<Pair<Float, Long>>()
        val step = 1000 / fps
        var t = t0

        // Eccentric: top -> bottom
        val nEcc = maxOf(1, (eccSec * fps).toInt())
        for (i in 1..nEcc) {
            val k = i.toFloat() / nEcc
            val angle = top + (bottom - top) * k
            samples += angle to t
            t += step
        }

        // Bottom pause
        val nPause = maxOf(1, (pauseSec * fps).toInt())
        repeat(nPause) {
            samples += bottom to t
            t += step
        }

        // Concentric: bottom -> top
        val nCon = maxOf(1, (conSec * fps).toInt())
        for (i in 1..nCon) {
            val k = i.toFloat() / nCon
            val angle = bottom + (top - bottom) * k
            samples += angle to t
            t += step
        }

        return samples to t
    }

    /** Generate a whole set. `decay` shrinks range and slows the concentric, rep by rep. */
    fun set(
        reps: Int,
        top: Float,
        bottom: Float,
        fps: Int = 30,
        decay: Float = 0f,
        gapSec: Float = 0.4f,
        restGrowth: Float = 0f,
        eccSec: Float = 1.0f,
        pauseSec: Float = 0.3f,
    ): List<Pair<Float, Long>> {
        val all = mutableListOf<Pair<Float, Long>>()
        var t = 0L

        // Settle at top
        val step = 1000 / fps
        repeat(fps) {
            all += top to t
            t += step
        }

        // Generate reps
        for (r in 0 until reps) {
            val k = decay * r
            val b = bottom + (top - bottom) * minOf(0.8f, k)  // Range collapses
            val conDur = 0.8f * (1 + minOf(3f, k * 6f))  // Concentric slows
            val (repSamples, repEnd) = rep(top, b, eccSec, pauseSec, conDur, fps, t)
            all += repSamples
            t = repEnd

            // Rest gap
            val gapDur = gapSec + restGrowth * r
            repeat(maxOf(1, (gapDur * fps).toInt())) {
                all += top to t
                t += step
            }
        }

        return all
    }

    /** Generate synthetic WORLD landmarks for a given hip-knee-ankle angle (degrees). */
    fun worldFrame(angleDeg: Float, leftAngleDeg: Float? = null, rightAngleDeg: Float? = null): List<Landmark> {
        val lm = MutableList(33) { Landmark(0f, 0f, 0f, 0f) }

        // Default initialization
        for (i in 0 until 33) {
            lm[i] = Landmark(0f, 0f, 0f, 0.95f)
        }

        // Left and right sides
        for (side in listOf(0, 1)) {
            val sx = if (side == 0) -0.12f else 0.12f
            val angle = when {
                side == 0 && leftAngleDeg != null -> leftAngleDeg
                side == 1 && rightAngleDeg != null -> rightAngleDeg
                else -> angleDeg
            }

            val a = (angle * PI) / 180.0
            val sin_a = sin(a).toFloat()
            val cos_a = cos(a).toFloat()

            // Shoulder
            lm[11 + side] = Landmark(sx, -0.45f, 0f, 0.95f)
            // Elbow
            lm[13 + side] = Landmark(sx, -0.25f, 0f, 0.9f)
            // Wrist
            lm[15 + side] = Landmark(sx, -0.05f, 0f, 0.9f)
            // Hip
            lm[23 + side] = Landmark(sx, 0f, 0f, 0.95f)
            // Knee
            lm[25 + side] = Landmark(sx, 0.42f, 0f, 0.95f)
            // Ankle (varies with angle)
            val ankleX = sx + 0.42f * sin_a
            val ankleY = 0.42f + 0.42f * cos_a
            val ankle = Landmark(ankleX, ankleY, 0f, 0.95f)
            lm[27 + side] = ankle  // Ankle
            lm[29 + side] = ankle  // Heel
            lm[31 + side] = ankle  // Foot index
        }

        return lm
    }

    /** Generate a full set as (landmarks, tMs) frames, ready for SessionEngine. */
    fun worldSet(
        reps: Int,
        top: Float = 172f,
        bottom: Float = 80f,
        fps: Int = 30,
        decay: Float = 0f,
    ): List<Pair<List<Landmark>, Long>> {
        val frames = mutableListOf<Pair<List<Landmark>, Long>>()
        var t = 0L
        val step = 1000 / fps

        // Settle at top
        repeat(45) {
            frames += worldFrame(top) to t
            t += step
        }

        // Generate reps
        val angles = set(reps, top, bottom, fps, decay)
        for ((angle, angleTime) in angles) {
            frames += worldFrame(angle) to (angleTime + t)
        }

        return frames
    }
}
