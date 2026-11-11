package dev.toufik.trailmark.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.toufik.trailmark.app.AppGraph
import dev.toufik.trailmark.app.Lang
import dev.toufik.trailmark.app.MainActivity
import dev.toufik.trailmark.app.MediaCapture
import dev.toufik.trailmark.app.formatDate
import dev.toufik.trailmark.app.sportLabelOf
import dev.toufik.trailmark.app.textsFor
import dev.toufik.trailmark.app.export.ExportEntry
import dev.toufik.trailmark.app.export.ExportHolder
import dev.toufik.trailmark.app.export.ExportJob
import dev.toufik.trailmark.app.export.ExportLog
import dev.toufik.trailmark.app.export.ExportPhase
import dev.toufik.trailmark.app.export.ExportState
import dev.toufik.trailmark.app.export.ExportStore
import dev.toufik.trailmark.app.export.HeadlessFlyover
import dev.toufik.trailmark.app.ui.FlyoverStats
import dev.toufik.trailmark.analytics.analyze
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders a flyover clip while the user is elsewhere.
 *
 * A foreground service of type `mediaProcessing` is the only place a multi
 * minute render may live: the activity can be closed, the screen can lock, and
 * the job still finishes and posts its result. The job itself is on disk, so a
 * process death resumes the export instead of losing it.
 */
