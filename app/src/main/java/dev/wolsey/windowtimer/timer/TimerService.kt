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
        const val ACTION_RETIME = "dev.wolsey.windowtimer.RETIME"
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
        const val EXTRA_INTERIOR_MS = "interior_ms"
        const val EXTRA_STOPWATCH = "stopwatch"

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
        @Volatile var marks: List<Mark> = emptyList()
        @Volatile var stopwatch = false

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
    private var stopwatchEveryMs = 0L   // 0 = no repeating alert

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
                    interiorMs = intent.getLongExtra(EXTRA_INTERIOR_MS, 0L),
                    name = intent.getStringExtra(EXTRA_JOB_NAME).orEmpty(),
                    interior = intent.getIntExtra(EXTRA_INTERIOR, 0),
                    exterior = intent.getIntExtra(EXTRA_EXTERIOR, 0),
                    stopwatchMode = intent.getBooleanExtra(EXTRA_STOPWATCH, false)
                )
            }
            ACTION_FINISH -> finish(intent)
            ACTION_CANCEL -> stop()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_RETIME -> retime(
                intent.getLongExtra(EXTRA_TOTAL_MS, 0L),
                intent.getLongExtra(EXTRA_INTERIOR_MS, 0L)
            )

        }
        return START_NOT_STICKY
    }

    private fun start(
        total: Long, interiorMs: Long, name: String, interior: Int, exterior: Int,
        stopwatchMode: Boolean
    ) {
        if (running || (total <= 0 && !stopwatchMode)) return

        handler.removeCallbacksAndMessages(null)
        stopwatch = stopwatchMode
        totalMs = if (stopwatchMode) 0L else total
        // Fixed for the whole run, so changing settings mid-run can't shift the alerts.
        val plan = IntervalSettings.load(this)
        marks = if (stopwatchMode) emptyList() else buildMarks(total, interiorMs, plan)
        stopwatchEveryMs =
            if (stopwatchMode && plan.stopwatchAlerts) plan.stopwatchMinutes * 60_000L else 0L
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
            .apply { acquire(totalMs + MAX_RUN_MS) }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(if (stopwatchMode) "Stopwatch running" else "Timer running")
        )

        marks.forEachIndexed { index, mark ->
            handler.postDelayed({ fire(index) }, mark.timeMs)
        }

        if (stopwatchMode) {
            handler.postDelayed(stopwatchTick, OVERTIME_UPDATE_MS)
            scheduleStopwatchAlert(0L)
        }    }

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

        scheduleRemaining(elapsed)
    }

    /** Schedules everything still to come, given how much time has elapsed. */
    private fun scheduleRemaining(elapsed: Long) {
        // Reschedule the alerts that haven't fired yet.
        for (index in firedCount until marks.size) {
            val delay = (marks[index].timeMs - elapsed).coerceAtLeast(0L)
            handler.postDelayed({ fire(index) }, delay)
        }
        // Already past the last alert: restart the overtime notification updates.
        if (!stopwatch && firedCount >= marks.size) handler.postDelayed(overtimeTick, OVERTIME_UPDATE_MS)
        if (stopwatch) {
            handler.postDelayed(stopwatchTick, OVERTIME_UPDATE_MS)
            scheduleStopwatchAlert(elapsed)
        }
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                buildNotification(if (stopwatch) "Stopwatch running" else "Timer running")
            )
    }

    /**
     * Re-estimates mid-run, for example when someone arrives or leaves. Marks that the new
     * estimate puts in the past are counted as done without sounding, so nothing bursts out.
     */
    private fun retime(total: Long, interior: Long) {
        if (!running || stopwatch || total <= 0 || total == totalMs) return
        val elapsed = elapsedMs()

        totalMs = total
        // The same call start() makes to build the marks:
        marks = buildMarks(total, interior, IntervalSettings.load(this))
        firedCount = marks.count { it.timeMs <= elapsed }

        if (!paused) {
            handler.removeCallbacksAndMessages(null)
            scheduleRemaining(elapsed)
        }
        // While paused, resume() schedules from the new marks.
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

    private val stopwatchTick = object : Runnable {
        override fun run() {
            if (!running) return
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification("Stopwatch ${formatClock(elapsedMs())}"))
            handler.postDelayed(this, OVERTIME_UPDATE_MS)
        }
    }

    /** Schedules the next repeating alert after [elapsed], if they're turned on. */
    private fun scheduleStopwatchAlert(elapsed: Long) {
        val every = stopwatchEveryMs
        if (every <= 0) return
        val next = nextStopwatchAlert(elapsed, every)
        handler.postDelayed({ fireStopwatchAlert(next) }, next - elapsed)
    }

    private fun fireStopwatchAlert(atMs: Long) {
        if (!running || paused) return

        // Read settings now, so changes made during a run take effect.
        val config = AlertSettings.load(this)[AlertSettings.STOPWATCH_SOUND]
        if (config.enabled) Alerts.play(this, AlertSettings.STOPWATCH_SOUND, config)

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("Stopwatch ${formatClock(atMs)}"))

        // If this fired a hair late, still aim for the next multiple, never the same one.
        scheduleStopwatchAlert(maxOf(elapsedMs(), atMs))
    }

    private fun fire(index: Int) {
        if (!running) return
        val mark = marks.getOrNull(index) ?: return
        firedCount = index + 1

        // Read settings now, so changes made during a run take effect.
        val config = AlertSettings.load(this)[mark.sound]
        if (config.enabled) Alerts.play(this, mark.sound, config)

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("${mark.label} of estimated time"))

        // The timer keeps running after the last alert as overtime, until Finish or Cancel.
        if (index == marks.lastIndex) {
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

        // A stopwatch run with no windows entered up front has nothing "planned", so treat
        // what was done as the plan. That keeps it from showing as a partial job.
        val noPlan = stopwatch && interiorCount + exteriorCount == 0
        val doneBreakdown = intent.getStringExtra(EXTRA_DONE_BREAKDOWN).orEmpty()

        val run = if (actual >= MIN_RECORD_MS) {
            Run(
                jobName = jobName,
                startedAt = startedAt,
                plannedMs = totalMs,
                actualMs = actual,
                interior = doneInterior,
                exterior = doneExterior,
                plannedInterior = if (noPlan) doneInterior else interiorCount,
                plannedExterior = if (noPlan) doneExterior else exteriorCount,
                breakdown = doneBreakdown,
                plannedBreakdown = if (noPlan) doneBreakdown else plannedBreakdown,
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
        stopwatch = false
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