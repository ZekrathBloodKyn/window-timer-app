package dev.wolsey.windowtimer.data

import dev.wolsey.windowtimer.data.db.Person
import dev.wolsey.windowtimer.data.db.WindowType
import org.json.JSONException
import org.json.JSONObject

/** A person's own pace for one window type. A null side means "not set, scale from Standard". */
data class TypePace(val interior: Int?, val exterior: Int?)

/** Reads and writes a person's per-type paces as the text stored in `Person.typePaces`. */
object TypePaces {

    fun encode(paces: Map<Long, TypePace>): String {
        val set = paces.filter { it.value.interior != null || it.value.exterior != null }
        if (set.isEmpty()) return ""
        val json = JSONObject()
        set.forEach { (typeId, p) ->
            json.put(typeId.toString(), JSONObject().apply {
                p.interior?.let { put("i", it) }
                p.exterior?.let { put("e", it) }
            })
        }
        return json.toString()
    }

    fun decode(text: String): Map<Long, TypePace> {
        if (text.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(text)
            json.keys().asSequence().mapNotNull { key ->
                val id = key.toLongOrNull() ?: return@mapNotNull null
                val o = json.getJSONObject(key)
                id to TypePace(
                    interior = if (o.has("i")) o.getInt("i") else null,
                    exterior = if (o.has("e")) o.getInt("e") else null
                )
            }.toMap()
        } catch (_: JSONException) {
            emptyMap()
        }
    }
}

fun Person.typePaceMap(): Map<Long, TypePace> = TypePaces.decode(typePaces)

/**
 * This person's pace for [type], in windows per hour. Standard uses their own Standard pace.
 * Any other type uses their saved pace for it, or else scales their Standard pace by the
 * type's pace relative to Standard.
 */
fun Person.paceFor(type: WindowType, interior: Boolean, standard: WindowType): Double {
    val own = if (interior) interiorPace else exteriorPace
    if (type.id == standard.id) return own.toDouble()

    val saved = typePaceMap()[type.id]?.let { if (interior) it.interior else it.exterior }
    if (saved != null) return saved.toDouble()

    val typePace = if (interior) type.interiorPace else type.exteriorPace
    val standardPace = if (interior) standard.interiorPace else standard.exteriorPace
    return own * typePace.toDouble() / standardPace
}