class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val job = intent.getStringExtra(EXTRA_JOB)?.let(ExportJob::fromJson)
                if (job == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ExportStore.savePending(this, job)
                ExportStore.resetCrashes(this)
                begin(job)
            }
            ACTION_CANCEL -> {
                running?.cancel()
                ExportStore.clearPending(this)
                ExportHolder.publish(null)
                stopSelf()
            }
            // No action: Android restarted us after killing the process. The job
            // is on disk, so pick it up again — unless it has crashed the app
            // three times in a row, in which case it is not going to work.
            else -> resume()
        }
        return START_STICKY
    }

    private fun resume() {
        val job = ExportStore.pending(this)
        if (job == null) {
            stopSelf()
            return
        }
        if (ExportStore.crashes(this) >= 3) {
            ExportStore.clearPending(this)
            val t = textsFor(lang())
            ExportHolder.publish(
                ExportState(ExportPhase.FAILED, job.activityId, job.title, error = t.exportFailed),
            )
            stopSelf()
            return
        }
        begin(job)
    }

    private fun begin(job: ExportJob) {
        running?.cancel()
        val t = textsFor(lang())
        createChannels(t.exportRunning)
        if (!startAsForeground(NOTIF_ID, progressNotification(job, 0f, null))) {
            // The platform refused every declared type. Rendering without a
            // foreground service would be killed as soon as the screen locks,
            // so report the failure instead of crashing the app.
            ExportStore.clearPending(this)
            ExportHolder.publish(
                ExportState(ExportPhase.FAILED, job.activityId, job.title, error = t.exportFailed),
            )
            stopSelf()
            return
        }
        val wake = acquireWakeLock()
        running = scope.launch {
            ExportHolder.publish(
                ExportState(ExportPhase.RENDERING, job.activityId, job.title, 0f),
            )
            ExportStore.pruneOutputs(this@ExportService)
            val out = ExportStore.outputFile(this@ExportService, job.activityId)
            try {
                val repo = AppGraph.repository(this@ExportService)
                val points = withContext(Dispatchers.IO) { repo.loadPoints(job.activityId) }
                val media = withContext(Dispatchers.IO) { repo.listMedia(job.activityId) }
                val row = withContext(Dispatchers.IO) { repo.activityById(job.activityId) }
                    ?: error("activity gone")
                val stats = withContext(Dispatchers.Default) {
                    val analysis = analyze(points, row.elapsedNanos)
                    FlyoverStats(
                        distanceM = row.distanceM,
                        movingNanos = row.elapsedNanos,
                        climbM = analysis.elevationGainM,
                        title = job.title.ifBlank { sportLabelOf(t, sportOf(job.sport)) },
                        subtitle = job.subtitle.ifBlank {
                            formatDate(row.startWallMillis, lang())
                        },
                    )
                }
                var lastShown = -1
                // The render is minutes of bitmap work; it must not run on the
                // main thread or the whole app freezes while it draws.
                withContext(Dispatchers.Default) {
                    HeadlessFlyover(this@ExportService, job, out).render(points, media, stats) { d, total, eta ->
                        val progress = if (total > 0) d.toFloat() / total else 0f
                        val percent = (progress * 100).toInt()
                        ExportHolder.publish(
                            ExportState(
                                ExportPhase.RENDERING,
                                job.activityId,
                                job.title,
                                progress,
                                eta,
                            ),
                        )
                        if (percent != lastShown) {
                            lastShown = percent
                            notify(NOTIF_ID, progressNotification(job, progress, eta))
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    ExportHolder.publish(
                        ExportState(ExportPhase.SAVING, job.activityId, job.title, 1f),
                    )
                    notify(NOTIF_ID, progressNotification(job, 1f, 0L))
                }
                val bytes = out.length()
                val saved = if (job.galleryCopy) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        MediaCapture.saveVideoToGallery(this@ExportService, out)
                    }
                } else {
                    null
                }
                // No gallery copy (turned off, or a locked-down storage
                // provider): keep the file in app storage and share it from there.
                val shared: Uri = saved ?: MediaCapture.uriFor(this@ExportService, out)
                if (saved != null) out.delete()
                withContext(NonCancellable + Dispatchers.IO) {
                    ExportLog.add(
                        this@ExportService,
                        ExportEntry(
                            activityId = job.activityId,
                            title = job.title,
                            wallMillis = System.currentTimeMillis(),
                            seconds = job.seconds,
                            camera = job.camera.name,
                            media = job.media.name,
                            uri = shared.toString(),
                            bytes = bytes,
                        ),
                    )
                }
                ExportStore.clearPending(this@ExportService)
                ExportHolder.publish(
                    ExportState(
                        phase = ExportPhase.DONE,
                        activityId = job.activityId,
                        title = job.title,
                        progress = 1f,
                        uri = shared,
                        inGallery = saved != null,
                    ),
                )
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    notifyDone(job, saved != null)
                }
            } catch (_: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { out.delete() }
                ExportHolder.publish(null)
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                }
            } catch (e: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) { out.delete() }
                ExportStore.clearPending(this@ExportService)
                // The message here is a codec or snapshotter detail that says
                // nothing to the user, so it goes to the log and the screen
                // shows one localized sentence instead.
                android.util.Log.w("TrailmarkExport", "export failed for ${job.activityId}", e)
                ExportHolder.publish(
                    ExportState(
                        ExportPhase.FAILED,
                        job.activityId,
                        job.title,
                        error = t.exportFailed,
                    ),
                )
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    notifyFailed(job)
                }
            } finally {
                withContext(Dispatchers.Main) { stopSelf() }
                runCatching { wake.release() }
            }
        }
    }

    /**
     * A render takes minutes with the screen off. Without a partial wake lock the
     * CPU goes back to sleep between frames and a 30 s clip takes an hour.
     */
    private fun acquireWakeLock(): android.os.PowerManager.WakeLock {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Trailmark:export").apply {
            setReferenceCounted(false)
            acquire(MAX_EXPORT_MS)
        }
    }

    private fun sportOf(name: String) =
        dev.toufik.trailmark.engine.Sport.entries.firstOrNull { it.name == name }
            ?: dev.toufik.trailmark.engine.Sport.RUN

    private fun lang(): Lang {
        val code = getSharedPreferences("trailmark", MODE_PRIVATE).getString("lang_code", null)
        return Lang.entries.firstOrNull { it.code == code } ?: Lang.AR
    }

    // ---------------------------------------------------------- notifications

    private fun createChannels(title: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                DONE_CHANNEL_ID,
                textsFor(lang()).exportDone,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun progressNotification(job: ExportJob, progress: Float, etaNanos: Long?): Notification {
        val t = textsFor(lang())
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "detail:${job.activityId}"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            11,
            Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val text = when {
            etaNanos != null && etaNanos > 0 ->
                "$percent% · ${dev.toufik.trailmark.app.formatDuration(etaNanos)}"
            else -> "$percent%"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(dev.toufik.trailmark.R.drawable.ic_stat_track)
            .setContentTitle(job.title.ifBlank { "Trailmark" })
            .setContentText("${t.exportRunning}  $text")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, t.cancel, cancel)
            .build()
    }

    private fun notifyDone(job: ExportJob, inGallery: Boolean) {
        val t = textsFor(lang())
        val open = PendingIntent.getActivity(
            this,
            12,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "detail:${job.activityId}"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, DONE_CHANNEL_ID)
            .setSmallIcon(dev.toufik.trailmark.R.drawable.ic_stat_track)
            .setContentTitle(job.title.ifBlank { "Trailmark" })
            .setContentText(if (inGallery) t.exportDone else t.exportReady)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(DONE_ID, n)
    }

    private fun notifyFailed(job: ExportJob) {
        val t = textsFor(lang())
        val open = PendingIntent.getActivity(
            this,
            13,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "detail:${job.activityId}"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, DONE_CHANNEL_ID)
            .setSmallIcon(dev.toufik.trailmark.R.drawable.ic_stat_track)
            .setContentTitle(t.exportFailed)
            .setContentText(job.title)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(DONE_ID, n)
    }

    private fun notify(id: Int, n: Notification) {
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(id, n)
        }
    }

    private fun startAsForeground(id: Int, n: Notification): Boolean {
        // mediaProcessing exists from Android 15, but some ROMs refuse it even
        // when it is declared; dataSync is declared next to it as a fallback so
        // a long render still finishes instead of crashing the app.
        val sdk = Build.VERSION.SDK_INT
        val declared = runCatching {
            packageManager.getServiceInfo(
                android.content.ComponentName(this, ExportService::class.java),
                0,
            ).foregroundServiceType
        }.getOrDefault(0)
        val candidates = buildList {
            if (sdk >= 35) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            add(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        for (type in candidates) {
            val error = runCatching { ServiceCompat.startForeground(this, id, n, type) }.exceptionOrNull()
            android.util.Log.i(
                "TrailmarkExport",
                "startForeground sdk=$sdk type=0x${type.toString(16)} declared=0x${declared.toString(16)} ok=${error == null}",
                error,
            )
            if (error == null) return true
        }
        return false
    }

    companion object {
        const val ACTION_START = "dev.toufik.trailmark.action.EXPORT_START"
        const val ACTION_CANCEL = "dev.toufik.trailmark.action.EXPORT_CANCEL"
        const val EXTRA_JOB = "export_job"

        private const val CHANNEL_ID = "export_v1"
        private const val DONE_CHANNEL_ID = "export_done_v1"
        private const val NOTIF_ID = 4720
        private const val DONE_ID = 4721

        /** A flyover render is minutes, never hours; the lock is a safety net. */
        private const val MAX_EXPORT_MS = 60L * 60L * 1000L

        /** Starts (or restarts) one export; a newer request replaces the running one. */
        fun start(context: Context, job: ExportJob) {
            val intent = Intent(context, ExportService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_JOB, job.toJson())
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ExportService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}
