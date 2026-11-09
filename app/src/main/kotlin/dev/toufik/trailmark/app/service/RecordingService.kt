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
import android.os.SystemClock
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
import dev.toufik.trailmark.coach.TurnDirection
import dev.toufik.trailmark.coach.Locale as CoachLocale
import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.TurnCue
import dev.toufik.trailmark.core.geo.TurnKind
import dev.toufik.trailmark.core.geo.flattenChunks
import dev.toufik.trailmark.core.geo.matchToRoute
import dev.toufik.trailmark.core.geo.polylineLengthMeters
import dev.toufik.trailmark.core.geo.turnCues
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
    private var navJob: Job? = null
    private var weatherJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var segStarts: List<dev.toufik.trailmark.data.SegmentRow> = emptyList()
    private val alertedSegments = mutableSetOf<Long>()
    private val segmentStartedNanos = mutableMapOf<Long, Long>()
    private var planPoints: List<ElevPoint> = emptyList()
    private var navCues: List<TurnCue> = emptyList()
    private var planTotalM = 0.0
    private var navChunksRef: List<dev.toufik.trailmark.core.geo.RouteChunk> = emptyList()
    private val announcedCues = mutableSetOf<Int>()
    private var offRouteSinceNanos = 0L
    private var lastOffRouteNanos = 0L
    private var joinedRoute = false

    @Volatile
    private var sportName: String = "RUN"

    @Volatile
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(
                sportHint = intent.getStringExtra(EXTRA_SPORT),
                synthetic = intent.getBooleanExtra(EXTRA_SYNTHETIC, false),
                userInitiated = true,
            )
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> scope.launch { controller?.pause() }
            ACTION_RESUME -> scope.launch { controller?.resume() }
            // No action means Android restarted us after killing the process
            // (START_STICKY): continue the session from its checkpoint.
            null -> resumeAfterKill()
            else -> stopSelf()
        }
        return START_STICKY
    }

    /**
     * The system killed the process mid-run (low memory, or a crash in the UI
     * thread — same process). The checkpoint is already on disk, so the run
     * continues instead of silently ending. A crash loop is not retried: after
     * three crashes the session stays interrupted and the app offers it back.
     */
    private fun resumeAfterKill() {
        val repo = runCatching { dev.toufik.trailmark.app.AppGraph.repository(this) }.getOrNull()
        val resume = repo?.loadResume()
        if (resume == null) {
            stopSelf()
            return
        }
        val prefs = getSharedPreferences("trailmark", MODE_PRIVATE)
        if (prefs.getInt(KEY_CRASHES, 0) >= 3) {
            prefs.edit().putBoolean(KEY_INTERRUPTED, true).apply()
            stopSelf()
            return
        }
        val sport = repo.activityById(resume.activityId)?.sport
        startRecording(sportHint = sport, synthetic = false, userInitiated = false)
    }

    private fun currentLang(): Lang {
        val code = getSharedPreferences("trailmark", MODE_PRIVATE).getString("lang_code", null)
        return Lang.entries.firstOrNull { it.code == code } ?: Lang.AR
    }

    /** Coach banks cover every UI language; names line up with the Lang enum. */
    private fun coachLocale(lang: Lang): CoachLocale =
        CoachLocale.entries.firstOrNull { it.name == lang.name } ?: CoachLocale.EN

    private fun coachDirection(kind: TurnKind): TurnDirection = when (kind) {
        TurnKind.LEFT -> TurnDirection.LEFT
        TurnKind.RIGHT -> TurnDirection.RIGHT
        TurnKind.SLIGHT_LEFT -> TurnDirection.SLIGHT_LEFT
        TurnKind.SLIGHT_RIGHT -> TurnDirection.SLIGHT_RIGHT
        TurnKind.SHARP_LEFT -> TurnDirection.SHARP_LEFT
        TurnKind.SHARP_RIGHT -> TurnDirection.SHARP_RIGHT
        TurnKind.UTURN -> TurnDirection.UTURN
    }

    private fun startRecording(sportHint: String?, synthetic: Boolean, userInitiated: Boolean) {
        if (controller != null) return
        WeatherHolder.update(null)
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) 0
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        acquireWakeLock()

        val repo = dev.toufik.trailmark.app.AppGraph.repository(this)
        val resume = runCatching { repo.loadResume() }.getOrNull()
        val sport = sportHint
            ?.let { runCatching { Sport.valueOf(it) }.getOrNull() }
            ?: Sport.RUN
        sportName = sport.name
        val prefs = getSharedPreferences("trailmark", MODE_PRIVATE)
        if (userInitiated) {
            prefs.edit().putInt(KEY_CRASHES, 0).putBoolean(KEY_INTERRUPTED, false).apply()
        } else {
            // A resumed run is visible in the UI as a normal one; the flag is only
            // used to tell the user why it restarted.
            prefs.edit().putBoolean(KEY_RESUMED, true).apply()
        }
        val source: FixSource = if (synthetic) {
            SyntheticFixSource()
        } else {
            AndroidLocationFixSource(this)
        }
        // A reboot resets elapsedRealtimeNanos; shift the new fixes above the
        // checkpoint so the resumed activity's timeline never goes backwards.
        val nowRealtime = SystemClock.elapsedRealtimeNanos()
        val offset = resume
            ?.takeIf { nowRealtime < it.lastStored.timeNanos }
            ?.let { it.lastStored.timeNanos - nowRealtime + 1_000_000_000L }
            ?: 0L
        val c = RecordingController(
            repo = repo,
            source = OffsetFixSource(source, offset),
            scope = scope,
            sport = sport,
        )
        controller = c
        navChunksRef = PlanRouteHolder.state.value
        planPoints = flattenChunks(navChunksRef)
        navCues = if (planPoints.size >= 2) turnCues(planPoints) else emptyList()
        planTotalM = if (planPoints.size >= 2) polylineLengthMeters(planPoints) else 0.0
        announcedCues.clear()
        offRouteSinceNanos = 0L
        lastOffRouteNanos = 0L
        joinedRoute = false
        RouteNavHolder.update(null)
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
                    publishLive(st)
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
                    tts?.let { TtsVoice.apply(it, this@RecordingService, currentLang()) }
                    ttsReady = true
                }
            }
            voiceJob = scope.launch {
                var lastKm = 0
                // Ghost call-outs: text is always on screen; this is only the voice.
                var ghostSpokenAt = 0L
                var ghostSpokenSign = 0
                var ghostSpokenM = 0
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
                        val gap = RecordingHolder.ghostDelta.value
                        if (gap != null && ttsReady &&
                            getSharedPreferences("trailmark", MODE_PRIVATE)
                                .getBoolean("ghost_voice", true)
                        ) {
                            val metres = kotlin.math.abs(gap)
                            val sign = if (gap >= 0) 1 else -1
                            val now = System.nanoTime()
                            val waited = now - ghostSpokenAt
                            val due = ghostSpokenAt == 0L ||
                                sign != ghostSpokenSign ||
                                waited > 60_000_000_000L ||
                                metres - ghostSpokenM >= 100
                            if (metres >= 30 && due) {
                                ghostSpokenAt = now
                                ghostSpokenSign = sign
                                ghostSpokenM = metres
                                val event = if (gap >= 0) {
                                    CoachEvent.GhostAhead(metres)
                                } else {
                                    CoachEvent.GhostBehind(metres)
                                }
                                tts?.speak(
                                    Coach.message(event, coachLocale(currentLang()), metres),
                                    TextToSpeech.QUEUE_ADD,
                                    null,
                                    "ghost-$sign-$metres",
                                )
                            }
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
                                    segmentStartedNanos[s.id] = st.elapsedNanos
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
                            for (s in segStarts) {
                                val startedAt = segmentStartedNanos[s.id] ?: continue
                                val nearEnd = dev.toufik.trailmark.core.geo.haversineMeters(
                                    la, lo, s.endLat, s.endLon,
                                ) < 30.0
                                if (!nearEnd) continue
                                segmentStartedNanos.remove(s.id)
                                val seconds =
                                    ((st.elapsedNanos - startedAt) / 1_000_000_000L).coerceAtLeast(0L)
                                // Efforts are stored when the recording stops, so the
                                // best on file is still the previous record here.
                                val bestSeconds = runCatching {
                                    dev.toufik.trailmark.app.AppGraph.repository(this@RecordingService)
                                        .bestEffortNanos(s.id)
                                }.getOrNull()?.let { it / 1_000_000_000L }
                                val event = if (bestSeconds != null && seconds < bestSeconds) {
                                    CoachEvent.SegmentRecord(s.name, seconds, bestSeconds - seconds)
                                } else {
                                    CoachEvent.SegmentFinish(s.name, seconds)
                                }
                                val line = Coach.message(event, coachLocale(currentLang()), s.name.hashCode())
                                if (ttsReady) {
                                    tts?.speak(line, TextToSpeech.QUEUE_ADD, null, "segment-finish-${s.id}")
                                }
                                if (event is CoachEvent.SegmentRecord) {
                                    notifyRecord(s.id, line)
                                    buzz(longArrayOf(0, 250, 100, 250, 100, 400))
                                } else {
                                    buzz(longArrayOf(0, 120, 80, 120, 80, 120))
                                }
                            }
                        }
                    }
                    delay(2_000L)
                }
            }
        }
        val voiceOn = getSharedPreferences("trailmark", MODE_PRIVATE)
            .getBoolean("voice_coach", true)
        navJob = scope.launch {
            while (isActive) {
                val st = c.state.value
                if (st.phase == Phase.RECORDING) evaluateNavigation(st, voiceOn)
                delay(2_000L)
            }
        }
        scope.launch { c.start() }
    }

    private fun evaluateNavigation(st: RecordingState, voiceOn: Boolean) {
        val chunks = PlanRouteHolder.state.value
        if (chunks !== navChunksRef) {
            navChunksRef = chunks
            planPoints = flattenChunks(chunks)
            navCues = if (planPoints.size >= 2) turnCues(planPoints) else emptyList()
            planTotalM = if (planPoints.size >= 2) polylineLengthMeters(planPoints) else 0.0
            announcedCues.clear()
            offRouteSinceNanos = 0L
            joinedRoute = false
        }
        if (planPoints.size < 2) {
            RouteNavHolder.update(null)
            return
        }
        val la = st.currentLat ?: return
        val lo = st.currentLon ?: return
        val match = matchToRoute(planPoints, la, lo) ?: return
        val nowNanos = System.nanoTime()
        if (match.offRouteM <= 40.0) joinedRoute = true
        val offRoute = match.offRouteM > 60.0
        if (offRoute) {
            if (offRouteSinceNanos == 0L) offRouteSinceNanos = nowNanos
            val offFor = nowNanos - offRouteSinceNanos
            val sinceAlert = nowNanos - lastOffRouteNanos
            if (voiceOn && joinedRoute &&
                offFor > 20_000_000_000L && sinceAlert > 120_000_000_000L
            ) {
                lastOffRouteNanos = nowNanos
                if (ttsReady) {
                    tts?.speak(
                        Coach.message(
                            CoachEvent.OffRoute,
                            coachLocale(currentLang()),
                            (nowNanos / 1_000_000_000L).toInt(),
                        ),
                        TextToSpeech.QUEUE_ADD,
                        null,
                        "off-route-$nowNanos",
                    )
                }
                buzz(longArrayOf(0, 200, 120, 200))
            }
        } else {
            offRouteSinceNanos = 0L
            if (voiceOn && joinedRoute) {
                val speed = st.currentSpeedMps ?: 0.0
                val aheadM = (speed * 12.0).coerceIn(45.0, 120.0)
                val idx = navCues.withIndex().firstOrNull { (i, cue) ->
                    val remaining = cue.distanceAlongM - match.distanceAlongM
                    i !in announcedCues && remaining in 5.0..aheadM
                }?.index ?: -1
                if (idx >= 0 && announcedCues.add(idx)) {
                    val cue = navCues[idx]
                    val remaining = cue.distanceAlongM - match.distanceAlongM
                    val metres = ((remaining / 10.0).toInt() * 10).coerceAtLeast(10)
                    if (ttsReady) {
                        tts?.speak(
                            Coach.message(
                                CoachEvent.Turn(coachDirection(cue.kind), metres),
                                coachLocale(currentLang()),
                                idx,
                            ),
                            TextToSpeech.QUEUE_ADD,
                            null,
                            "turn-$idx",
                        )
                    }
                    buzz(longArrayOf(0, 120, 90, 120))
                }
            }
        }
        val nextIdx = navCues.indexOfFirst { it.distanceAlongM - match.distanceAlongM > 0.0 }
        val next = navCues.getOrNull(nextIdx)
        RouteNavHolder.update(
            RouteNav(
                nextTurn = next?.kind,
                turnInM = next?.let {
                    (it.distanceAlongM - match.distanceAlongM).coerceAtLeast(0.0)
                } ?: 0.0,
                remainingM = (planTotalM - match.distanceAlongM).coerceAtLeast(0.0),
                offRoute = offRoute,
            ),
        )
    }

    private fun stopRecording() {
        val c = controller
        val repo = dev.toufik.trailmark.app.AppGraph.repository(this)
        scope.launch {
            c?.stop()
            val endState = c?.state?.value
            val finished = endState?.lastActivityId
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
            if (finished != null && endState != null) {
                getSharedPreferences("trailmark", MODE_PRIVATE).edit()
                    .putBoolean(
                        dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_ACTIVE,
                        false,
                    )
                    .putFloat("last_dist_m", endState.distanceM.toFloat())
                    .putLong("last_time_s", endState.elapsedNanos / 1_000_000_000L)
                    .putString("last_sport", sportName)
                    .apply()
                dev.toufik.trailmark.app.widget.TrailmarkWidget.refresh(this@RecordingService)
            }
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
            navJob?.cancel()
            navJob = null
            RouteNavHolder.update(null)
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
            segStarts = emptyList()
            alertedSegments.clear()
            segmentStartedNanos.clear()
            planPoints = emptyList()
            navCues = emptyList()
            planTotalM = 0.0
            navChunksRef = emptyList()
            announcedCues.clear()
            offRouteSinceNanos = 0L
            lastOffRouteNanos = 0L
            joinedRoute = false
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
        clearLive()
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

    /**
     * Mirrors the session into preferences so the home-screen widget can show a
     * live distance and time without binding to the service or reading the
     * database from a widget broadcast.
     */
    private fun publishLive(st: RecordingState) {
        getSharedPreferences("trailmark", MODE_PRIVATE).edit()
            .putBoolean(dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_ACTIVE, true)
            .putFloat(
                dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_DIST,
                st.distanceM.toFloat(),
            )
            .putLong(
                dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_TIME,
                st.elapsedNanos / 1_000_000_000L,
            )
            .putString(dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_SPORT, sportName)
            .apply()
        dev.toufik.trailmark.app.widget.TrailmarkWidget.refresh(this)
    }

    private fun clearLive() {
        getSharedPreferences("trailmark", MODE_PRIVATE).edit()
            .putBoolean(dev.toufik.trailmark.app.widget.TrailmarkWidget.KEY_LIVE_ACTIVE, false)
            .apply()
        dev.toufik.trailmark.app.widget.TrailmarkWidget.refresh(this)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val t = textsFor(currentLang())
        mgr.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            t.tabRecord,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    /** A heads-up notification for a new segment record, kept after the run ends. */
    private fun notifyRecord(segmentId: Long, line: String) {
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        RECORDS_CHANNEL_ID,
                        textsFor(currentLang()).segments,
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                )
            }
            val open = PendingIntent.getActivity(
                this,
                3,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(this, RECORDS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_track)
                .setContentTitle(textsFor(currentLang()).newRecord)
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            mgr.notify((RECORD_NOTIF_BASE + segmentId).toInt(), n)
        }
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
            val speed = live.currentSpeedMps?.let { mps ->
                val kmh = mps * 3.6
                String.format(java.util.Locale.US, "%.1f %s", kmh, t.kmh)
            }
            val detail = buildString {
                append(
                    "${dev.toufik.trailmark.app.formatDistance(live.distanceM)} " +
                        "${dev.toufik.trailmark.app.distanceUnit(t)} \u00b7 ",
                )
                append(dev.toufik.trailmark.app.formatDuration(live.elapsedNanos))
                if (speed != null) append(" \u00b7 $speed")
                if (live.autoPaused) append(" \u00b7 ${t.paused}")
            }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            if (live.phase == Phase.RECORDING) {
                builder.addAction(0, t.pause, actionIntent(ACTION_PAUSE, 1))
            } else {
                builder.addAction(0, t.resume, actionIntent(ACTION_RESUME, 2))
            }
            builder.addAction(0, t.stop, actionIntent(ACTION_STOP, 3))
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
        private const val CHANNEL_ID = "recording_v2"
        private const val LEGACY_CHANNEL_ID = "recording"
        private const val NOTIF_ID = 4711
        private const val RECORDS_CHANNEL_ID = "segment_records"
        private const val RECORD_NOTIF_BASE = 50_000L
        const val KEY_CRASHES = "recording_crashes"
        const val KEY_INTERRUPTED = "recording_interrupted"
        const val KEY_RESUMED = "recording_resumed"
    }
}
