package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** An address and where it is on the map. [address] is trimmed and lower-cased. */
@Entity(tableName = "places")
data class GeoPlace(
    @PrimaryKey val address: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(defaultValue = "") val label: String = "",          // what the service matched
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false     // true when you set it yourself
)