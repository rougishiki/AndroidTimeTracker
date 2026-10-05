package com.timetrack.app.data

import androidx.room.TypeConverter

/**
 * Room cannot store an enum directly, and the obvious alternative — an ordinal
 * — would silently change meaning if the constants were ever reordered.
 *
 * The name is used instead, so the column stays readable from a plain SQLite
 * shell and reordering [TodoScope] cannot corrupt existing rows.
 */
class Converters {

    @TypeConverter
    fun scopeToName(scope: TodoScope): String = scope.name

    @TypeConverter
    fun nameToScope(name: String): TodoScope = TodoScope.valueOf(name)
}
