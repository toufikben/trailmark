package dev.toufik.trailmark.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.toufik.trailmark.data.ActivityRepository
import dev.toufik.trailmark.data.db.TrailmarkDb

/** App-wide singletons: the one file-backed database and its repository. */
object AppGraph {
    @Volatile private var repository: ActivityRepository? = null

    fun repository(context: Context): ActivityRepository {
        repository?.let { return it }
        synchronized(this) {
            repository?.let { return it }
            val app = context.applicationContext
            val driver = AndroidSqliteDriver(TrailmarkDb.Schema, app, "trailmark.db")
            val repo = ActivityRepository(TrailmarkDb(driver))
            repository = repo
            return repo
        }
    }
}
