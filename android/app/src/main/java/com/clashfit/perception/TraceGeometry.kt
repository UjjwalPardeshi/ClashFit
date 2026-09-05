package com.clashfit.perception

import com.clashfit.core.model.Landmark
import com.clashfit.core.model.Landmarks
import kotlin.math.sqrt

/**
 * Turning a recorded trace's world landmarks into image landmarks.
 *
 * A trace stores WORLD landmarks: metres, centred on the hip, y running from about -0.45 at the
 * head to +0.84 at the feet. Image landmarks are a different thing — the fraction of the camera
 * frame a joint sits at, always between 0 and 1.
 *
 * Passing the first in as the second made the framing check measure a body 1.29 frames tall, which
 * reads as "too close" forever: calibration never completed, and a replayed set sat on "step back"
 * until somebody gave up. The demo script names trace replay as the escape when a room's lighting
 * beats the camera, so that was the escape hatch failing to open.
 *
 * Pure and separate from the source so it can be tested against the shipped trace on the JVM.
 */
object TraceGeometry {

    /** The framing band the shipped pose config accepts, from config/pose.json. */
    const val BAND_MIN = 0.55f
    const val BAND_MAX = 0.92f

    /**
     * One affine map from this trace's world space into image space, fitted over the whole trace.
     *
     * Fitted once rather than per frame on purpose. A per-frame fit would rescale the body every
     * time it moved, so a squat would look like someone standing still while the room shrank — and
     * the framing check would never see the player leave the target band. One transform keeps every
     * relative movement intact and only decides where the body sits in the frame.
     *
     * The scale is chosen so the whole SET fits the band, not just the standing frames. A body is
     * genuinely shorter at the bottom of a squat than at the top — that is what a squat is — so
     * fitting the tallest frame to three quarters of the screen pushed the deepest frames down to
     * 0.54 and back outside the band. Placing the geometric mean of the tallest and shortest frames
     * at the middle of the band leaves both ends comfortably inside it.
     *
     * Landmarks the recorder never saw sit at the origin with no visibility. They are skipped when
     * measuring, because including one would stretch the box to a point that is not part of a body,
     * and passed through untouched, because moving a joint nobody saw would invent data.
     */
    fun imageTransform(frames: List<Landmarks>): (Landmark) -> Landmark {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var tallest = 0f
        var shortest = Float.MAX_VALUE

        for (frame in frames) {
            var fLo = Float.MAX_VALUE
            var fHi = -Float.MAX_VALUE
            for (lm in frame) {
                if (lm.visibility <= 0f) continue
                if (lm.x < minX) minX = lm.x
                if (lm.x > maxX) maxX = lm.x
                if (lm.y < minY) minY = lm.y
                if (lm.y > maxY) maxY = lm.y
                if (lm.y < fLo) fLo = lm.y
                if (lm.y > fHi) fHi = lm.y
            }
            if (fHi > fLo) {
                val h = fHi - fLo
                if (h > tallest) tallest = h
                if (h < shortest) shortest = h
            }
        }

        // Nothing measurable: hand the landmarks back untouched rather than divide by zero.
        if (tallest <= 0f || minY == Float.MAX_VALUE) return { it }
        if (shortest == Float.MAX_VALUE) shortest = tallest

        // Put the geometric middle of the set's own range at the geometric middle of the band, then
        // make sure the tallest frame still clears the ceiling with a little room to spare.
        val bandMid = sqrt(BAND_MIN * BAND_MAX)
        var scale = bandMid / sqrt(shortest * tallest)
        val ceiling = BAND_MAX * 0.97f
        if (tallest * scale > ceiling) scale = ceiling / tallest

        val centreX = (minX + maxX) / 2f
        val centreY = (minY + maxY) / 2f
        return { lm ->
            if (lm.visibility <= 0f) {
                lm
            } else {
                Landmark(
                    x = (0.5f + (lm.x - centreX) * scale).coerceIn(0f, 1f),
                    y = (0.5f + (lm.y - centreY) * scale).coerceIn(0f, 1f),
                    z = lm.z,
                    visibility = lm.visibility,
                )
            }
        }
    }
}
