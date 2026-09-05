package com.clashfit

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * No suspending function may swallow its own cancellation.
 *
 * `CancellationException` is an `Exception`, so `catch (e: Exception)` around a suspending call
 * catches it. The coroutine then carries on: it logs a failure that did not happen, takes a
 * fallback path nobody asked for, and — where the catch retries — restarts work whose caller is
 * already gone. Structured concurrency stops being a guarantee.
 *
 * That was not hypothetical here. The text-to-speech queue caught everything and, on catching,
 * called itself again if the queue was not empty; cancelling the coach on the way out of a fight
 * therefore made the phone finish reading the queue to an empty room.
 *
 * A rule of this shape is easier to hold with a test than with attention. This walks the app's
 * own sources, finds every catch-all inside a `suspend fun`, and requires a cancellation arm
 * immediately above it. It reads source rather than bytecode, so it costs nothing to run and
 * fails on the line that needs changing.
 */
class CancellationGuardTest {

    private val catchAll = Regex("""catch \((\w+): (Exception|Throwable)\)""")
    private val cancelArm = Regex("""catch \(\w+: CancellationException\)""")
    private val funStart = Regex("""^\s*(private |internal |public |protected |override |inline |suspend )*fun\s""")
    private val suspendFun = Regex("""^\s*(private |internal |public |protected |override |inline )*suspend fun\s""")

    /**
     * The waiver, written where the code is rather than as a list of line numbers here.
     *
     * A catch-all may stand alone when its `try` contains no suspension point at all — parsing a
     * line of a file, say — because no cancellation can arrive and a guard would be unreachable
     * noise. Saying so on the line itself means the waiver moves with the code it waives, and
     * disappears the moment somebody puts an `await` inside that try.
     */
    private val waiver = "no suspension"

    @Test
    fun `every catch-all inside a suspend function lets cancellation through`() {
        val root = sourceRoot()
        val offenders = mutableListOf<String>()
        var checked = 0

        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val rel = file.relativeTo(root).path
            val lines = file.readLines()
            var inSuspend = false
            lines.forEachIndexed { i, line ->
                if (funStart.containsMatchIn(line)) inSuspend = suspendFun.containsMatchIn(line)
                if (!inSuspend || !catchAll.containsMatchIn(line)) return@forEachIndexed
                checked++
                val here = "$rel:${i + 1}"
                if (line.contains(waiver)) return@forEachIndexed
                // Look back over this catch chain, from the catch-all to its own `try {`.
                //
                // Kotlin matches catch clauses in order, so a cancellation arm anywhere earlier in
                // the same chain wins — it does not have to be the line directly above. A fixed
                // window got that wrong the first time a catch-all sat below both a cancellation
                // arm and a multi-line OutOfMemoryError arm, and reported a function that handles
                // cancellation correctly.
                val chainStart = (i - 1 downTo (i - 60).coerceAtLeast(0))
                    .firstOrNull { lines[it].contains("try {") || lines[it].trimEnd().endsWith("= try {") }
                    ?: (i - 8).coerceAtLeast(0)
                val above = lines.subList(chainStart, i).joinToString("\n")
                val guarded = cancelArm.containsMatchIn(above) &&
                    above.substringAfterLast("CancellationException").contains("throw")
                if (!guarded) offenders += here
            }
        }

        assertTrue(
            "found no catch-alls to check at all — the walk is looking in the wrong place",
            checked > 0,
        )
        if (offenders.isNotEmpty()) {
            fail(
                "these catch-alls sit inside a suspend function and would swallow cancellation.\n" +
                    "Add `} catch (cancelled: CancellationException) { throw cancelled }` above " +
                    "each one, or mark the line `// $waiver` if that try genuinely cannot " +
                    "suspend:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }

    /** The app's own sources, found from the module directory the test runs in. */
    private fun sourceRoot(): File {
        val here = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Gradle runs unit tests with the module directory as the working directory, but that has
        // moved between AGP versions. Walk up until the source tree is under foot.
        var dir: File? = here
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/com/clashfit")
            if (candidate.isDirectory) return candidate
            val direct = File(dir, "src/main/java/com/clashfit")
            if (direct.isDirectory) return direct
            dir = dir.parentFile
        }
        throw AssertionError("could not find the app sources from $here")
    }
}
