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

    /**
     * Migration 4 to 5 adds columns rather than tables, so the trap is the mirror image of the one
     * above: `ALTER TABLE … ADD COLUMN … NOT NULL` *requires* a SQL default, and Room will reject
     * the database unless its exported schema expects exactly that default. The entity therefore
     * carries `@ColumnInfo(defaultValue = …)`, and this pins the two together.
     */
    @Test
    fun `every column added by migration 4 to 5 matches the exported schema exactly`() {
        val runsSql = normalise(requireNotNull(createSqlByTable(5)["runs"]) { "schema 5 has no runs entity" })
        assertEquals(3, MIGRATION_4_5_SQL.size, "three columns are added")

        // Each ALTER must name a column definition that appears verbatim in schema 5's CREATE.
        val expected = listOf(
            "kind TEXT NOT NULL DEFAULT 'RUN'",
            "steps INTEGER NOT NULL DEFAULT 0",
            "fastestKmSec REAL",
        )
        expected.zip(MIGRATION_4_5_SQL).forEach { (column, alter) ->
            assertTrue(
                runsSql.contains(column),
                "schema 5's runs table does not declare `$column`; the migration and the entity have drifted.\n$runsSql",
            )
            assertTrue(
                normalise(alter).endsWith(column),
                "the migration statement does not add `$column` exactly as the schema declares it:\n$alter",
            )
            assertTrue(normalise(alter).startsWith("ALTER TABLE runs ADD COLUMN"), "expected an ALTER on runs:\n$alter")
        }
    }

    @Test
    fun `the column added by migration 5 to 6 matches the exported schema exactly`() {
        val runsSql = normalise(requireNotNull(createSqlByTable(6)["runs"]) { "schema 6 has no runs entity" })
        assertEquals(1, MIGRATION_5_6_SQL.size, "one column is added")
        val column = "score INTEGER NOT NULL DEFAULT 0"
        assertTrue(runsSql.contains(column), "schema 6's runs table does not declare `$column` in $runsSql")
        val alter = normalise(MIGRATION_5_6_SQL.single())
        assertTrue(alter.endsWith(column), "the migration does not add `$column` as the schema declares it: $alter")
        assertTrue(alter.startsWith("ALTER TABLE runs ADD COLUMN"), "expected an ALTER on runs: $alter")
    }

    @Test
    fun `the table added by migration 7 to 8 matches the exported schema exactly`() {
        // This is the test that caught the real mistake. The first version of this migration wrote
        // the id as a table-level `PRIMARY KEY(id)` with a separate NOT NULL, which produces a
        // perfectly working table — and not the same table. Room generates
        // `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, records the AUTOINCREMENT in its schema,
        // and compares. A mismatch does not fail the build; it throws on the first launch after an
        // upgrade, on somebody's phone, holding their history.
        val expected = requireNotNull(createSqlByTable(8)["breathing_sessions"]) {
            "schema 8 has no breathing_sessions entity"
        }
        assertEquals(1, MIGRATION_7_8_SQL.size, "one statement, one new table")
        assertEquals(normalise(expected), normalise(MIGRATION_7_8_SQL.single()))
    }

    @Test
    fun `schema 8 adds breathing sessions and touches nothing else`() {
        val before = createSqlByTable(7)
        val after = createSqlByTable(8)
        assertEquals(
            before.keys + "breathing_sessions",
            after.keys,
            "version 8 must add exactly one table and drop none",
        )
        before.forEach { (table, sql) ->
            assertEquals(normalise(sql), normalise(after.getValue(table)), "version 8 changed `$table`")
        }
    }

    @Test
    fun `schema 6 changes nothing but the runs table`() {
        val before = createSqlByTable(5)
        val after = createSqlByTable(6)
        assertEquals(before.keys, after.keys, "version 6 must add and drop no tables")
        before.forEach { (table, sql) ->
            if (table == "runs") return@forEach
            assertEquals(normalise(sql), normalise(after.getValue(table)), "version 6 changed `$table`")
        }
    }

    @Test
    fun `schema 5 changes nothing but the runs table`() {
        val before = createSqlByTable(4)
        val after = createSqlByTable(5)
        assertEquals(before.keys, after.keys, "version 5 must add and drop no tables")
        before.forEach { (table, sql) ->
            if (table == "runs") return@forEach
            assertEquals(
                normalise(sql),
                normalise(after.getValue(table)),
                "version 5 changed `$table`, which migration 4 to 5 does not touch",
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
