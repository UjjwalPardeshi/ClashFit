package com.clashfit.data

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
 * Verifies that database schema files contain the correct table structures.
 * Loads exported Room schema JSON files (1.json, 2.json, 3.json) and validates entity definitions.
 */
class MigrationTest {

    @Test
    fun `schema 2 posture table has 5 columns with correct types`() {
        // posture was introduced by MIGRATION_1_2, so it first appears in schema 2 (not 1).
        val schema2 = loadSchemaJson(2)
        val postureTable = schema2["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "posture"
        }

        assertNotNull(postureTable, "Schema 2 should have posture entity")

        val fields = postureTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Posture table should have fields")
        assertEquals(5, fields.size, "Posture table should have 5 columns")

        // Verify column order and types
        val fieldMap = fields.associate {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            val affinity = obj["affinity"]?.jsonPrimitive?.content ?: ""
            path to affinity
        }

        assertEquals("INTEGER", fieldMap["id"], "id should be INTEGER")
        assertEquals("INTEGER", fieldMap["tMs"], "tMs should be INTEGER")
        assertEquals("INTEGER", fieldMap["score"], "score should be INTEGER")
        assertEquals("REAL", fieldMap["neckDeg"], "neckDeg should be REAL")
        assertEquals("REAL", fieldMap["elevation"], "elevation should be REAL")
    }

    @Test
    fun `schema 2 posture table has correct column order`() {
        val schema2 = loadSchemaJson(2)
        val postureTable = schema2["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "posture"
        }

        assertNotNull(postureTable, "Schema 2 should have posture entity")

        val fields = postureTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Posture table should have fields")

        val columnNames = fields.map { it.jsonObject["columnName"]?.jsonPrimitive?.content ?: "" }
        assertEquals(listOf("id", "tMs", "score", "neckDeg", "elevation"), columnNames)
    }

    @Test
    fun `schema 2 posture table has index on tMs`() {
        val schema2 = loadSchemaJson(2)
        val postureTable = schema2["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "posture"
        }

        assertNotNull(postureTable, "Schema 2 should have posture entity")

        val indices = postureTable.jsonObject.get("indices")?.jsonArray
        assertNotNull(indices, "Posture table should have indices")
        assertTrue(indices.size > 0, "Posture table should have at least one index")

        val tMsIndex = indices.find {
            it.jsonObject["columnNames"]?.jsonArray?.any {
                it.jsonPrimitive.content == "tMs"
            } ?: false
        }

        assertNotNull(tMsIndex, "Should have index on tMs column")
    }

    @Test
    fun `MIGRATION_1_2 CREATE TABLE and CREATE INDEX statements match schema 2 posture entity`() {
        val schema2 = loadSchemaJson(2)
        val postureTable = schema2["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "posture"
        }
        assertNotNull(postureTable, "Schema 2 should have posture entity")

        val fields = postureTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Posture table should have fields")

        val dbSource = loadClashDbSource()

        val migrationBlock = Regex(
            "MIGRATION_1_2\\s*=.*?override fun migrate.*?\\{(.*?)\\n\\s*\\}",
            RegexOption.DOT_MATCHES_ALL
        ).find(dbSource)?.groupValues?.get(1)
        assertNotNull(migrationBlock, "Should find MIGRATION_1_2.migrate(...) body in ClashDb.kt")

        val createTableColumns = Regex(
            "CREATE TABLE IF NOT EXISTS posture \\((.*?)\\)",
            RegexOption.DOT_MATCHES_ALL
        ).find(migrationBlock).let {
            assertNotNull(it, "MIGRATION_1_2 should contain a CREATE TABLE posture statement")
            it.groupValues[1]
        }

        // Column definitions are comma-separated; verify name, affinity, order and NOT NULL
        // flag for every column the exported schema says the posture table has.
        val columnClauses = createTableColumns.split(",").map { it.trim() }
        var previousIndex = -1
        fields.forEach { field ->
            val obj = field.jsonObject
            val columnName = obj["columnName"]?.jsonPrimitive?.content ?: ""
            val affinity = obj["affinity"]?.jsonPrimitive?.content ?: ""
            val notNull = obj["notNull"]?.jsonPrimitive?.booleanOrNull ?: false

            val clauseIndex = columnClauses.indexOfFirst { it.startsWith(columnName) }
            assertTrue(clauseIndex >= 0, "MIGRATION_1_2 should name column $columnName")
            assertTrue(
                clauseIndex > previousIndex,
                "Column $columnName should appear in the same order as schema 2"
            )
            previousIndex = clauseIndex

            val clause = columnClauses[clauseIndex]
            assertTrue(
                clause.contains(affinity),
                "Column $columnName should have affinity $affinity in MIGRATION_1_2 SQL, was: $clause"
            )
            assertEquals(
                notNull,
                clause.contains("NOT NULL"),
                "Column $columnName NOT NULL flag mismatch between MIGRATION_1_2 SQL and schema 2, was: $clause"
            )
        }

        val indices = postureTable.jsonObject["indices"]?.jsonArray
        assertNotNull(indices, "Schema 2 posture table should have indices")
        indices.forEach { index ->
            val indexObj = index.jsonObject
            val indexName = indexObj["name"]?.jsonPrimitive?.content ?: ""
            val columnNames = indexObj["columnNames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            assertTrue(
                migrationBlock.contains("CREATE INDEX IF NOT EXISTS $indexName"),
                "MIGRATION_1_2 should create index $indexName"
            )
            columnNames.forEach { columnName ->
                assertTrue(
                    Regex("CREATE INDEX IF NOT EXISTS $indexName ON posture\\s*\\([^)]*\\b$columnName\\b[^)]*\\)")
                        .containsMatchIn(migrationBlock),
                    "Index $indexName should be on posture($columnName)"
                )
            }
        }
    }

