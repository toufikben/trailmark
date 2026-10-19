package dev.toufik.trailmark.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.toufik.trailmark.data.ActivityRepository
import dev.toufik.trailmark.data.db.TrailmarkDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons: the one file-backed database, its repository and a background scope. */
object AppGraph {
    @Volatile private var repository: ActivityRepository? = null

    /** Outlives every screen: used for one-off background work like segment backfill. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
