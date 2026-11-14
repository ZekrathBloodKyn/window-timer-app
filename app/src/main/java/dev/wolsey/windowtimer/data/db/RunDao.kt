package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    /** Links runs that have no visit yet to [visitId] if they started within [from]..[to]. */
    @Query(
        "UPDATE runs SET visitId = :visitId WHERE visitId IS NULL " +
                "AND jobName = :name COLLATE NOCASE AND startedAt >= :from " +
                "AND (:to IS NULL OR startedAt <= :to)"
    )
    suspend fun attachRunsInRange(name: String, visitId: Long, from: Long, to: Long?)
    
    /** Every run, newest first. Emits again whenever the table changes. */
    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Run>>

    @Insert
    suspend fun insert(run: Run): Long

    @Insert
    suspend fun insertAll(runs: List<Run>)

    @Update
    suspend fun update(run: Run)

    @Delete
    suspend fun delete(run: Run)

    /** Detaches every run from a visit, used when the visit is deleted. */
    @Query("UPDATE runs SET visitId = NULL WHERE visitId = :visitId")
    suspend fun detachFromVisit(visitId: Long)

    /** Moves every run of one job to a new name, ignoring case. */
    @Query("UPDATE runs SET jobName = :newName WHERE jobName = :oldName COLLATE NOCASE")
    suspend fun renameJob(oldName: String, newName: String)
}