    @Test
    fun `schema 3 sets table has asymmetryPct and weakerSide columns`() {
        val schema3 = loadSchemaJson(3)
        val setsTable = schema3["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "sets"
        }

        assertNotNull(setsTable, "Schema 3 should have sets entity")

        val fields = setsTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Sets table should have fields")

        val fieldMap = fields.associate {
            val obj = it.jsonObject
            val path = obj["fieldPath"]?.jsonPrimitive?.content ?: ""
            val affinity = obj["affinity"]?.jsonPrimitive?.content ?: ""
            // Room omits the "notNull" key entirely for nullable columns, so its absence means false.
            val notNull = obj["notNull"]?.jsonPrimitive?.booleanOrNull ?: false
            path to (affinity to notNull)
        }

        assertTrue(fieldMap.containsKey("asymmetryPct"), "Schema 3 sets should have asymmetryPct column")
        assertTrue(fieldMap.containsKey("weakerSide"), "Schema 3 sets should have weakerSide column")

        // Verify types (both fields are nullable in the schema)
        val asymmetryPct = fieldMap["asymmetryPct"]
        assertEquals("INTEGER", asymmetryPct?.first, "asymmetryPct should be INTEGER")
        assertEquals(false, asymmetryPct?.second, "asymmetryPct should be nullable (notNull=false)")

        val weakerSide = fieldMap["weakerSide"]
        assertEquals("TEXT", weakerSide?.first, "weakerSide should be TEXT")
        assertEquals(false, weakerSide?.second, "weakerSide should be nullable (notNull=false)")
    }

    @Test
    fun `schema 3 sets table has all required columns from schema 1`() {
        val schema3 = loadSchemaJson(3)
        val setsTable = schema3["database"]?.jsonObject?.get("entities")?.jsonArray?.find {
            it.jsonObject["tableName"]?.jsonPrimitive?.content == "sets"
        }

        assertNotNull(setsTable, "Schema 3 should have sets entity")

        val fields = setsTable.jsonObject["fields"]?.jsonArray
        assertNotNull(fields, "Sets table should have fields")

        val fieldNames = fields.map { it.jsonObject["fieldPath"]?.jsonPrimitive?.content ?: "" }

        // Verify original columns still exist
        assertTrue(fieldNames.contains("sessionId"), "Sets should have sessionId")
        assertTrue(fieldNames.contains("setIndex"), "Sets should have setIndex")
        assertTrue(fieldNames.contains("exerciseId"), "Sets should have exerciseId")
        assertTrue(fieldNames.contains("reps"), "Sets should have reps")
        assertTrue(fieldNames.contains("formMean"), "Sets should have formMean")
    }

    @Test
    fun `schema 3 version is 3 and identity hash updated from schema 1`() {
        val schema1 = loadSchemaJson(1)
        val schema3 = loadSchemaJson(3)

        val version1 = schema1["database"]?.jsonObject?.get("version")?.jsonPrimitive?.content
        val version3 = schema3["database"]?.jsonObject?.get("version")?.jsonPrimitive?.content

        assertEquals("1", version1, "Schema 1 should have version 1")
        assertEquals("3", version3, "Schema 3 should have version 3")

        val hash1 = schema1["database"]?.jsonObject?.get("identityHash")?.jsonPrimitive?.content
        val hash3 = schema3["database"]?.jsonObject?.get("identityHash")?.jsonPrimitive?.content

        assertNotNull(hash1, "Schema 1 should have identityHash")
        assertNotNull(hash3, "Schema 3 should have identityHash")
        assertTrue(hash1 != hash3, "Identity hash should differ after schema changes")
    }

    private fun loadSchemaJson(version: Int): JsonObject {
        // Try multiple paths: relative to project root, relative to test directory, or absolute
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

        // If not found in filesystem, try loading from classpath
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

    private fun loadClashDbSource(): String {
        // Same candidate-path strategy as loadSchemaJson: the test working directory
        // varies depending on whether Gradle runs from the repo root, the android/
        // project root, or the app/ module directory.
        val possiblePaths = listOf(
            "app/src/main/java/com/clashfit/data/ClashDb.kt",
            "android/app/src/main/java/com/clashfit/data/ClashDb.kt",
            "../app/src/main/java/com/clashfit/data/ClashDb.kt"
        )

        val file = possiblePaths.map { File(it) }.find { it.exists() }
        assertNotNull(file, "ClashDb.kt source should be found at one of: $possiblePaths")
        assertTrue(file.exists(), "ClashDb.kt should exist at ${file.absolutePath}")

        return file.readText()
    }
}
