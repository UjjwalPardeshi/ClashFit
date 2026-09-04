package com.clashfit.coach

import com.clashfit.core.model.FatigueBand
import com.clashfit.core.model.SetTelemetry
import com.clashfit.engine.coach.CoachFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A cancelled coach request must stay cancelled.
 *
 * `CancellationException` is an `Exception`, so a broad `catch (e: Exception)` swallows it. Every
 * layer of the coach chain had one, and each turned "the user left the session screen" into "the
 * model failed, use a template". The line was then built for a screen nobody was looking at, and —
 * worse — the coroutine kept running after its scope was cancelled, which is precisely the
 * guarantee structured concurrency exists to provide.
 *
 * The session view model launches `speakFor` in `viewModelScope` at the end of every set, and it
 * can be in flight for up to ten seconds. Backing out of a fight in that window is not an unusual
 * thing for someone to do; it is the normal way to abandon a set.
 */
class CoachCancellationTest {

    private val telemetry = SetTelemetry(
        exercise = "squat",
        reps = 8,
        formMean = 0.82f,
        formFirst3 = 0.88f,
        formLast3 = 0.74f,
        formMeanPct = 82,
        formFirst3Pct = 88,
        formLast3Pct = 74,
        depthCm = null,
        depthDropCm = null,
        velocityLossPct = 12,
        romLossPct = 4,
        fatigueBand = FatigueBand.FADING,
        bestRep = SetTelemetry.RepRef(1, 0.93f),
        worstRep = SetTelemetry.RepRef(7, 0.68f, "depth"),
        comboMax = 1.4f,
        comboReps = 3,
        bossHpPct = 41,
        sessionSetIndex = 2,
        restSec = 45,
        trend = SetTelemetry.Trend.DECLINING,
    )

    @Test
    fun `cancelling a coach request propagates instead of returning a template`() = runTest {
        val started = CompletableDeferred<Unit>()
        // A model that never answers, which is what a slow one looks like from here.
        val coach = CoachFor(llm = { started.complete(Unit); awaitCancellation() }, timeoutMs = 60_000)

        val job = async { coach.speakFor(telemetry) }
        started.await()
        job.cancel()

        var threw = false
        try {
            job.await()
        } catch (e: CancellationException) {
            threw = true
        }
        assertTrue(threw, "a cancelled request must not come back as a cheerful template line")
    }

    // CoachEngine wraps CoachFor and had the same swallowing catch, fixed the same way. It is not
    // covered here because it takes a concrete LlmEngine, which needs a Context and a model file,
    // rather than a function this test could stand in for. CoachFor is the layer the session
    // actually cancels through, and it is pinned above.

    @Test
    fun `a model that genuinely fails still falls back to a template`() = runTest {
        // The behaviour the broad catch was there for, which must survive the fix.
        val coach = CoachFor(llm = { error("the model fell over") }, timeoutMs = 60_000)
        val out = coach.speakFor(telemetry)

        assertTrue(out.coachLine.isNotEmpty(), "a failed model must still produce a coach line")
        assertTrue(out.bossLine.isNotEmpty(), "a failed model must still produce a boss line")
    }

    @Test
    fun `a model that never answers falls back once its timeout expires`() = runTest {
        var cancelledInside = false
        val coach = CoachFor(
            llm = {
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    cancelledInside = true
                    throw e
                }
            },
            timeoutMs = 5_000,
        )

        // runTest's virtual clock skips the five seconds rather than waiting them out.
        val out = coach.speakFor(telemetry)
        yield()

        assertTrue(cancelledInside, "the timeout should have cancelled the model call")
        assertTrue(out.coachLine.isNotEmpty(), "a timed-out model must still produce a line")
        assertFalse(out.coachLine.isBlank())
    }
}
