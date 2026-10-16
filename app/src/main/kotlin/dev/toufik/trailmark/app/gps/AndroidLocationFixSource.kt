package dev.toufik.trailmark.app.gps

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.engine.FixSource
import dev.toufik.trailmark.engine.UNMEASURED_ACCURACY_M
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production [FixSource]: the device's GNSS. Monotonic time comes from
 * `Location.elapsedRealtimeNanos` (never the wall clock); the platform instant
 * is kept only as display metadata. Android substitutes 0.0 for fields it could
 * not measure, so unavailable measurements map to null and an unmeasured 1σ
 * maps to the worst-case sentinel.
 */
class AndroidLocationFixSource(private val context: Context) : FixSource {

    override fun fixes(): Flow<RawFix> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { loc -> trySend(loc.toRawFix()) }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        awaitClose { lm.removeUpdates(listener) }
    }

    private fun Location.toRawFix(): RawFix = RawFix(
        lat = latitude,
        lon = longitude,
        timeNanos = elapsedRealtimeNanos,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else UNMEASURED_ACCURACY_M,
        altitudeM = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearingDeg = if (hasBearing()) bearing.toDouble() else null,
        isMocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else false,
        wallMillis = time,
    )
}
