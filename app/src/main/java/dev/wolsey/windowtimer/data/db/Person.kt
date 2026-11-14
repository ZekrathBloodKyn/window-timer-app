package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Someone who works jobs. The paces are windows per hour on Standard windows;
 * other window types are scaled from these by their pace relative to Standard.
 */
@Entity(tableName = "people", indices = [Index(value = ["name"], unique = true)])
data class Person(
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val interiorPace: Int,
    val exteriorPace: Int,
    val isOwner: Boolean = false,   // "Me": created automatically, can't be deleted
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val typePaces: String = ""
)