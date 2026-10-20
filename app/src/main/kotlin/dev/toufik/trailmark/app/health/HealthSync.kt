package dev.toufik.trailmark.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.kilocalories
import androidx.health.connect.client.units.meters
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.data.ActivityRow
import dev.toufik.trailmark.engine.Calories
import java.time.Instant
import java.time.ZoneId

/** Writes finished activities into Health Connect (session, distance, energy). */
object HealthSync {

    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )

    fun available(context: Context): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    suspend fun granted(context: Context): Boolean = runCatching {
        if (!available(context)) return false
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(writePermissions)
    }.getOrDefault(false)

    /** True when the records were accepted. Quietly false without access. */
    suspend fun writeActivity(
        context: Context,
        row: ActivityRow,
        weightKg: Double,
    ): Boolean = runCatching {
        if (!available(context) || !granted(context)) return false
        if (row.elapsedNanos <= 0L || row.distanceM <= 0.0) return false
        val start = Instant.ofEpochMilli(row.startWallMillis)
        val end = start.plusNanos(row.elapsedNanos)
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val records = ArrayList<Record>(3)
        records.add(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                exerciseType = exerciseType(row.sport),
            ),
        )
        records.add(
            DistanceRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                distance = row.distanceM.meters,
            ),
        )
        val kcal = Calories.estimateKcal(row.sport, row.elapsedNanos / 1e9, weightKg)
        if (kcal > 0.0) {
            records.add(
                TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    energy = kcal.kilocalories,
                ),
            )
        }
        HealthConnectClient.getOrCreate(context).insertRecords(records)
        true
    }.getOrDefault(false)

    /**
     * Writes freshly imported activities — including tracks sent by the Wear
     * app — to Health Connect, honouring the same preference as recordings.
     */
    suspend fun writeActivities(context: Context, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences("trailmark", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("health_sync", true)) return
        val weightKg = prefs.getFloat("weight_kg", 70f).toDouble()
        val repo = AppGraph.repository(context)
        for (id in ids) {
            val row = repo.activityById(id) ?: continue
            writeActivity(context, row, weightKg)
        }
    }

    /** Health Connect exercise-type codes for Trailmark sport strings. */
    private fun exerciseType(sport: String): Int = when (sport) {
        "WALK" -> 79
        "BIKE" -> 8
        "HIKE" -> 37
        else -> 56
    }
}
