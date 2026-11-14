package dev.wolsey.windowtimer.data.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LatLng(val lat: Double, val lng: Double)
/** A place the service found, with the name it matched. */
data class Found(val point: LatLng, val label: String)

/** A problem to show the user, such as a bad key or no connection. */
class RoutingException(message: String) : Exception(message)

/** Talks to OpenRouteService: finds addresses on the map and works out driving times. */
object OrsClient {
    private const val BASE = "https://api.openrouteservice.org"
    private const val UNREACHABLE = 1.0e9

    /** Where an address is, or null if it can't be found. [near] helps pick the right match. */
    suspend fun geocode(key: String, address: String, near: LatLng?): Found? =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(BASE).append("/geocode/search?api_key=").append(encode(key))
                append("&text=").append(encode(address))
                append("&size=1&boundary.country=US")
                if (near != null) append("&focus.point.lat=${near.lat}&focus.point.lon=${near.lng}")
            }
            val features = parse { JSONObject(send(url, key, null)).optJSONArray("features") }
            if (features == null || features.length() == 0) return@withContext null
            parse {
                val feature = features.getJSONObject(0)
                val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                // GeoJSON order is longitude, then latitude.
                Found(
                    LatLng(coordinates.getDouble(1), coordinates.getDouble(0)),
                    feature.optJSONObject("properties")?.optString("label").orEmpty()
                )
            }
        }

    /** Driving time in seconds between every pair of [points]: `result[i][j]` is i to j. */
    suspend fun durations(key: String, points: List<LatLng>): Array<DoubleArray> =
        withContext(Dispatchers.IO) {
            val locations = JSONArray()
            points.forEach { locations.put(JSONArray().put(it.lng).put(it.lat)) }
            val body = JSONObject()
                .put("locations", locations)
                .put("metrics", JSONArray().put("duration"))
                .toString()

            val rows = parse { JSONObject(send("$BASE/v2/matrix/driving-car", key, body)).getJSONArray("durations") }
            parse {
                Array(rows.length()) { i ->
                    val row = rows.getJSONArray(i)
                    DoubleArray(row.length()) { j -> if (row.isNull(j)) UNREACHABLE else row.getDouble(j) }
                }
            }
        }

    private fun <T> parse(block: () -> T): T =
        try {
            block()
        } catch (e: JSONException) {
            throw RoutingException("The routing service sent something unexpected.")
        }

    /** GET when [body] is null, otherwise POST of JSON. Returns the response text. */
    private fun send(url: String, key: String, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", key)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            when (val code = connection.responseCode) {
                200 -> return connection.inputStream.bufferedReader().use { it.readText() }
                401, 403 -> throw RoutingException("OpenRouteService didn't accept the API key. Check it in Settings.")
                429 -> throw RoutingException("Routing limit reached for now. Try again later.")
                else -> throw RoutingException("The routing service had a problem (code $code).")
            }
        } catch (e: IOException) {
            throw RoutingException("Couldn't reach the routing service. Check your connection.")
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}