package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY isOwner DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM people ORDER BY isOwner DESC, name COLLATE NOCASE")
    suspend fun getAll(): List<Person>

    @Query("SELECT * FROM people WHERE isOwner = 1 LIMIT 1")
    suspend fun findOwner(): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    /** The owner can never be deleted. */
    @Query("DELETE FROM people WHERE id = :id AND isOwner = 0")
    suspend fun deleteById(id: Long)
}