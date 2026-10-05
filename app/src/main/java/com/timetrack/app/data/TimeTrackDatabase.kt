package com.timetrack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Task::class, Session::class],
    version = 1,
    exportSchema = false,
)
abstract class TimeTrackDatabase : RoomDatabase() {

    abstract fun dao(): TimeTrackDao

    companion object {
        @Volatile
        private var instance: TimeTrackDatabase? = null

        fun get(context: Context): TimeTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeTrackDatabase::class.java,
                    "timetrack.db",
                ).build().also { instance = it }
            }
    }
}
