package dev.wolsey.windowtimer.data.store

import android.content.Context
import androidx.room.withTransaction
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.Visit
import kotlinx.coroutines.flow.Flow

object VisitStore {

    /** Every visit, most recent arrival first. Updates by itself when visits change. */
    fun visits(context: Context): Flow<List<Visit>> =
        AppDatabase.get(context).visitDao().observeAll()

    /** The latest visit to this job that's still open, or null. Updates by itself. */
    fun openVisit(context: Context, jobName: String): Flow<Visit?> =
        AppDatabase.get(context).visitDao().observeOpenVisit(jobName.trim())

    /** Adds a visit from the past, linking any unattached runs for that job that started during it. */
    suspend fun add(context: Context, visit: Visit) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            val clean = visit.copy(jobName = visit.jobName.trim())
            val id = db.visitDao().insert(clean)
            db.runDao().attachRunsInRange(clean.jobName, id, clean.arrivedAt, clean.departedAt)
        }
    }

    /** Records arriving at a job. */
    suspend fun markArrival(
        context: Context,
        jobName: String,
        at: Long = System.currentTimeMillis()
    ) {
        AppDatabase.get(context).visitDao().insert(
            Visit(
                jobName = jobName.trim(),
                arrivedAt = at
            )
        )
    }

    /** Records leaving a job that was previously marked as arrived. */
    suspend fun markDeparture(
        context: Context,
        visit: Visit,
        at: Long = System.currentTimeMillis()
    ) {
        AppDatabase.get(context).visitDao().update(visit.copy(departedAt = at))
    }

    /** Saves changes to a visit, such as corrected times. It's matched by its id. */
    suspend fun update(context: Context, visit: Visit) {
        AppDatabase.get(context).visitDao().update(visit)
    }

    /** Deletes a visit. Any runs that belonged to it are kept, just no longer linked. */
    suspend fun delete(context: Context, visit: Visit) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            db.runDao().detachFromVisit(visit.id)
            db.visitDao().delete(visit)
        }
    }
}