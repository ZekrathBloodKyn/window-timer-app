package dev.wolsey.windowtimer.timer

/** When a person is working, counted from the timer's start. Null end = stays to the end. */
data class Availability(val startMs: Long = 0L, val endMs: Long? = null)

/** Keeps the settings as one string so they survive rotation and leaving the screen. */
object AvailabilityCodec {

    fun encode(map: Map<Long, Availability>): String =
        map.filter { (_, a) -> a.startMs > 0 || a.endMs != null }
            .entries.joinToString(";") { (id, a) -> "$id:${a.startMs}:${a.endMs ?: ""}" }

    fun decode(text: String): Map<Long, Availability> =
        text.split(';').mapNotNull { part ->
            val fields = part.split(':')
            if (fields.size != 3) return@mapNotNull null
            val id = fields[0].toLongOrNull() ?: return@mapNotNull null
            id to Availability(fields[1].toLongOrNull() ?: 0L, fields[2].toLongOrNull())
        }.toMap()
}