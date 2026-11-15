package dev.wolsey.windowtimer.data

import dev.wolsey.windowtimer.data.db.Person
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * One person's part in a run. The name and paces are saved with the run, so history
 * stays correct if the person is later renamed, retimed or deleted.
 */
data class CrewMember(
    val personId: Long,
    val name: String,
    val interiorPace: Int,
    val exteriorPace: Int,
    val counts: List<TypeCount> = emptyList(),   // windows this person actually did
    val startMs: Long = 0L,                      // when they started, from the run's start
    val endMs: Long? = null                      // when they left; null = stayed to the end
) {
    val interior: Int get() = counts.sumOf { it.interior }
    val exterior: Int get() = counts.sumOf { it.exterior }
    val windows: Int get() = interior + exterior

    /** How long they worked, given the run lasted [runMs]. */
    fun presentMs(runMs: Long): Long {
        val end = (endMs ?: runMs).coerceAtMost(runMs)
        return (end - startMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}

/** Reads and writes a list of [CrewMember] as the text stored in the run's `crew` column. */
object Crew {

    fun encode(members: List<CrewMember>): String {
        if (members.isEmpty()) return ""
        val array = JSONArray()
        members.forEach { m ->
            val o = JSONObject()
                .put("id", m.personId)
                .put("name", m.name)
                .put("ip", m.interiorPace)
                .put("ep", m.exteriorPace)
                .put("counts", JSONArray(Breakdown.encode(m.counts).ifEmpty { "[]" }))
            if (m.startMs > 0) o.put("s", m.startMs)
            m.endMs?.let { o.put("e", it) }
            array.put(o)
        }
        return array.toString()
    }

    fun decode(text: String): List<CrewMember> {
        if (text.isBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                CrewMember(
                    personId = o.getLong("id"),
                    name = o.getString("name"),
                    interiorPace = o.getInt("ip"),
                    exteriorPace = o.getInt("ep"),
                    counts = Breakdown.decode(o.optJSONArray("counts")?.toString().orEmpty()),
                    startMs = o.optLong("s", 0L),
                    endMs = if (o.has("e")) o.getLong("e") else null
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    /**
     * Splits planned counts between the crew in proportion to their paces (interior and
     * exterior separately), and to how long each worked when [presentMs] is given. Returns one
     * list per person, in the same order as [crew]. The totals always add back up.
     */
    fun split(
        planned: List<TypeCount>,
        crew: List<Person>,
        presentMs: Map<Long, Long> = emptyMap()
    ): List<List<TypeCount>> {
        if (crew.isEmpty()) return emptyList()
        fun time(p: Person) = presentMs[p.id]?.toDouble() ?: 1.0
        val interior = planned.map { allocate(it.interior, crew.map { p -> p.interiorPace * time(p) }) }
        val exterior = planned.map { allocate(it.exterior, crew.map { p -> p.exteriorPace * time(p) }) }
        return crew.indices.map { p ->
            planned.indices.map { t ->
                planned[t].copy(interior = interior[t][p], exterior = exterior[t][p])
            }
        }
    }

    /** Shares [total] out by [weights], giving leftovers to the largest fractions. */
    private fun allocate(total: Int, weights: List<Double>): List<Int> {
        val sum = weights.sum()
        if (total <= 0 || sum <= 0) return weights.mapIndexed { i, _ -> if (i == 0) total else 0 }
        val exact = weights.map { total * it / sum }
        val shares = exact.map { it.toInt() }.toMutableList()
        var leftover = total - shares.sum()
        exact.indices.sortedByDescending { exact[it] - shares[it] }.forEach {
            if (leftover > 0) { shares[it]++; leftover-- }
        }
        return shares
    }

    /** Everyone's counts added together, by window type. */
    fun totals(members: List<CrewMember>): List<TypeCount> =
        members.flatMap { it.counts }
            .groupBy { it.typeId }
            .map { (typeId, list) ->
                TypeCount(
                    typeId,
                    list.first().name,
                    list.sumOf { it.interior },
                    list.sumOf { it.exterior }
                )
            }
}