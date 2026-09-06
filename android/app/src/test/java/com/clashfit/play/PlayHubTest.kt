package com.clashfit.play

import com.clashfit.core.model.GameMode
import com.clashfit.core.model.LinkState
import com.clashfit.core.util.Clock
import com.clashfit.core.util.FakeClock
import com.clashfit.duel.LoopbackTransport
import com.clashfit.duel.RaidSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * PlayHub over a loopback bus: the lobby's host -> arm -> start path, plus leaderboard
 * tie-breaking and double-close safety.
 *
 * PlayHub takes its transport as a factory rather than building a `NearbyTransport` from a
 * `Context`, so the whole link lifecycle runs on the JVM with no Android or Play Services on the
 * classpath. `LoopbackTransport` is the same transport the duel/raid/race sessions are tested on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayHubTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun hub(bus: MutableSet<LoopbackTransport>, clock: Clock, scope: CoroutineScope) =
        PlayHub(clock, scope) { LoopbackTransport(bus) }

    // ------------------------------------------------------------------ lobby

    /**
     * The bug this pins: arming only on LINKED meant a host advertising on its own was never
     * armed, `link` stayed null, and the lobby's Start button — gated on it — never appeared.
     * One phone could not start a rep race at all.
     */
    @Test
    fun `a rep race host is armed while it is still advertising`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val hub = hub(bus, FakeClock(), backgroundScope)

        assertTrue(hub.host("ClashFit · Rep Race").isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LinkState.ADVERTISING, hub.linkState.value, "Hosting should advertise")

        // What the lobby does as soon as the link is open, peer or no peer.
        hub.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        val hud = hub.link.value
        assertNotNull(hud, "An advertising host must be armed, or the lobby has no Start button")
        assertTrue(hud.isHost, "The phone that pressed Host is the host")
        assertEquals(0, hud.peers, "Nobody has joined yet")

        hub.release()
    }

    @Test
    fun `a rep race host can start with nobody joined`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val hub = hub(bus, FakeClock(), backgroundScope)

        hub.host("ClashFit · Rep Race")
        hub.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        val signals = mutableListOf<StartSignal>()
        val watch = backgroundScope.launch { hub.started.collect { signals += it } }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(hub.start().isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(StartSignal(GameMode.REP_RACE, "bicep_curl", 30)),
            signals,
            "A solo host pressing Start should still go",
        )

        watch.cancel()
        hub.release()
    }

    @Test
    fun `a guest is not offered the host controls`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val hub = hub(bus, FakeClock(), backgroundScope)

        assertTrue(hub.join().isSuccess)
        hub.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        val hud = hub.link.value
        assertNotNull(hud)
        assertTrue(!hud.isHost, "A phone that pressed Join is never the host")

        hub.release()
    }

    /**
     * A re-arm used to run `session.close()`, and a session's close() also closes the transport —
     * so changing the clock, or the link flapping LINKED -> LOST -> LINKED, dropped the very
     * connection the lobby was sitting on.
     */
    @Test
    fun `re-arming keeps the link open`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val hub = hub(bus, FakeClock(), backgroundScope)

        hub.host("ClashFit · Rep Race")
        hub.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()
        val transport = bus.single()

        hub.arm(GameMode.REP_RACE, "bicep_curl", 60)  // the host moved the clock to 60s
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(transport in bus, "Re-arming must not close the transport the lobby is linked on")
        assertNotNull(hub.link.value, "Still armed after the clock changed")

        hub.release()
        assertTrue(transport !in bus, "release() does close it")
    }

    /**
     * The whole point of the lobby: the guest chooses nothing and is told what it is about to do
     * BEFORE anyone presses start. It arms blank, the way the lobby arms it, and the host's SETUP
     * fills it in.
     */
    @Test
    fun `the guest learns the host's movement before anyone starts`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()
        val host = hub(bus, clock, backgroundScope)
        val guest = hub(bus, clock, backgroundScope)

        host.host("ClashFit · Rep Race")
        guest.join()
        guest.arm(GameMode.REP_RACE, "", null)  // the guest has nothing to say
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", guest.link.value?.exerciseId, "Nothing heard from the host yet")

        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("bicep_curl", guest.link.value?.exerciseId, "The host's pick reaches the guest's lobby")
        assertEquals(30, guest.link.value?.durationSec, "And so does the clock")

        // The host changes its mind; the guest follows.
        host.arm(GameMode.REP_RACE, "squat", 90)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("squat", guest.link.value?.exerciseId)
        assertEquals(90, guest.link.value?.durationSec)

        host.release()
        guest.release()
    }

    /**
     * The host's START carries the clock and the exercise too, so a guest that somehow missed
     * every SETUP still lands on the right movement rather than on whatever it had.
     */
    @Test
    fun `the host's clock and exercise reach the guest`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()
        val host = hub(bus, clock, backgroundScope)
        val guest = hub(bus, clock, backgroundScope)

        host.host("ClashFit · Rep Race")
        guest.join()
        testDispatcher.scheduler.advanceUntilIdle()

        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        val got = mutableListOf<StartSignal>()
        val watch = backgroundScope.launch { guest.started.collect { got += it } }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(host.start().isSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(StartSignal(GameMode.REP_RACE, "bicep_curl", 30)),
            got,
            "The guest races the host's exercise on the host's clock",
        )

        watch.cancel()
        host.release()
        guest.release()
    }

    /**
     * A guest that links after the host has already picked. The transport backlog replays the
     * last SETUP, but the host also says it again on every link, so the catch-up does not depend
     * on the backlog having survived the wait.
     */
    @Test
    fun `a guest that links late is told the movement`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()
        val host = hub(bus, clock, backgroundScope)

        host.host("ClashFit · Rep Race")
        host.arm(GameMode.REP_RACE, "shoulder_press", 90)
        testDispatcher.scheduler.advanceUntilIdle()
        val hostTransport = bus.single()

        // Only now does the second phone show up.
        val guest = hub(bus, clock, backgroundScope)
        guest.join()
        guest.arm(GameMode.REP_RACE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("", guest.link.value?.exerciseId, "It arrived after the host had already chosen")

        hostTransport.simulateLinked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("shoulder_press", guest.link.value?.exerciseId, "Linking is what tells it")
        assertEquals(90, guest.link.value?.durationSec)

        host.release()
        guest.release()
    }

    /** A guest has no say. If it announced anything the host would follow its own guest. */
    @Test
    fun `a guest does not announce a movement of its own`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()
        val host = hub(bus, clock, backgroundScope)
        val guest = hub(bus, clock, backgroundScope)

        host.host("ClashFit · Rep Race")
        guest.join()
        host.arm(GameMode.REP_RACE, "bicep_curl", 30)
        testDispatcher.scheduler.advanceUntilIdle()

        guest.arm(GameMode.REP_RACE, "squat", 90)  // as if the guest had a picker
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("bicep_curl", host.link.value?.exerciseId, "The host is unmoved")
        assertEquals(30, host.link.value?.durationSec)

        host.release()
        guest.release()
    }

    // ---------------------------------------------------------------- sessions

    @Test
    fun `double-close does not throw exception`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
        )

        // First close, same as PlayHub.release() would perform on its session + transport.
        r1.close()
        testDispatcher.scheduler.advanceUntilIdle()

        // Second close should not throw.
        r1.close()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(t1 !in bus, "Transport should be removed from the bus after close")
    }

    @Test
    fun `detach leaves the transport on the bus`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Player 1",
            clock = FakeClock(),
            scope = backgroundScope,
            isHost = true,
        )

        r1.detach()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(t1 in bus, "detach() drops the session, never the link")
        t1.close()
    }

    @Test
    fun `raid standings ordered by damage desc, then reps desc, then name asc`() = testScope.runTest {
        val bus = mutableSetOf<LoopbackTransport>()
        val clock = FakeClock()

        // Create raid sessions directly with loopback transports, the same mechanism
        // PlayHub.arm(GameMode.RAID, ...) wires up internally.
        val t1 = LoopbackTransport(bus)
        val r1 = RaidSession(
            transport = t1,
            playerId = "P1",
            playerName = "Charlie",
            clock = clock,
            scope = backgroundScope,
            isHost = true,
        )

        val t2 = LoopbackTransport(bus)
        val r2 = RaidSession(
            transport = t2,
            playerId = "P2",
            playerName = "Alice",
            clock = clock,
            scope = backgroundScope,
            isHost = false,
        )

        // Send reps: both have 100 damage but different reps
        r1.sendRep(damage = 100, reps = 8, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.9f)
        r2.sendRep(damage = 100, reps = 5, exerciseId = "squat", fatigueBand = "WORKING", formScore = 0.8f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify standings are ordered by damage desc, then reps desc, then name asc
        val standings1 = r1.getStandings()
        assertEquals(2, standings1.size, "Should have 2 players")
        assertEquals("P1", standings1[0].playerId, "P1 (8 reps) should be first")
        assertEquals(8, standings1[0].reps)
        assertEquals("P2", standings1[1].playerId, "P2 (5 reps) should be second")
        assertEquals(5, standings1[1].reps)

        r1.close(); r2.close()
    }
}
