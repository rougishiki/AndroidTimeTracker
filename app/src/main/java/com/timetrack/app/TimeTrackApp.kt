package com.timetrack.app

import android.app.Application
import com.timetrack.app.data.TimeTrackDatabase
import com.timetrack.app.data.TimeTrackRepository

class TimeTrackApp : Application() {

    val repository: TimeTrackRepository by lazy {
        TimeTrackRepository(TimeTrackDatabase.get(this))
    }
}
