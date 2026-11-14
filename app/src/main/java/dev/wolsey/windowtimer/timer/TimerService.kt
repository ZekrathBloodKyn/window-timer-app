package dev.wolsey.windowtimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dev.wolsey.windowtimer.MainActivity
import dev.wolsey.windowtimer.alerts.AlertSettings
import dev.wolsey.windowtimer.alerts.Alerts
import dev.wolsey.windowtimer.data.db.Run
import dev.wolsey.windowtimer.data.store.RunStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerService : Service() {

    companion object {
        const val ACTION_START = "dev.wolsey.windowtimer.START"
        const val ACTION_FINISH = "dev.wolsey.windowtimer.FINISH"
        const val ACTION_CANCEL = "dev.wolsey.windowtimer.CANCEL"
        const val ACTION_PAUSE = "dev.wolsey.windowtimer.PAUSE"
        const val ACTION_RESUME = "dev.wolsey.windowtimer.RESUME"
        const val EXTRA_TOTAL_MS = "total_ms"
        const val EXTRA_JOB_NAME = "job_name"
        const val EXTRA_INTERIOR = "interior"
        const val EXTRA_EXTERIOR = "exterior"
        const val EXTRA_ACTUAL_MS = "actual_ms"
        const val EXTRA_DONE_INTERIOR = "done_interior"
        const val EXTRA_DONE_EXTERIOR = "done_exterior"
        const val EXTRA_BREAKDOWN = "breakdown"
        const val EXTRA_DONE_BREAKDOWN = "done_breakdown"
        const val EXTRA_DONE_CREW = "done_crew"

        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1
        private const val MAX_RUN_MS = 6 * 3_600_000L        // wake lock safety limit
        private const val MIN_RECORD_MS = 5_000L              // ignore runs shorter than this
        private const val OVERTIME_UPDATE_MS = 30_000L

        // Read by the UI to draw the countdown and checklist.
        @Volatile var running = false
        @Volatile var startRealtime = 0L
        @Volatile var totalMs = 0L
        @Volatile var firedCount = 0
        @Volatile var paused = false
        @Volatile private var pausedElapsedMs = 0L

        fun elapsedMs(): Long = when {
            !running -> 0L
            paused -> pausedElapsedMs
            else -> SystemClock.elapsedRealtime() - startRealtime
        }

        /** Clears the checklist marks; ignored while a run is in progress. */
        fun resetFired() {
            if (!running) firedCount = 0
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var finishing = false   // true while a Finish is being saved
    private var wakeLock: PowerManager.WakeLock? = null

    // Details of the current run, saved to history on Finish.
    private var jobName = ""
    private var plannedBreakdown = ""
    private var interiorCount = 0
    private var exteriorCount = 0
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Window timer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!running) plannedBreakdown = intent.getStringExtra(EXTRA_BREAKDOWN).orEmpty()
                start(
                    total = intent.getLongExtra(EXTRA_TOTAL_MS, 0L),
                    name = intent.getStringExtra(EXTRA_JOB_NAME).orEmpty(),
                    interior = intent.getIntExtra(EXTRA_INTERIOR, 0),
                    exterior = intent.getIntExtra(EXTRA_EXTERIOR, 0)
                )
            }
            ACTION_FINISH -> finish(intent)
            ACTION_CANCEL -> stop()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()

        }
        return START_NOT_STICKY
    }

    private fun start(total: Long, name: String, interior: Int, exterior: Int) {
        if (total <= 0 || running) return

        handler.removeCallbacksAndMessages(null)
        totalMs = total
        jobName = name
        interiorCount = interior
        exteriorCount = exterior
        startedAt = System.currentTimeMillis()
        startRealtime = SystemClock.elapsedRealtime()
        firedCount = 0
        running = true
        paused = false
        finishing = false

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "windowtimer:run")
            .apply { acquire(total + MAX_RUN_MS) }

        startForeground(NOTIFICATION_ID, buildNotification("Timer running"))

        QUARTERS.forEachIndexed { index, fraction ->
            handler.postDelayed({ fire(index) }, markMillis(total, fraction))
        }
    }

    private fun pause() {
        if (!running || paused) return
        pausedElapsedMs = elapsedMs()
        paused = true
        Alerts.stop()
        // Drop the pending quarter alerts and the overtime tick; resume() reschedules them.
        handler.removeCallbacksAndMessages(null)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("Paused"))
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun resume() {
        if (!running || !paused) return
        val elapsed = pausedElapsedMs
        // Shift the start so elapsedMs() carries on from where it stopped.
        startRealtime = SystemClock.elapsedRealtime() - elapsed
        paused = false

        // Keep the wake lock good for the rest of the run.
        wakeLock?.let { if (it.isHeld) it.release() }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "windowtimer:run")
            .apply { acquire(totalMs + MAX_RUN_MS) }

        // Reschedule the quarters that haven't fired yet.
        for (index in firedCount until QUARTERS.size) {
            val delay = (markMillis(totalMs, QUARTERS[index]) - elapsed).coerceAtLeast(0L)
            handler.postDelayed({ fire(index) }, delay)
        }
        // Already past 100%: restart the overtime notification updates.
        if (firedCount >= QUARTERS.size) handler.postDelayed(overtimeTick, OVERTIME_UPDATE_MS)

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("Timer running"))
    }

    private val overtimeTick = object : Runnable {
        override fun run() {
            if (!running) return
            val over = elapsedMs() - totalMs
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification("Over estimate by ${formatClock(over)}"))
            handler.postDelayed(this, OVERTIME_UPDATE_MS)
        }
    }

    private fun fire(index: Int) {
        if (!running) return
        firedCount = index + 1

        // Read settings now, so changes made during a run take effect.
        val config = AlertSettings.load(this)[index]
        if (config.enabled) Alerts.play(this, index, config)

        val pct = (QUARTERS[index] * 100).toInt()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("$pct% of estimated time"))

        // The timer keeps running after 100% as overtime, until Finish or Cancel.
        if (index == QUARTERS.lastIndex) {
            handler.postDelayed(overtimeTick, OVERTIME_UPDATE_MS)
        }
    }

    /** Saves the run to history, then shuts down. */
    private fun finish(intent: Intent) {
        if (!running || finishing) return
        finishing = true

        // Use the time captured when Finish was tapped, so time spent in the dialogs doesn't count.
        val actual = intent.getLongExtra(EXTRA_ACTUAL_MS, -1L)
            .takeIf { it >= 0 } ?: elapsedMs()
        // Windows actually done; defaults to the planned counts if the UI didn't say.
        val doneInterior = intent.getIntExtra(EXTRA_DONE_INTERIOR, interiorCount)
        val doneExterior = intent.getIntExtra(EXTRA_DONE_EXTERIOR, exteriorCount)

        val run = if (actual >= MIN_RECORD_MS) {
            Run(
                jobName = jobName,
                startedAt = startedAt,
                plannedMs = totalMs,
                actualMs = actual,
                interior = doneInterior,
                exterior = doneExterior,
                plannedInterior = interiorCount,
                plannedExterior = exteriorCount,
                breakdown = intent.getStringExtra(EXTRA_DONE_BREAKDOWN).orEmpty(),
                plannedBreakdown = plannedBreakdown,
                crew = intent.getStringExtra(EXTRA_DONE_CREW).orEmpty(),
            )
        } else null

        scope.launch {
            try {
                run?.let { RunStore.add(applicationContext, it) }
            } catch (e: Exception) {
                Log.e("TimerService", "Couldn't save the run", e)
            }
            // Only shut down once the save is done.
            withContext(Dispatchers.Main) { stop() }
        }
    }

    /** Shuts down without recording anything. */
    private fun stop() {
        Alerts.stop()
        handler.removeCallbacksAndMessages(null)
        running = false
        paused = false
        finishing = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        wakeLock?.let { if (it.isHeld) it.release() }
        running = false
        paused = false
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Window Timer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }
}