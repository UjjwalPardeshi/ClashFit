package com.clashfit.meta

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

/**
 * Verifies that MIGRATION_3_4 creates the correct meta-progression tables.
 */
class MigrationMetaTest {

    @Test
    fun `schema 4 meta table exists with correct columns`() {
        val schema4 = loadSchemaJson(4)
        val metaTable = schema4["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "meta"
        }

        assertNotNull(metaTable, "Schema 4 should have meta entity")

        val fields = metaTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Meta table should have fields")

        val fieldMap = fields.map {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            path
        }.toSet()

        assertTrue(fieldMap.contains("id"), "Meta should have id field")
        assertTrue(fieldMap.contains("xp"), "Meta should have xp field")
        assertTrue(fieldMap.contains("level"), "Meta should have level field")
        assertTrue(fieldMap.contains("sessions"), "Meta should have sessions field")
        assertTrue(fieldMap.contains("wins"), "Meta should have wins field")
        assertTrue(fieldMap.contains("totalReps"), "Meta should have totalReps field")
        assertTrue(fieldMap.contains("cleanReps"), "Meta should have cleanReps field")
        assertTrue(fieldMap.contains("totalDamage"), "Meta should have totalDamage field")
        assertTrue(fieldMap.contains("familiesPlayedMask"), "Meta should have familiesPlayedMask field")
        assertTrue(fieldMap.contains("pbCount"), "Meta should have pbCount field")
        assertTrue(fieldMap.contains("weeklyChallengesDone"), "Meta should have weeklyChallengesDone field")
        assertTrue(fieldMap.contains("bestStreak"), "Meta should have bestStreak field")
    }

    @Test
    fun `schema 4 achievements table exists`() {
        val schema4 = loadSchemaJson(4)
        val achievementsTable = schema4["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "achievements"
        }

        assertNotNull(achievementsTable, "Schema 4 should have achievements entity")

        val fields = achievementsTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Achievements table should have fields")

        val fieldMap = fields.map {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            path
        }.toSet()

        assertTrue(fieldMap.contains("id"), "Achievements should have id field")
        assertTrue(fieldMap.contains("unlockedAtMs"), "Achievements should have unlockedAtMs field")
    }

    @Test
    fun `schema 4 weekly table exists`() {
        val schema4 = loadSchemaJson(4)
        val weeklyTable = schema4["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "weekly"
        }

        assertNotNull(weeklyTable, "Schema 4 should have weekly entity")

        val fields = weeklyTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Weekly table should have fields")

        val fieldMap = fields.map {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            path
        }.toSet()

        assertTrue(fieldMap.contains("weekKey"), "Weekly should have weekKey field")
        assertTrue(fieldMap.contains("metric"), "Weekly should have metric field")
        assertTrue(fieldMap.contains("value"), "Weekly should have value field")
        assertTrue(fieldMap.contains("completedAtMs"), "Weekly should have completedAtMs field")
    }

    @Test
    fun `schema 4 xp_ledger table exists`() {
        val schema4 = loadSchemaJson(4)
        val ledgerTable = schema4["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "xp_ledger"
        }

        assertNotNull(ledgerTable, "Schema 4 should have xp_ledger entity")

        val fields = ledgerTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "XP ledger table should have fields")

        val fieldMap = fields.map {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            path
        }.toSet()

        assertTrue(fieldMap.contains("sessionId"), "XP ledger should have sessionId field")
        assertTrue(fieldMap.contains("xp"), "XP ledger should have xp field")
        assertTrue(fieldMap.contains("atMs"), "XP ledger should have atMs field")
    }

    @Test
    fun `schema 4 version is 4`() {
        val schema4 = loadSchemaJson(4)
        val version = schema4["database"]?.jsonObject?.get("version")?.jsonPrimitive?.content
        assertEquals("4", version, "Schema 4 should have version 4")
    }

    private fun loadSchemaJson(version: Int): JsonObject {
        val possiblePaths = listOf(
            "app/schemas/com.clashfit.data.ClashDb/$version.json",
            "android/app/schemas/com.clashfit.data.ClashDb/$version.json",
            "../app/schemas/com.clashfit.data.ClashDb/$version.json"
        )

        var file: File? = null
        for (path in possiblePaths) {
            val candidate = File(path)
            if (candidate.exists()) {
                file = candidate
                break
            }
        }

        if (file == null || !file.exists()) {
            val resourcePath = "/schemas/com.clashfit.data.ClashDb/$version.json"
            val resourceStream = this::class.java.getResourceAsStream(resourcePath)
            if (resourceStream != null) {
                val content = resourceStream.bufferedReader().use { it.readText() }
                return kotlinx.serialization.json.Json.parseToJsonElement(content).jsonObject
            }
        }

        assertNotNull(file, "Schema file should exist for version $version")
        assertTrue(file.exists(), "Schema file should exist at ${file.absolutePath}")

        val content = file.readText()
        return kotlinx.serialization.json.Json.parseToJsonElement(content).jsonObject
    }
}
