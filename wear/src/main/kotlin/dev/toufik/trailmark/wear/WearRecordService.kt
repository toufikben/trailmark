package dev.toufik.trailmark.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.UNMEASURED_ACCURACY_M
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.FixQualityGate
import dev.toufik.trailmark.engine.RecorderEngine
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.engine.SportConfig
import dev.toufik.trailmark.portable.Gpx
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the watch face shows while the service records. */
data class WearState(
    val recording: Boolean = false,
    val distanceM: Double = 0.0,
    val elapsedNanos: Long = 0L,
    val fixCount: Int = 0,
    val fixing: Boolean = false,
    val sport: String = "RUN",
    val notes: String? = null,
)

object WearSession {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(WearState())
    val state: kotlinx.coroutines.flow.StateFlow<WearState> = _state

    fun update(block: (WearState) -> WearState) {
        _state.value = block(_state.value)
    }
}

/**
 * Foreground recorder for the watch: GNSS fixes feed the same engine as the
 * phone, the finished track is written as GPX and streamed to the phone over
 * a Wearable channel, where the phone app imports it into the activity list.
 */
class WearRecordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val points = ArrayList<StoredPoint>()
    private var engine: RecorderEngine? = null
    private var startedWallMillis = 0L
    private var sport = Sport.RUN

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent.getStringExtra(EXTRA_SPORT) ?: "RUN")
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun begin(sportName: String) {
        if (job != null) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            WearSession.update { it.copy(notes = "Location permission missing") }
            stopSelf()
            return
        }
        sport = Sport.entries.firstOrNull { it.name == sportName } ?: Sport.RUN
        startForeground(NOTIFICATION_ID, notification())
        points.clear()
        engine = null
        startedWallMillis = System.currentTimeMillis()
        WearSession.update { WearState(recording = true, sport = sport.name) }
        job = scope.launch {
            val gate = FixQualityGate { System.nanoTime() }
            fixes().collect { fix ->
                val ready = gate.add(fix)
                var current = engine
                if (current == null) {
                    if (!ready) {
                        WearSession.update { it.copy(fixing = true, notes = null) }
                        return@collect
                    }
                    current = RecorderEngine(
                        SportConfig.forSport(sport),
                        startNanos = fix.timeNanos,
                    )
                    engine = current
                }
                val active = current
                active.onFix(fix)
                val slice = active.storedSlice(points.size, active.storedCount)
                points.addAll(slice)
                WearSession.update {
                    it.copy(
                        recording = true,
                        distanceM = active.distanceMeters,
                        elapsedNanos = active.elapsedNanos,
                        fixCount = points.size,
                        fixing = false,
                    )
                }
            }
        }
    }

    private fun fixes(): Flow<RawFix> {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return emptyFlow()
        return callbackFlow {
            val listener = LocationListener { loc -> trySend(loc.toRawFix()) }
            runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
            runCatching {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
            awaitClose { runCatching { lm.removeUpdates(listener) } }
        }
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

    private fun stopRecording() {
        val running = job
        job = null
        running?.cancel()
        WearSession.update { it.copy(recording = false) }
        scope.launch {
            if (running != null) running.join()
            val visible = points.filter { it.type != WaypointType.REJECTED }
            val gpx = if (visible.size >= 2) Gpx.write(visible, startedWallMillis) else null
            if (gpx != null) {
                runCatching {
                    val dir = File(filesDir, "exports").apply { mkdirs() }
                    File(dir, "trailmark-watch-${System.currentTimeMillis()}.gpx")
                        .writeText(gpx)
                }
                val sent = sendToPhone(gpx.toByteArray(Charsets.UTF_8))
                WearSession.update {
                    it.copy(
                        recording = false,
                        notes = if (sent) "Sent to phone" else "Saved on watch (phone offline)",
                    )
                }
            } else {
                WearSession.update {
                    it.copy(recording = false, notes = "Too short to save")
                }
            }
            engine = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun sendToPhone(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(this@WearRecordService).connectedNodes)
            if (nodes.isEmpty()) return@runCatching false
            val channelClient = Wearable.getChannelClient(this@WearRecordService)
            var sent = false
            for (node in nodes) {
                runCatching {
                    val channel = Tasks.await(channelClient.openChannel(node.id, GPX_PATH))
                    val out = Tasks.await(channelClient.getOutputStream(channel))
                    out.use { it.write(bytes) }
                    Tasks.await(channelClient.close(channel))
                    sent = true
                }
            }
            sent
        }.getOrDefault(false)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Trailmark")
            .setContentText("Recording ${sport.name.lowercase()}")
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.toufik.trailmark.wear.START"
        const val ACTION_STOP = "dev.toufik.trailmark.wear.STOP"
        const val EXTRA_SPORT = "sport"
        const val GPX_PATH = "/trailmark/gpx"
        private const val CHANNEL_ID = "wear-recording"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context, sport: String) {
            val intent = Intent(context, WearRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SPORT, sport)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearRecordService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
