package com.clashfit

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.clashfit.cloud.CloudConfig

class ClashFitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
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
}
