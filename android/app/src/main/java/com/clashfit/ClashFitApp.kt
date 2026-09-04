package com.clashfit

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.clashfit.cloud.CloudConfig
import com.clashfit.util.CrashLog

class ClashFitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash during the rest of startup is still recorded.
        CrashLog.install(this)
        CloudConfig.initialize(this)
        graph = AppGraph(this)
        graph.config.reload()
        // Config is re-read on every foreground: edit a JSON on the phone, background, resume.
        // Scores go up whenever they change, and only ever when somebody is signed in.
        graph.scoreSync.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { graph.config.reload() }
        })
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
