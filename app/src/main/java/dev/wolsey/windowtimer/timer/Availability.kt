package dev.wolsey.windowtimer.timer

/** Stands in for "hasn't arrived yet": far enough ahead that the job is done before then. */
const val NOT_HERE_MS = 100L * 3_600_000L

/**
 * When a person is working. [startMs] and [endMs] count from the timer's start. Before a run
 * begins, times are picked as clock times in [startAt] and [endAt] instead, because the start
 * isn't known yet; [resolve] turns them into offsets. [joining] means they'll turn up later at
 * a time nobody knows yet, so they're left out until an arrival is recorded.
 */
data class Availability(
    val startMs: Long = 0L,
    val endMs: Long? = null,        // null = stays to the end
    val startAt: Long? = null,      // picked clock time (epoch millis), before the run starts
    val endAt: Long? = null,
    val joining: Boolean = false
) {
    /** True while they haven't arrived and nobody knows when they will. */
    val notHere: Boolean get() = joining || startMs >= NOT_HERE_MS

    /** Offsets from the timer's start, as if the timer started at [now]. */
    fun resolve(now: Long): Availability = Availability(
        startMs = when {
            joining -> NOT_HERE_MS
            startAt != null -> (startAt - now).coerceAtLeast(0L)
            else -> startMs
        },
        endMs = if (endAt != null) (endAt - now).takeIf { it > 0L } else endMs
    )
}

/** Keeps the settings as one string so they survive rotation and leaving the screen. */
object AvailabilityCodec {

    fun encode(map: Map<Long, Availability>): String =
        map.filter { (_, a) ->
            a.startMs > 0 || a.endMs != null || a.startAt != null || a.endAt != null || a.joining
        }.entries.joinToString(";") { (id, a) ->
            "$id:${a.startMs}:${a.endMs ?: ""}:${a.startAt ?: ""}:${a.endAt ?: ""}:${if (a.joining) 1 else 0}"
        }

    fun decode(text: String): Map<Long, Availability> =
        text.split(';').mapNotNull { part ->
            val fields = part.split(':')
            if (fields.size < 3) return@mapNotNull null
            val id = fields[0].toLongOrNull() ?: return@mapNotNull null
            id to Availability(
                startMs = fields[1].toLongOrNull() ?: 0L,
                endMs = fields[2].toLongOrNull(),
                startAt = fields.getOrNull(3)?.toLongOrNull(),
                endAt = fields.getOrNull(4)?.toLongOrNull(),
                joining = fields.getOrNull(5) == "1"
            )
        }.toMap()
}