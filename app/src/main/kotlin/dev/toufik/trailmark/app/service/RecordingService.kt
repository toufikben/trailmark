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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.toufik.trailmark.R
import dev.toufik.trailmark.app.MainActivity
import dev.toufik.trailmark.app.gps.AndroidLocationFixSource
import dev.toufik.trailmark.app.gps.SyntheticFixSource
import dev.toufik.trailmark.engine.FixSource
import dev.toufik.trailmark.engine.Sport
import dev.toufik.trailmark.recording.RecordingController
import dev.toufik.trailmark.recording.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service of type location: keeps GNSS alive and the CPU awake while
 * the screen is off. Owns the one [RecordingController] for the session.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controller: RecordingController? = null
    private var stateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (controller != null) return
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
        stateJob = scope.launch { c.state.collect { RecordingHolder.update(it) } }
        scope.launch { c.start() }
    }

    private fun stopRecording() {
        val c = controller
        scope.launch {
            c?.stop()
            RecordingHolder.update(RecordingState())
            stateJob?.cancel()
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

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تسجيل المسار", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_track)
            .setContentTitle("Trailmark")
            .setContentText("جارٍ تسجيل المسار")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val ACTION_START = "dev.toufik.trailmark.action.START"
        const val ACTION_STOP = "dev.toufik.trailmark.action.STOP"
        const val EXTRA_SPORT = "sport"
        const val EXTRA_SYNTHETIC = "synthetic"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 4711
    }
}
