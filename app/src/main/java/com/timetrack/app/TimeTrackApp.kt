package com.timetrack.app

import android.app.Application
import com.timetrack.app.data.TimeTrackDatabase
import com.timetrack.app.data.TimeTrackRepository
import com.timetrack.app.data.TodoRepository

class TimeTrackApp : Application() {

    val repository: TimeTrackRepository by lazy {
        TimeTrackRepository(TimeTrackDatabase.get(this))
    }

    /** Todos are a separate domain from tracked intervals, hence a second repository. */
    val todoRepository: TodoRepository by lazy {
        TodoRepository(TimeTrackDatabase.get(this))
    }
}
