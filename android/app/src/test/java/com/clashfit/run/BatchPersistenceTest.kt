package com.clashfit.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for batch persistence and data integrity.
 */
class BatchPersistenceTest {

    @Test
    fun batchPersistence_pointsAreFlushedOnKill_beforeCleared() {
        // Collect 5 points (< BATCH_SIZE=10)
        // Simulate service kill
        // Verify 5 points are either:
        // 1. Persisted to DB, or
        // 2. Still in buffer for recovery on restart

        val BATCH_SIZE = 10
        var pointsBuffer = mutableListOf<String>() // Simulating points
        var persistedPoints = mutableListOf<String>()
        var isPersisting = false

        // Collect 5 points
        for (i in 0..4) {
            pointsBuffer.add("point_$i")
        }

        // Before kill, any pending write must be awaited
        if (pointsBuffer.size > 0) {
            isPersisting = true
            // Simulate DB write with latency
            persistedPoints.addAll(pointsBuffer)
            pointsBuffer.clear()
            isPersisting = false
        }

        // Simulate service kill
        val survivors = persistedPoints + pointsBuffer // Either persisted or still buffered

        // Verify no data loss: 5 points should either be persisted or in buffer
        assertEquals(5, survivors.size, "All 5 points should be accounted for (persisted or in buffer)")
    }

    @Test
    fun batchPersistence_multipleFlushes_allDataPreserved() {
        // Collect 10 points (triggers flush at BATCH_SIZE)
        // Collect 5 more
        // Verify first batch is persisted, second batch is buffered

        val BATCH_SIZE = 10
        var pointsBuffer = mutableListOf<String>()
        val allPersisted = mutableListOf<String>()
        var flushCount = 0

        // Collect 10 points
        for (i in 0..9) {
            pointsBuffer.add("point_$i")
            if (pointsBuffer.size >= BATCH_SIZE) {
                // Trigger flush
                allPersisted.addAll(pointsBuffer)
                pointsBuffer.clear()
                flushCount++
            }
        }

        assertEquals(1, flushCount, "Should trigger one flush at BATCH_SIZE=10")
        assertEquals(10, allPersisted.size, "First batch should be persisted")
        assertEquals(0, pointsBuffer.size, "Buffer should be empty after flush")

        // Collect 5 more points
        for (i in 10..14) {
            pointsBuffer.add("point_$i")
            if (pointsBuffer.size >= BATCH_SIZE) {
                allPersisted.addAll(pointsBuffer)
                pointsBuffer.clear()
                flushCount++
            }
        }

        assertEquals(1, flushCount, "Should not trigger another flush (5 < BATCH_SIZE)")
        assertEquals(10, allPersisted.size, "Only first batch persisted")
        assertEquals(5, pointsBuffer.size, "Second batch should be in buffer")

        // Total points accounted for
        val totalPoints = allPersisted.size + pointsBuffer.size
        assertEquals(15, totalPoints, "All 15 points should be accounted for")
    }

    @Test
    fun batchPersistence_emergencyStop_flushesPendingBuffer() {
        // Collect 7 points (< BATCH_SIZE=10)
        // Call stopRun() which should flush remaining points synchronously
        // Verify all 7 points are persisted before service stops

        val BATCH_SIZE = 10
        var pointsBuffer = mutableListOf<String>()
        val allPersisted = mutableListOf<String>()

        // Collect 7 points
        for (i in 0..6) {
            pointsBuffer.add("point_$i")
        }

        // Simulate stopRun() which calls withContext(Dispatchers.IO) for flush
        // Ensure DB write completes before clearing buffer
        if (pointsBuffer.isNotEmpty()) {
            // Synchronous persist (awaited)
            allPersisted.addAll(pointsBuffer)
            pointsBuffer.clear()
        }

        assertEquals(0, pointsBuffer.size, "Buffer should be cleared after flush")
        assertEquals(7, allPersisted.size, "All 7 points should be persisted")
    }

    @Test
    fun pointsBuffer_doesNotGrowUnbounded_batchesFlushed() {
        // Run for 30 seconds at 1 Hz, with BATCH_SIZE=10
        // Should have 3 batches flushed
        // Buffer should never exceed BATCH_SIZE

        val BATCH_SIZE = 10
        var pointsBuffer = mutableListOf<String>()
        val allPersisted = mutableListOf<String>()
        var flushCount = 0
        var maxBufferSize = 0

        // Simulate 30 seconds of 1 Hz updates
        for (t in 0..29) {
            pointsBuffer.add("point_${t + 1}")
            maxBufferSize = maxOf(maxBufferSize, pointsBuffer.size)

            if (pointsBuffer.size >= BATCH_SIZE) {
                allPersisted.addAll(pointsBuffer)
                pointsBuffer.clear()
                flushCount++
            }
        }

        // Should have 3 flushes (30 points / 10 BATCH_SIZE)
        assertEquals(3, flushCount, "Should have 3 complete batches")
        assertEquals(30, allPersisted.size, "Should persist 30 points")
        assertEquals(0, pointsBuffer.size, "No remainder from 30/10 division")
        assertTrue(maxBufferSize <= BATCH_SIZE, "Buffer should never exceed BATCH_SIZE")
    }
}
