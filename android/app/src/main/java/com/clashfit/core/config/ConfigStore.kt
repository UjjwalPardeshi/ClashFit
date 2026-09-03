package com.clashfit.core.config

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads every tunable from `assets/config`, letting a copy under `filesDir/config` override
 * any file so balance can be edited on the phone and picked up on resume. Malformed JSON keeps
 * the last good value and reports the error; it never takes the app down.
 */
class ConfigStore(private val context: Context, private val json: Json, private val scope: CoroutineScope) {

    private val _pose = MutableStateFlow(PoseConfig())
    private val _combat = MutableStateFlow(CombatConfig())
    private val _exercises = MutableStateFlow<Map<String, ExerciseSpec>>(emptyMap())
    private val _ghosts = MutableStateFlow<Map<String, GhostData>>(emptyMap())
    private val _clinic = MutableStateFlow<Map<String, ClinicProtocol>>(emptyMap())
    private val _version = MutableStateFlow(0)
    private val _lastError = MutableStateFlow<String?>(null)

    val pose: StateFlow<PoseConfig> = _pose.asStateFlow()
    val combat: StateFlow<CombatConfig> = _combat.asStateFlow()
    val exercises: StateFlow<Map<String, ExerciseSpec>> = _exercises.asStateFlow()
    val ghosts: StateFlow<Map<String, GhostData>> = _ghosts.asStateFlow()
    val clinic: StateFlow<Map<String, ClinicProtocol>> = _clinic.asStateFlow()
    /** Bumps on every reload so screens can react. */
    val version: StateFlow<Int> = _version.asStateFlow()
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val overrideDir: File get() = File(context.filesDir, "config")

    fun exercise(id: String): ExerciseSpec? = _exercises.value[id]

    /** Called at splash and on every foreground. Runs on IO; safe to call often. */
    fun reload() {
        scope.launch(Dispatchers.IO) { reloadBlocking() }
    }

    fun reloadBlocking() {
        val errors = mutableListOf<String>()
        fun <T> load(path: String, parse: (String) -> T): T? = runCatching { parse(read(path)) }
            .onFailure { errors += "$path: ${it.message}" }.getOrNull()

        load("config/pose.json") { json.decodeFromString(PoseConfig.serializer(), it) }?.let { _pose.value = it }
        load("config/combat.json") { json.decodeFromString(CombatConfig.serializer(), it) }?.let { _combat.value = it }

        val ex = LinkedHashMap<String, ExerciseSpec>()
        for (name in list("config/exercises")) {
            if (!name.endsWith(".json") || name == "index.json") continue
            load("config/exercises/$name") { json.decodeFromString(ExerciseSpec.serializer(), it) }?.let { ex[it.id] = it }
        }
        if (ex.isNotEmpty()) _exercises.value = ex

        val gh = LinkedHashMap<String, GhostData>()
        for (name in list("config/ghosts")) {
            if (!name.endsWith(".json")) continue
            load("config/ghosts/$name") { json.decodeFromString(GhostData.serializer(), it) }?.let { gh[name.removeSuffix(".json")] = it }
        }
        if (gh.isNotEmpty()) _ghosts.value = gh

        val cl = LinkedHashMap<String, ClinicProtocol>()
        for (name in list("config/clinic")) {
            if (!name.endsWith(".json")) continue
            load("config/clinic/$name") { json.decodeFromString(ClinicProtocol.serializer(), it) }?.let { cl[it.id] = it }
        }
        if (cl.isNotEmpty()) _clinic.value = cl

        _lastError.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
        _version.value = _version.value + 1
    }

    /** Files on the phone win over files in the APK: that is the whole hot-reload story. */
    private fun read(path: String): String {
        val override = File(context.filesDir, path)
        if (override.isFile) return override.readText()
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun list(dir: String): List<String> {
        val fromAssets = runCatching { context.assets.list(dir)?.toList() }.getOrNull().orEmpty()
        val fromFiles = File(context.filesDir, dir).list()?.toList().orEmpty()
        return (fromAssets + fromFiles).distinct().sorted()
    }
}
