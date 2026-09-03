package com.clashfit.engine.summary

import com.clashfit.core.model.Landmark
import com.clashfit.core.pose.SyntheticBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostureTest {
    @Test
    fun `upright posture scores 90+`() {
        // Fully upright figure
        val landmarks = SyntheticBody.world(kneeDeg = 170f, elbowDeg = 170f, visibility = 1f)
        val sample = PostureScorer.score(landmarks, null)
        assertNotNull(sample)
        assertTrue(sample.score >= 90, "Upright should score 90+, got ${sample.score}")
    }

    @Test
    fun `30-degree forward head scores in middle range`() {
        // Build a figure with 30° forward head flexion
        val landmarks = createLandmarksWithNeckFlexion(30f)
        val sample = PostureScorer.score(landmarks, null)
        assertNotNull(sample)
        assertTrue(sample.score in 35..65, "30° flexion should score middle range, got ${sample.score}")
    }

    @Test
    fun `hunched posture scores low`() {
        // Build a figure with severe forward head (40°+)
        val landmarks = createLandmarksWithNeckFlexion(40f)
        val sample = PostureScorer.score(landmarks, null)
        assertNotNull(sample)
        assertTrue(sample.score < 30, "Hunched should score low, got ${sample.score}")
    }

    @Test
    fun `poor visibility returns null`() {
        // All landmarks with low visibility
        val landmarks = SyntheticBody.world(kneeDeg = 170f, visibility = 0.2f)
        val sample = PostureScorer.score(landmarks, null)
        assertNull(sample, "Low visibility should return null")
    }

    @Test
    fun `missing critical landmarks returns null`() {
        val landmarks = MutableList(33) { Landmark(0f, 0f, 0f, 0f) }   // unset points are invisible
        // Only set ear L and shoulder L (2 points, need at least 4)
        landmarks[7] = Landmark(0f, 1f, 0f, 0.9f)  // ear L
        landmarks[11] = Landmark(0f, 0.9f, 0f, 0.9f)  // shoulder L
        val sample = PostureScorer.score(landmarks, null)
        assertNull(sample, "Insufficient landmarks should return null")
    }

    @Test
    fun `description does not use blocklisted words`() {
        val landmarks = SyntheticBody.world(kneeDeg = 170f, visibility = 1f)
        val sample = PostureScorer.score(landmarks, null)
        assertNotNull(sample)
        val blocklisted = listOf("injury", "risk", "diagnos", "cleared", "abnormal")
        for (word in blocklisted) {
            assertTrue(!sample.description.lowercase().contains(word),
                "Description should not contain '$word', got: ${sample.description}")
        }
    }

    private val NECK = 0.17

    private fun createLandmarksWithNeckFlexion(flexDeg: Float): List<Landmark> {
        val landmarks = MutableList(33) { Landmark(0f, 1.05f, 0f, 1f) }

        // Hip points (base)
        landmarks[23] = Landmark(-0.1f, 0.5f, 0f, 1f)  // left hip
        landmarks[24] = Landmark(0.1f, 0.5f, 0f, 1f)   // right hip

        // Shoulder points
        landmarks[11] = Landmark(-0.1f, 0.95f, 0f, 1f)  // left shoulder
        landmarks[12] = Landmark(0.1f, 0.95f, 0f, 1f)   // right shoulder

        // Ears sit one neck-length above the shoulders and swing FORWARD (toward the camera, -z) by the flexion angle.
        val flexRad = kotlin.math.PI * flexDeg / 180f
        val earY = (0.95 + NECK * kotlin.math.cos(flexRad)).toFloat()
        val earZ = (-NECK * kotlin.math.sin(flexRad)).toFloat()
        landmarks[7] = Landmark(-0.1f, earY, earZ, 1f)   // left ear
        landmarks[8] = Landmark(0.1f, earY, earZ, 1f)    // right ear

        return landmarks
    }
}
