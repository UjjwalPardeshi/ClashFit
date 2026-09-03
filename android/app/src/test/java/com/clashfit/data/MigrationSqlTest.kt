package com.clashfit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Room validates a migrated database against its exported schema column by column, and that
 * comparison includes default values. A migration that creates a table with an extra `DEFAULT 0`
 * therefore passes SQLite and then makes Room throw on the very next launch, on exactly the phones
 * that already had the app installed. That is the worst kind of bug: invisible on a fresh install,
 * fatal on an upgrade.
 *
 * This pins every statement in [MIGRATION_3_4_SQL] against Room's own `createSql` for the same
 * table, so the two cannot drift apart again.
 */
class MigrationSqlTest {

    /** The working directory differs depending on where Gradle is invoked from. */
    private fun schemaFile(version: Int): File = listOf(
        "app/schemas/com.clashfit.data.ClashDb",
        "schemas/com.clashfit.data.ClashDb",
        "android/app/schemas/com.clashfit.data.ClashDb",
        "../app/schemas/com.clashfit.data.ClashDb",
    ).map { File(it, "$version.json") }.firstOrNull { it.exists() }
        ?: File("schemas/com.clashfit.data.ClashDb/$version.json")

    private fun createSqlByTable(version: Int): Map<String, String> {
        val file = schemaFile(version)
        assertTrue(file.exists(), "exported schema $version.json not found; looked from ${File(".").absolutePath}")
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val entities = requireNotNull(root["database"]?.jsonObject?.get("entities")?.jsonArray) {
            "schema $version.json has no database.entities"
        }
        return entities.associate { entity ->
            val obj = entity.jsonObject
            val table = requireNotNull(obj["tableName"]?.jsonPrimitive?.content) { "entity without a tableName" }
            val sql = requireNotNull(obj["createSql"]?.jsonPrimitive?.content) { "entity $table without createSql" }
            table to sql.replace("\${TABLE_NAME}", "`$table`")
        }
    }

    /** Whitespace and backticks vary; the column list and its defaults are what Room compares. */
    private fun normalise(sql: String) = sql.replace("`", "").replace(Regex("\\s+"), " ").trim()

    @Test
    fun `every statement in migration 3 to 4 matches the exported schema exactly`() {
        val expected = createSqlByTable(4)
        val tables = listOf("meta", "achievements", "weekly", "xp_ledger")
        assertEquals(tables.size, MIGRATION_3_4_SQL.size, "one statement per new table")

        tables.zip(MIGRATION_3_4_SQL).forEach { (table, actual) ->
            val want = expected[table]
            assertNotNull(want, "schema 4 has no entity for $table")
            assertEquals(
                normalise(want),
                normalise(actual),
                "the migration's SQL for `$table` differs from the schema Room will validate against",
            )
        }
    }

    @Test
    fun `the migration declares no default values`() {
        // Kotlin default arguments are not SQL defaults. Room's expected schema has none here, so
        // neither may the migration.
        MIGRATION_3_4_SQL.forEach { sql ->
            assertTrue(
                !sql.contains("DEFAULT", ignoreCase = true),
                "a DEFAULT clause in this statement will make Room reject the upgraded database:\n$sql",
            )
        }
    }

    @Test
    fun `schema 4 adds exactly the four progression tables to schema 3`() {
        val before = createSqlByTable(3).keys
        val after = createSqlByTable(4).keys
        assertEquals(
            setOf("meta", "achievements", "weekly", "xp_ledger"),
            after - before,
            "version 4 should add the progression tables and nothing else",
        )
        assertTrue(before.all { it in after }, "version 4 must not drop a table that version 3 had")
    }
}
