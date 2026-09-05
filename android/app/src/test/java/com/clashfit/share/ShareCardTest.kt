package com.clashfit.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.clashfit.data.RunPointEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shareable card, rendered.
 *
 * This is the one artefact of the whole app that leaves the phone and is seen by people who will
 * never open it, and it is drawn straight onto a Canvas with no Compose preview and no screenshot
 * baseline to catch a mistake. A card that throws, or comes out blank, or comes out the wrong
 * shape, would be discovered by somebody pasting it into a group chat.
 *
 * So every style is rendered here, at the real size, including the two shapes that break naive
 * drawing code: an activity with no route at all, and one that never left the same spot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareCardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun route(n: Int, spreadDeg: Double = 0.0005): List<RunPointEntity> =
        (0 until n).map { i ->
            RunPointEntity(
                id = i.toLong(), runId = 1L, tMs = i * 1000L,
                lat = 18.5204 + i * spreadDeg / n,
                lon = 73.8567 + (if (i % 2 == 0) spreadDeg else 0.0) / n,
                altM = 560.0 + i, accuracyM = 5f, speedMps = 2f + (i % 5) * 0.4f,
            )
        }

    private fun stats(points: List<RunPointEntity>) = ShareCard.Stats(
        title = "Run",
        dateLabel = "5 Sep, 17:04",
        distanceM = 5240f,
        movingMs = 1_691_000L,
        paceSecPerKm = 322f,
        climbM = 84f,
        cadenceSpm = 168,
        points = points,
        levelLabel = "Level 7 · Fighter",
        records = listOf("Fastest kilometre", "Longest activity"),
        fastestKmSec = 298f,
        effort = 0.42f,
        effortLabel = "PUSHED",
    )

    @Test
    fun `every style renders at story size`() {
        ShareCard.Style.entries.forEach { style ->
            val bmp = ShareCard.render(context, stats(route(200)), style)
            assertEquals(ShareCard.WIDTH, bmp.width, "${style.name} was the wrong width")
            assertEquals(ShareCard.HEIGHT, bmp.height, "${style.name} was the wrong height")
        }
    }

    @Test
    fun `the aspect ratio is nine by sixteen, which is what a story is`() {
        assertEquals(9f / 16f, ShareCard.WIDTH.toFloat() / ShareCard.HEIGHT, 0.001f)
    }

    @Test
    fun `an activity with no route still produces a card`() {
        // A card that threw here would take the share button down with it, on the one activity
        // most likely to be shared by somebody testing the feature indoors.
        ShareCard.Style.entries.forEach { style ->
            val bmp = ShareCard.render(context, stats(emptyList()), style)
            assertEquals(ShareCard.WIDTH, bmp.width)
        }
    }

    @Test
    fun `an activity that never moved does not divide by its own zero span`() {
        val stuck = (0 until 30).map { i ->
            RunPointEntity(
                id = i.toLong(), runId = 1L, tMs = i * 1000L,
                lat = 18.5204, lon = 73.8567, altM = 560.0, accuracyM = 5f, speedMps = 0f,
            )
        }
        ShareCard.Style.entries.forEach { style ->
            val bmp = ShareCard.render(context, stats(stuck), style)
            assertEquals(ShareCard.HEIGHT, bmp.height)
        }
    }

    @Test
    fun `a card without records or a level still renders`() {
        val bare = stats(route(60)).copy(records = emptyList(), levelLabel = null, fastestKmSec = null)
        ShareCard.Style.entries.forEach { ShareCard.render(context, bare, it) }
    }

    @Test
    fun `the card is not blank`() {
        // Every pixel identical would mean the whole draw silently no-opped.
        val bmp = ShareCard.render(context, stats(route(200)), ShareCard.Style.HERO)
        val corner = bmp.getPixel(4, 4)
        val distinct = mutableSetOf<Int>()
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                distinct += bmp.getPixel(x, y)
                if (distinct.size > 8) break
                x += 37
            }
            if (distinct.size > 8) break
            y += 53
        }
        assertTrue(distinct.size > 1, "the card rendered as one flat colour ($corner)")
    }

    @Test
    fun `effort fraction is bounded and safe on an empty route`() {
        assertEquals(0f, ShareCard.effortFraction(emptyList()))
        val f = ShareCard.effortFraction(route(120))
        assertTrue(f in 0f..1f, "effort fraction escaped 0..1: $f")
    }

    @Test
    fun `styles all carry a label and a blurb for the picker`() {
        ShareCard.Style.entries.forEach {
            assertTrue(it.label.isNotBlank(), "${it.name} has no label")
            assertTrue(it.blurb.isNotBlank(), "${it.name} has no blurb")
        }
    }
}
