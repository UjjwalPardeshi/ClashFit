package com.clashfit.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crash log exists for the case where nobody has a laptop and the app has just closed.
 *
 * That makes its failure mode particular: it must never itself be the thing that goes wrong, it
 * must survive a restart, and it must not grow without bound on a phone that is having a bad day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashLogTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() = CrashLog.clear(context)

    @Test
    fun `nothing is reported before anything has crashed`() {
        assertNull(CrashLog.read(context))
        assertNull(CrashLog.latest(context))
    }

    @Test
    fun `an uncaught exception is written down`() {
        CrashLog.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("the boss fell over"))

        val log = CrashLog.read(context)
        assertNotNull(log, "a crash should leave a note")
        assertTrue("IllegalStateException" in log, "the exception type should be in the log")
        assertTrue("the boss fell over" in log, "the message should be in the log")
    }

    @Test
    fun `the newest crash is the one that survives`() {
        CrashLog.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("first"))
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("second"))

        val latest = CrashLog.latest(context)
        assertNotNull(latest)
        assertTrue("second" in CrashLog.read(context)!!, "the newest crash must be kept")
    }

    @Test
    fun `the file cannot grow without bound`() {
        CrashLog.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        repeat(120) {
            handler.uncaughtException(Thread.currentThread(), IllegalStateException("crash $it"))
        }
        val size = CrashLog.read(context)!!.length
        assertTrue(size <= 64 * 1024, "the log grew to $size bytes")
        assertTrue("crash 119" in CrashLog.read(context)!!, "the most recent crash must still be there")
    }

    @Test
    fun `clearing removes it`() {
        CrashLog.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), IllegalStateException("gone soon"))
        assertNotNull(CrashLog.read(context))
        CrashLog.clear(context)
        assertNull(CrashLog.read(context))
    }

    @Test
    fun `the platform handler still runs, so the process still dies`() {
        var handedOn = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> handedOn++ }
        CrashLog.install(context)
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertEquals(1, handedOn, "swallowing the exception would turn a crash into a freeze")
    }
}
