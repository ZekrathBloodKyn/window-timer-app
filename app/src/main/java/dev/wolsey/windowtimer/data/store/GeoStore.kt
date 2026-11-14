package dev.wolsey.windowtimer.data.store

import android.content.Context
import dev.wolsey.windowtimer.data.RoutingSettings
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.GeoPlace
import dev.wolsey.windowtimer.data.routing.LatLng
import dev.wolsey.windowtimer.data.routing.OrsClient
import dev.wolsey.windowtimer.data.routing.RoutingException
import kotlinx.coroutines.flow.Flow

/** Where addresses are on the map: found automatically, or set by you. */
object GeoStore {

    /** How an address is stored, ignoring case and extra spaces. */
    fun key(address: String): String =
        address.trim().lowercase().replace(Regex("\\s+"), " ")

    fun places(context: Context): Flow<List<GeoPlace>> =
        AppDatabase.get(context).geoPlaceDao().observeAll()

    /** Pins [address] to an exact spot. */
    suspend fun setManual(context: Context, address: String, point: LatLng) {
        AppDatabase.get(context).geoPlaceDao()
            .put(GeoPlace(key(address), point.lat, point.lng, "Set by you", true))
    }

    /** Forgets what's stored, so the address is looked up again next time. */
    suspend fun clear(context: Context, address: String) {
        AppDatabase.get(context).geoPlaceDao().delete(key(address))
    }

    /**
     * Looks up [searchText] and stores the result for [address], replacing whatever was there.
     * Returns null if nothing was found.
     */
    suspend fun lookup(context: Context, address: String, searchText: String, near: LatLng?): GeoPlace? {
        val apiKey = RoutingSettings.apiKey(context)
        if (apiKey.isBlank()) throw RoutingException("Add your OpenRouteService key in Settings.")
        val found = OrsClient.geocode(apiKey, searchText, near) ?: return null
        val place = GeoPlace(key(address), found.point.lat, found.point.lng, found.label, false)
        AppDatabase.get(context).geoPlaceDao().put(place)
        return place
    }
}