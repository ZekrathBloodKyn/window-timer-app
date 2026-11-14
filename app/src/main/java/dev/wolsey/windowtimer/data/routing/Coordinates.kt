package dev.wolsey.windowtimer.data.routing

import kotlin.math.abs

private val PAIR = Regex("""(-?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)""")

/**
 * Reads "40.1149, -111.6549" (the format Google Maps copies), with or without a comma,
 * brackets or degree signs. Returns null if it isn't a valid latitude and longitude.
 */
fun parseCoordinates(text: String): LatLng? {
    val match = PAIR.find(text.replace("°", "")) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (abs(lat) > 90 || abs(lng) > 180) return null
    return LatLng(lat, lng)
}