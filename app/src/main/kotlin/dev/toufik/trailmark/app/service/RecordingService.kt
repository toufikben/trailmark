package dev.toufik.trailmark.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.toufik.trailmark.R
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.MainActivity
import dev.toufik.trailmark.app.WeatherService
import dev.toufik.trailmark.app.gps.AndroidLocationFixSource
import dev.toufik.trailmark.app.gps.SyntheticFixSource
import dev.toufik.trailmark.app.textsFor
import dev.toufik.trailmark.coach.Coach
import dev.toufik.trailmark.coach.CoachEvent
import dev.toufik.trailmark.coach.Locale as CoachLocale
import dev.toufik.trailmark.engine.FixSource
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.recording.Phase
import dev.toufik.trailmark.recording.RecordingController
import dev.toufik.trailmark.recording.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service of type location: keeps GNSS alive and the CPU awake while
 * the screen is off. Owns the one [RecordingController] for the session.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: RecordingController? = null
    private var stateJob: Job? = null
    private var notifJob: Job? = null
    private var voiceJob: Job? = null
    private var weatherJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var segStarts: List<dev.toufik.trailmark.data.SegmentRow> = emptyList()
    private val alertedSegments = mutableSetOf<Long>()

    @Volatile
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> scope.launch { controller?.pause() }
            ACTION_RESUME -> scope.launch { controller?.resume() }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun currentLang(): Lang {
        val code = getSharedPreferences("trailmark", MODE_PRIVATE).getString("lang_code", null)
        return Lang.entries.firstOrNull { it.code == code } ?: Lang.AR
    }

    private fun coachLocale(lang: Lang): CoachLocale = when (lang) {
        Lang.FR -> CoachLocale.FR
        Lang.AR -> CoachLocale.AR
        else -> CoachLocale.EN
    }

    private fun speechLocale(lang: Lang): java.util.Locale = when (coachLocale(lang)) {
        CoachLocale.AR -> java.util.Locale("ar")
        CoachLocale.FR -> java.util.Locale.FRENCH
        CoachLocale.EN -> java.util.Locale.ENGLISH
    }

    private fun startRecording(intent: Intent) {
        if (controller != null) return
        WeatherHolder.update(null)
        createChannel()
        val synthetic = intent.getBooleanExtra(EXTRA_SYNTHETIC, false)
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
                synthetic -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            },
        )
        acquireWakeLock()

        val sport = intent.getStringExtra(EXTRA_SPORT)
            ?.let { runCatching { Sport.valueOf(it) }.getOrNull() } ?: Sport.RUN
        val source: FixSource = if (intent.getBooleanExtra(EXTRA_SYNTHETIC, false)) {
            SyntheticFixSource()
        } else {
            AndroidLocationFixSource(this)
        }
        val c = RecordingController(
            repo = dev.toufik.trailmark.app.AppGraph.repository(this),
            source = source,
            scope = scope,
            sport = sport,
        )
        controller = c
        c.setAutoPause(
            getSharedPreferences("trailmark", MODE_PRIVATE).getBoolean("auto_pause", true),
        )
        scope.launch {
            runCatching {
                segStarts = dev.toufik.trailmark.app.AppGraph.repository(this@RecordingService)
                    .listSegments()
            }
        }
        stateJob = scope.launch { c.state.collect { RecordingHolder.update(it) } }
        notifJob = scope.launch {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive) {
                val st = c.state.value
                if (st.phase == Phase.RECORDING || st.phase == Phase.PAUSED) {
                    mgr.notify(NOTIF_ID, buildNotification(st))
                    delay(5_000L)
                } else {
                    delay(2_000L)
                }
            }
        }
        weatherJob = scope.launch {
            while (isActive) {
                val st = c.state.value
                val la = st.currentLat
                val lo = st.currentLon
                if (la != null && lo != null && st.phase == Phase.RECORDING) {
                    WeatherService.at(la, lo)?.let { WeatherHolder.update(it) }
                    delay(5 * 60 * 1000L)
                } else {
                    delay(30_000L)
                }
            }
        }
        if (getSharedPreferences("trailmark", MODE_PRIVATE).getBoolean("voice_coach", true)) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = speechLocale(currentLang()) }
                    ttsReady = true
                }
            }
            voiceJob = scope.launch {
                var lastKm = 0
                while (isActive) {
                    val st = c.state.value
                    if (st.phase == Phase.RECORDING) {
                        val km = (st.distanceM / 1000.0).toInt()
                        if (km > lastKm) {
                            lastKm = km
                            if (ttsReady) {
                                tts?.speak(
                                    Coach.message(
                                        CoachEvent.Milestone(km),
                                        coachLocale(currentLang()),
                                        km,
                                    ),
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "milestone-$km",
                                )
                            }
                            buzz(longArrayOf(0, 120))
                        }
                        val la = st.currentLat
                        val lo = st.currentLon
                        if (la != null && lo != null) {
                            for (s in segStarts) {
                                if (s.id in alertedSegments) continue
                                val near = dev.toufik.trailmark.core.geo.haversineMeters(
                                    la, lo, s.startLat, s.startLon,
                                ) < 30.0
                                if (near) {
                                    alertedSegments.add(s.id)
                                    if (ttsReady) {
                                        tts?.speak(
                                            Coach.message(
                                                CoachEvent.SegmentStart(s.name),
                                                coachLocale(currentLang()),
                                                s.name.hashCode(),
                                            ),
                                            TextToSpeech.QUEUE_ADD,
                                            null,
                                            "segment-${s.id}",
                                        )
                                    }
                                    buzz(longArrayOf(0, 120, 100, 120))
                                }
                            }
                        }
                    }
                    delay(2_000L)
                }
            }
        }
        scope.launch { c.start() }
    }

    private fun stopRecording() {
        val c = controller
        val repo = dev.toufik.trailmark.app.AppGraph.repository(this)
        scope.launch {
            c?.stop()
            val finished = c?.state?.value?.lastActivityId
            val weather = WeatherHolder.state.value
            if (finished != null && weather != null) {
                runCatching {
                    repo.setWeather(
                        activityId = finished,
                        tempC = weather.tempC,
                        windKmh = weather.windKmh,
                        windDeg = weather.windDeg,
                        code = weather.code,
                    )
                }
            }
            WeatherHolder.update(null)
            if (finished != null) {
                buzz(longArrayOf(0, 200, 120, 200))
                runCatching {
                    val points = repo.loadPoints(finished)
                    dev.toufik.trailmark.app.matchSegments(repo, finished, points)
                }
                val prefs = getSharedPreferences("trailmark", MODE_PRIVATE)
                if (prefs.getBoolean("health_sync", true)) {
                    runCatching {
                        val row = repo.activityById(finished)
                        if (row != null) {
                            dev.toufik.trailmark.app.health.HealthSync.writeActivity(
                                this@RecordingService,
                                row,
                                prefs.getFloat("weight_kg", 70f).toDouble(),
                            )
                        }
                    }
                }
            }
            RecordingHolder.update(RecordingState(phase = Phase.STOPPED, lastActivityId = finished))
            stateJob?.cancel()
            notifJob?.cancel()
            notifJob = null
            weatherJob?.cancel()
            weatherJob = null
            voiceJob?.cancel()
            voiceJob = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
            segStarts = emptyList()
            alertedSegments.clear()
            controller = null
            releaseWakeLock()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trailmark:recording").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buzz(timings: LongArray) {
        runCatching {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val t = textsFor(currentLang())
        mgr.deleteNotificationChannel(CHANNEL_ID)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, t.tabRecord, NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(state: RecordingState? = null): Notification {
        val t = textsFor(currentLang())
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val live = state?.takeIf { it.phase == Phase.RECORDING || it.phase == Phase.PAUSED }
        val text = if (live == null) {
            t.recordingNow
        } else {
            val prefix = if (live.phase == Phase.PAUSED) "\u23f8 " else ""
            "$prefix${dev.toufik.trailmark.app.formatDistance(live.distanceM)} " +
                "${dev.toufik.trailmark.app.distanceUnit(t)} \u00b7 ${dev.toufik.trailmark.app.formatDuration(live.elapsedNanos)}"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_track)
            .setContentTitle("Trailmark")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (live != null) {
            if (live.phase == Phase.RECORDING) {
                builder.addAction(0, t.pause, actionIntent(ACTION_PAUSE, 1))
            } else {
                builder.addAction(0, t.resume, actionIntent(ACTION_RESUME, 2))
            }
        }
        return builder.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_START = "dev.toufik.trailmark.action.START"
        const val ACTION_STOP = "dev.toufik.trailmark.action.STOP"
        const val ACTION_PAUSE = "dev.toufik.trailmark.action.PAUSE"
        const val ACTION_RESUME = "dev.toufik.trailmark.action.RESUME"
        const val EXTRA_SPORT = "sport"
        const val EXTRA_SYNTHETIC = "synthetic"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 4711
    }
}
