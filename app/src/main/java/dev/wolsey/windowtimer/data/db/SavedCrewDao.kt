package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCrewDao {
    @Query("SELECT * FROM crews ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedCrew>>

    @Insert
    suspend fun insert(crew: SavedCrew): Long

    @Update
    suspend fun update(crew: SavedCrew)

    @Query("DELETE FROM crews WHERE id = :id")
    suspend fun deleteById(id: Long)
}