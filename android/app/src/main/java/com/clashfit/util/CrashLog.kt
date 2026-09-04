package com.clashfit.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few crashes, written to a file the phone keeps.
 *
 * A crash at a venue is a story told from memory: "it closed when I pressed something". Logcat has
 * the answer but needs a laptop, a cable and the presence of mind to capture it before the buffer
 * rolls. This writes the stack trace to the app's own storage, where it survives the restart and
 * can be read on the phone from the System check screen.
 *
 * It never replaces the platform's handler — the default is called afterwards, so the process still
 * dies and Android still reports it. All this does is leave a note.
 */
object CrashLog {

    private const val TAG = "ClashFit/crash"
    private const val FILE = "crash.log"

    /** Serialises the read-modify-write, since crashes do not queue politely. */
    private val lock = Any()

    /** Keep the file small enough to read on a phone and to never matter for storage. */
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
                .onFailure { Log.w(TAG, "could not write the crash log", it) }
            // Always hand back to whoever was there first. Swallowing this would turn a crash into
            // a freeze, which is worse to diagnose and worse to watch.
            previous?.uncaughtException(thread, error)
        }
    }

    /** The whole log, newest last, or null when nothing has ever crashed. */
    fun read(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        return if (f.exists() && f.length() > 0) f.readText() else null
    }

    /** The first line of the most recent entry, for a one-line report on screen. */
    fun latest(context: Context): String? =
        read(context)?.trim()?.split("\n\n")?.lastOrNull()?.lines()?.take(3)?.joinToString(" · ")

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(Date())
        val entry = buildString {
            append("\n\n")
            append("$when_  on ${thread.name}\n")
            append("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n")
            append(stack.lineSequence().take(40).joinToString("\n"))
        }

        // Locked, because two threads can crash at the same instant and each handler runs on its
        // own thread. Unsynchronised, both would read the same file, both would append, and the
        // second write would erase the first — losing exactly one of the two crashes somebody
        // needs to explain.
        synchronized(lock) {
            val f = File(context.filesDir, FILE)
            val existing = if (f.exists()) f.readText() else ""
            // Oldest first out, so the newest crash is always the one that survives.
            val combined = (existing + entry).let {
                if (it.length > MAX_BYTES) it.takeLast(MAX_BYTES) else it
            }
            f.writeText(combined)
        }
    }
}
