package com.clashfit.config

import com.clashfit.core.model.Family
import com.clashfit.core.model.GameMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Every mode and family an exercise names must exist.
 *
 * `breathing.json` listed a game called `RECOVERY`, which is not a GameMode and never was. Nothing
 * caught it because the field is a list of plain strings: an unknown value parses fine, matches no
 * mode, and simply makes the exercise unavailable everywhere without ever saying so. A typo in a
 * config file is invisible until somebody asks why an exercise cannot be selected.
 */
class ExerciseGamesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun exerciseFiles(): List<File> {
        val dir = File("src/main/assets/config/exercises")
        assertTrue(dir.isDirectory, "config directory not found at ${dir.absolutePath}")
        return dir.listFiles()!!.filter { it.extension == "json" && it.name != "index.json" }.sorted()
    }

    @Test
    fun `every game an exercise names is a real GameMode`() {
        val modes = GameMode.entries.map { it.name }.toSet()
        val offenders = mutableListOf<String>()
        exerciseFiles().forEach { file ->
            val games = json.parseToJsonElement(file.readText()).jsonObject["games"]
                ?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            games.filterNot { it in modes }.forEach { offenders += "${file.name}: $it" }
        }
        assertTrue(offenders.isEmpty(), "exercises name modes that do not exist: $offenders")
    }

    @Test
    fun `every family an exercise names is a real Family`() {
        val families = Family.entries.map { it.name }.toSet()
        val offenders = mutableListOf<String>()
        exerciseFiles().forEach { file ->
            val family = json.parseToJsonElement(file.readText()).jsonObject["family"]?.jsonPrimitive?.content
            if (family != null && family !in families) offenders += "${file.name}: $family"
        }
        assertTrue(offenders.isEmpty(), "exercises name families that do not exist: $offenders")
    }

    @Test
    fun `the index agrees with the files beside it`() {
        val index = File("src/main/assets/config/exercises/index.json")
        val listed = json.parseToJsonElement(index.readText()).jsonObject["exercises"]!!
            .jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
        val onDisk = exerciseFiles().map {
            json.parseToJsonElement(it.readText()).jsonObject["id"]!!.jsonPrimitive.content
        }.toSet()
        assertTrue(
            listed == onDisk,
            "index lists ${(listed - onDisk).sorted()} that have no file, " +
                "and misses ${(onDisk - listed).sorted()}",
        )
    }
}
