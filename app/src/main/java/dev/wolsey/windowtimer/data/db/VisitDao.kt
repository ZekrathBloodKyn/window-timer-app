package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    /** Every visit, most recent arrival first. */
    @Query("SELECT * FROM visits ORDER BY arrivedAt DESC")
    fun observeAll(): Flow<List<Visit>>

    /** The latest visit to this job that hasn't had its departure marked, if any. */
    @Query(
        "SELECT * FROM visits WHERE jobName = :name COLLATE NOCASE " +
                "AND departedAt IS NULL ORDER BY arrivedAt DESC LIMIT 1"
    )
    fun observeOpenVisit(name: String): Flow<Visit?>

    /**
     * The visit a newly finished run should attach to: the newest visit to this job
     * since [since] that either has no run yet or is still open.
     */
    @Query(
        "SELECT * FROM visits WHERE jobName = :name COLLATE NOCASE " +
                "AND arrivedAt >= :since " +
                "AND (departedAt IS NULL OR id NOT IN " +
                "(SELECT visitId FROM runs WHERE visitId IS NOT NULL)) " +
                "ORDER BY arrivedAt DESC LIMIT 1"
    )
    suspend fun findAttachable(name: String, since: Long): Visit?

    /** The visit that was in progress at [time]: arrived by then and not yet departed. */
    @Query(
        "SELECT * FROM visits WHERE jobName = :name COLLATE NOCASE " +
                "AND arrivedAt <= :time AND (departedAt IS NULL OR departedAt >= :time) " +
                "ORDER BY arrivedAt DESC LIMIT 1"
    )
    suspend fun findContaining(name: String, time: Long): Visit?

    @Insert
    suspend fun insert(visit: Visit): Long

    @Update
    suspend fun update(visit: Visit)

    @Delete
    suspend fun delete(visit: Visit)

    /** Moves every visit of one job to a new name, ignoring case. */
    @Query("UPDATE visits SET jobName = :newName WHERE jobName = :oldName COLLATE NOCASE")
    suspend fun renameJob(oldName: String, newName: String)
}