package dev.wolsey.windowtimer.data.store

import android.content.Context
import androidx.room.withTransaction
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.Run
import kotlinx.coroutines.flow.Flow

object RunStore {

    /** Every run, newest first. Updates by itself when runs change. */
    fun runs(context: Context): Flow<List<Run>> =
        AppDatabase.get(context).runDao().observeAll()

    private const val ATTACH_WINDOW_MS = 24 * 60 * 60 * 1000L

    /**
     * Adds a run, linking it to the job's latest recent visit when there is one.
     */
    suspend fun add(context: Context, run: Run) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            val visit = if (run.visitId == null && run.jobName.isNotBlank()) {
                db.visitDao().findAttachable(run.jobName.trim(), run.startedAt - ATTACH_WINDOW_MS)
            } else null

            db.runDao().insert(if (visit != null) run.copy(visitId = visit.id) else run)
        }
    }

    /** Adds a run from the past, linking it to the visit that was in progress at its start. */
    suspend fun addHistorical(context: Context, run: Run) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            val visit = if (run.jobName.isNotBlank()) {
                db.visitDao().findContaining(run.jobName.trim(), run.startedAt)
            } else null

            db.runDao().insert(if (visit != null) run.copy(visitId = visit.id) else run)
        }
    }

    /** Saves changes to a run. It's matched by its id, which edits don't change. */
    suspend fun update(context: Context, run: Run) {
        AppDatabase.get(context).runDao().update(run)
    }

    suspend fun delete(context: Context, run: Run) {
        AppDatabase.get(context).runDao().delete(run)
    }
}