package dev.toufik.trailmark.data

import app.cash.sqldelight.db.SqlDriver
import dev.toufik.trailmark.data.db.TrailmarkDb

/** Create the schema on a fresh database, then open it. */
fun createDatabase(driver: SqlDriver): TrailmarkDb {
    TrailmarkDb.Schema.create(driver)
    return TrailmarkDb(driver)
}
