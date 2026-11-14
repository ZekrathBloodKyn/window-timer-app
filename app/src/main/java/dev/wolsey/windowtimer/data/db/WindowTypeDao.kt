package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WindowTypeDao {
    @Query("SELECT * FROM window_types ORDER BY isStandard DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<WindowType>>

    @Query("SELECT * FROM window_types ORDER BY isStandard DESC, name COLLATE NOCASE")
    suspend fun getAll(): List<WindowType>

    @Query("SELECT * FROM window_types WHERE isStandard = 1 LIMIT 1")
    suspend fun findStandard(): WindowType?

    @Insert
    suspend fun insert(type: WindowType): Long

    @Update
    suspend fun update(type: WindowType)

    /** The Standard type can never be deleted. */
    @Query("DELETE FROM window_types WHERE id = :id AND isStandard = 0")
    suspend fun deleteById(id: Long)
}