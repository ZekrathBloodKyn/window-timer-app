package dev.wolsey.windowtimer.data

import dev.wolsey.windowtimer.data.db.WindowType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** How many interior and exterior windows of one type. The name is kept with the
 *  counts so history still reads correctly if the type is later renamed or deleted. */
data class TypeCount(
    val typeId: Long,
    val name: String,
    val interior: Int,
    val exterior: Int
) {
    val windows: Int get() = interior + exterior
}

/** Reads and writes a list of [TypeCount] as the text stored in the `breakdown` columns. */
object Breakdown {

    fun encode(counts: List<TypeCount>): String {
        if (counts.isEmpty()) return ""
        val array = JSONArray()
        counts.forEach {
            array.put(
                JSONObject()
                    .put("id", it.typeId)
                    .put("name", it.name)
                    .put("int", it.interior)
                    .put("ext", it.exterior)
            )
        }
        return array.toString()
    }

    fun decode(text: String): List<TypeCount> {
        if (text.isBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                TypeCount(o.getLong("id"), o.getString("name"), o.getInt("int"), o.getInt("ext"))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    /** "Standard 12 int / 10 ext · French 3 int", skipping types with no windows. */
    fun describe(counts: List<TypeCount>): String =
        counts.filter { it.windows > 0 }.joinToString(" · ") { c ->
            val parts = listOfNotNull(
                c.interior.takeIf { it > 0 }?.let { "$it int" },
                c.exterior.takeIf { it > 0 }?.let { "$it ext" }
            )
            "${c.name} ${parts.joinToString(" / ")}"
        }

    /**
     * The counts for a job or run. Rows saved before types existed have no breakdown,
     * so their totals count as all Standard windows.
     */
    fun resolve(text: String, interior: Int, exterior: Int, standard: WindowType?): List<TypeCount> {
        val decoded = decode(text)
        if (decoded.isNotEmpty()) return decoded
        if (interior + exterior == 0) return emptyList()
        return listOf(
            TypeCount(standard?.id ?: 0L, standard?.name ?: "Standard", interior, exterior)
        )
    }

    fun totalInterior(counts: List<TypeCount>) = counts.sumOf { it.interior }
    fun totalExterior(counts: List<TypeCount>) = counts.sumOf { it.exterior }
}