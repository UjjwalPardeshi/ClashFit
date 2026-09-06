package com.clashfit

import android.app.Application
import com.clashfit.map.MapTiles
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.clashfit.cloud.CloudConfig
import com.clashfit.util.CrashLog
import com.clashfit.BuildConfig
import android.os.StrictMode

class ClashFitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash during the rest of startup is still recorded.
        CrashLog.install(this)
        installStrictMode()
        CloudConfig.initialize(this)
        graph = AppGraph(this)
        // Off the main thread and before any map exists: the tile config makes two directories and
        // reads a preferences file, and a map that pays for that while it is composing drops the
        // frame it was trying to draw.
        MapTiles.warmUp(this, graph.scope)
        graph.config.reload()
        // Config is re-read on every foreground: edit a JSON on the phone, background, resume.
        // Scores go up whenever they change, and only ever when somebody is signed in.
        graph.scoreSync.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { graph.config.reload() }
        })
    }

    /**
     * Complain, in debug builds, about work that does not belong on the main thread.
     *
     * Disk and network on the UI thread are invisible on a fast phone in a quiet room and very
     * visible on a warm phone running pose inference at thirty frames a second — which is the only
     * condition this app is ever used in. StrictMode turns "it felt a bit janky" into a line in
     * logcat naming the exact call.
     *
     * It logs rather than crashes: a violation is worth knowing about, not worth ending a demo
     * over, and release builds never install it at all.
     */
    private fun installStrictMode() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    /**
     * Give the coach model back when the system says it needs the memory.
     *
     * The Gemma weights are the largest thing this app holds, they are native so the garbage
     * collector cannot reclaim them, and the coach is a between-sets nicety. The camera pipeline
     * mid-set is not. Handing the model back keeps the part that matters alive, and the template
     * bank speaks in the meantime.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            graph.llmEngine.shutDown()
        }
    }
}
