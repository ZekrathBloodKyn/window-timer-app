package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/** A named group of people, e.g. "Me + Sam". Members are stored as a comma-separated list of ids. */
@Entity(tableName = "crews", indices = [Index(value = ["name"], unique = true)])
data class SavedCrew(
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val memberIds: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
) {
    @get:Ignore
    val ids: List<Long>
        get() = memberIds.split(',').mapNotNull { it.trim().toLongOrNull() }

    companion object {
        fun join(ids: List<Long>): String = ids.joinToString(",")
    }
}