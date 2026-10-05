package com.timetrack.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: the day and week todo lists.
 *
 * Hand written rather than an `@AutoMigration`, because `exportSchema` was off
 * while v1 was already in the wild, so there is no v1 schema for Room to diff
 * against. The v1 JSON under `app/schemas` was therefore captured from the
 * released code before the todo entity existed, and must not be regenerated
 * from the current entities.
 *
 * Each statement is copied verbatim from `2.json`, the schema Room derives from
 * the current entities, with `${TABLE_NAME}` resolved. On the first open after
 * an update, Room re-validates the migrated database against that schema and
 * throws on any difference in a column's affinity, its nullability or an index
 * name — which the user experiences as the app crashing on launch. So this is
 * not "roughly equivalent" SQL, it has to match exactly.
 *
 * [MIGRATION_1_2_STATEMENTS] is separate from the migration object purely so
 * `MigrationsSchemaTest` can compare it against the generated schema without a
 * device. That test is the thing standing between a careless edit and every
 * installed copy of the app failing to open.
 */
internal val MIGRATION_1_2_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `todos` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`title` TEXT NOT NULL, " +
        "`scope` TEXT NOT NULL, " +
        "`periodKey` TEXT NOT NULL, " +
        "`createdAt` INTEGER NOT NULL, " +
        "`doneAt` INTEGER)",
    "CREATE INDEX IF NOT EXISTS `index_todos_scope_periodKey` " +
        "ON `todos` (`scope`, `periodKey`)",
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_STATEMENTS.forEach(db::execSQL)
    }
}
