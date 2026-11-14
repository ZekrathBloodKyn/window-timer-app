package dev.wolsey.windowtimer.data.store

import android.content.Context
import androidx.room.withTransaction
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.Job
import kotlinx.coroutines.flow.Flow

object JobStore {

    /** Every saved job, most recently saved first. Updates by itself when jobs change. */
    fun jobs(context: Context): Flow<List<Job>> =
        AppDatabase.get(context).jobDao().observeAll()

    /** Saves a job. A job with the same name (ignoring case) is replaced. */
    suspend fun save(context: Context, job: Job) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            val existing = db.jobDao().findByName(job.name)
            if (existing != null) {
                db.jobDao().update(job.copy(id = existing.id))
            } else {
                db.jobDao().insert(job)
            }
        }
    }

    /**
     * Replaces [original] with [updated], which may have a new name. If the name changed,
     * the job's recorded runs are moved to the new name in the same step.
     * Returns false, changing nothing, if the new name belongs to a different job.
     */
    suspend fun update(context: Context, original: Job, updated: Job): Boolean {
        val db = AppDatabase.get(context)
        return db.withTransaction {
            val clash = db.jobDao().findByName(updated.name)
            if (clash != null && clash.id != original.id) {
                return@withTransaction false
            }
            db.jobDao().update(updated.copy(id = original.id))
            if (original.name != updated.name) {
                db.runDao().renameJob(original.name, updated.name)
                db.visitDao().renameJob(original.name, updated.name)
            }
            true
        }
    }

    suspend fun delete(context: Context, job: Job) {
        AppDatabase.get(context).jobDao().deleteById(job.id)
    }
}