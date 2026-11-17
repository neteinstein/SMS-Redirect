package com.neteinstein.smsredirect.data.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * There is no instrumented test source set, so this checks the hand-written migration DDL
 * against the schema Room itself exported. A mismatch would crash every upgrading user on open.
 */
class AppDatabaseMigrationTest {
    private fun rulesCreateSql(version: Int): String {
        val schema = File("schemas/com.neteinstein.smsredirect.data.local.AppDatabase/$version.json").readText()
        return Regex(
            "\"createSql\": \"(CREATE TABLE IF NOT EXISTS `\\$\\{TABLE_NAME\\}` \\(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `senderText`[^\"]*)\"",
        ).find(schema)
            ?.groupValues
            ?.get(1)
            ?: error("rules table not found in $version.json")
    }

    @Test
    fun `migration 11 to 12 adds exactly the columns Room declares for rules`() {
        val v11 = rulesCreateSql(11)
        val v12 = rulesCreateSql(12)

        AppDatabase.RULES_V12_COLUMNS.forEach { column ->
            assertTrue("12.json is missing $column", v12.contains(column))
        }
        // The v12 table is the v11 table plus the new columns, in order.
        val expected = v11.removeSuffix(")") + AppDatabase.RULES_V12_COLUMNS.joinToString("") { ", $it" } + ")"
        assertTrue("v12 rules table differs from v11 + RULES_V12_COLUMNS:\n$v12\n$expected", v12 == expected)
    }
}
