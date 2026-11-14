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
    val counts: List<TypeCount> = emptyList()   // windows this person actually did
) {
    val interior: Int get() = counts.sumOf { it.interior }
    val exterior: Int get() = counts.sumOf { it.exterior }
    val windows: Int get() = interior + exterior
}

/** Reads and writes a list of [CrewMember] as the text stored in the run's `crew` column. */
object Crew {

    fun encode(members: List<CrewMember>): String {
        if (members.isEmpty()) return ""
        val array = JSONArray()
        members.forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.personId)
                    .put("name", m.name)
                    .put("ip", m.interiorPace)
                    .put("ep", m.exteriorPace)
                    .put("counts", JSONArray(Breakdown.encode(m.counts).ifEmpty { "[]" }))
            )
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
                    counts = Breakdown.decode(o.optJSONArray("counts")?.toString().orEmpty())
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    /**
     * Splits planned counts between the crew in proportion to their paces (interior and
     * exterior separately). Returns one list per person, in the same order as [crew], each
     * aligned with [planned]. The totals always add back up to the planned counts.
     */
    fun split(planned: List<TypeCount>, crew: List<Person>): List<List<TypeCount>> {
        if (crew.isEmpty()) return emptyList()
        val interior = planned.map { allocate(it.interior, crew.map { p -> p.interiorPace }) }
        val exterior = planned.map { allocate(it.exterior, crew.map { p -> p.exteriorPace }) }
        return crew.indices.map { p ->
            planned.indices.map { t ->
                planned[t].copy(interior = interior[t][p], exterior = exterior[t][p])
            }
        }
    }

    /** Shares [total] out by [weights], giving leftovers to the largest fractions. */
    private fun allocate(total: Int, weights: List<Int>): List<Int> {
        val sum = weights.sum().toDouble()
        if (total <= 0 || sum <= 0) return List(weights.size) { i -> if (i == 0) total else 0 }
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