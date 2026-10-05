package com.timetrack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Task::class, Session::class, Todo::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TimeTrackDatabase : RoomDatabase() {

    abstract fun dao(): TimeTrackDao

    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var instance: TimeTrackDatabase? = null

        fun get(context: Context): TimeTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeTrackDatabase::class.java,
                    "timetrack.db",
                )
                    // Never fall back to a destructive migration: the tracking
                    // history only exists on the device, so a dropped table is
                    // unrecoverable data loss rather than an inconvenience.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
