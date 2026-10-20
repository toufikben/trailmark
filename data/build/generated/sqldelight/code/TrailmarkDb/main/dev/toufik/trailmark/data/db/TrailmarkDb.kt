package dev.toufik.trailmark.`data`.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.toufik.trailmark.`data`.ActivityQueries
import dev.toufik.trailmark.`data`.db.`data`.newInstance
import dev.toufik.trailmark.`data`.db.`data`.schema
import kotlin.Unit

public interface TrailmarkDb : Transacter {
  public val activityQueries: ActivityQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = TrailmarkDb::class.schema

    public operator fun invoke(driver: SqlDriver): TrailmarkDb =
        TrailmarkDb::class.newInstance(driver)
  }
}
