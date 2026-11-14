package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    /** Every job, most recently saved first. Emits again whenever the table changes. */
    @Query("SELECT * FROM jobs ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<Job>>

    /** The job with this name. The name column is case-insensitive, so case doesn't matter. */
    @Query("SELECT * FROM jobs WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Job?

    @Insert
    suspend fun insert(job: Job): Long

    /** Used by the one-time import; a name that already exists is skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<Job>)

    @Update
    suspend fun update(job: Job)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: Long)
}