package dev.wolsey.windowtimer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoPlaceDao {
    @Query("SELECT * FROM places")
    fun observeAll(): Flow<List<GeoPlace>>

    @Query("SELECT * FROM places WHERE address = :address")
    suspend fun find(address: String): GeoPlace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(place: GeoPlace)

    @Query("DELETE FROM places WHERE address = :address")
    suspend fun delete(address: String)
}