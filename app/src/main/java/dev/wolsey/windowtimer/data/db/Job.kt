package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved job: a name, its window counts, and an optional address. */
@Entity(
    tableName = "jobs",
    indices = [Index(value = ["name"], unique = true)]
)
data class Job(
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val interior: Int,
    val exterior: Int,
    val savedAt: Long,
    val address: String = "",
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)