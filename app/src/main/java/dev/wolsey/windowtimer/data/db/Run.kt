package dev.wolsey.windowtimer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/** One finished timer run. */
@Entity(
    tableName = "runs",
    indices = [Index(value = ["startedAt"])]
)
data class Run(
    val jobName: String,
    val startedAt: Long,   // epoch millis
    val plannedMs: Long,   // the estimate when the run started
    val actualMs: Long,    // how long it really took
    val interior: Int,     // interior windows actually done
    val exterior: Int,     // exterior windows actually done
    val plannedInterior: Int = interior,
    val plannedExterior: Int = exterior,
    val visitId: Long? = null,
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
    @ColumnInfo(defaultValue = "") val plannedBreakdown: String = "",
    @ColumnInfo(defaultValue = "") val crew: String = "",
    @PrimaryKey(autoGenerate = true) val id: Long = 0
) {
    @get:Ignore
    val windows: Int get() = interior + exterior

    /** True when fewer windows were done than planned. */
    @get:Ignore
    val isPartial: Boolean
        get() = interior < plannedInterior || exterior < plannedExterior

    /** Actual windows per hour for this run, or null if it can't be worked out. */
    @get:Ignore
    val actualPace: Double?
        get() = if (actualMs > 0 && windows > 0) windows * 3_600_000.0 / actualMs else null
}