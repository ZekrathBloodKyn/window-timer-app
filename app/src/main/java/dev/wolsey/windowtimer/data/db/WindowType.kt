package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A kind of window with its own pace, e.g. Standard, French, Construction. */
@Entity(tableName = "window_types", indices = [Index(value = ["name"], unique = true)])
data class WindowType(
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val interiorPace: Int,          // windows per hour
    val exteriorPace: Int,          // windows per hour
    val isStandard: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
) {
    companion object {
        const val MIN_PACE = 5
        const val MAX_PACE = 60
    }
}