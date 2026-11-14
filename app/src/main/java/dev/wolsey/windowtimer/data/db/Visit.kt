package dev.wolsey.windowtimer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Time spent at a job: when you arrived and, once marked, when you left. */
@Entity(
    tableName = "visits",
    indices = [Index(value = ["arrivedAt"]), Index(value = ["jobName"])]
)
data class Visit(
    val jobName: String,
    val arrivedAt: Long,           // epoch millis
    val departedAt: Long? = null,  // null until departure is marked
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)