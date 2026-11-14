package dev.wolsey.windowtimer.data.routing

import android.content.Context
import dev.wolsey.windowtimer.data.RoutingSettings
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.GeoPlace
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.store.GeoStore

/**
 * @property ordered the stops that could be placed, in driving order
 * @property unplaced stops whose address couldn't be found on the map
 * @property driveSeconds total driving time, or null when there was nothing to compare
 */
data class PlanResult(
    val ordered: List<Job>,
    val unplaced: List<Job>,
    val driveSeconds: Double?
)

object RoutePlanner {

    /** Works out the best order for [jobs], starting from [shopAddress]. */
    suspend fun plan(
        context: Context,
        shopAddress: String,
        jobs: List<Job>,
        returnToShop: Boolean
    ): PlanResult {
        val key = RoutingSettings.apiKey(context)
        if (key.isBlank()) throw RoutingException("Add your OpenRouteService key in Settings.")
        if (shopAddress.isBlank()) throw RoutingException("Add your shop address in Settings.")

        val cache = AppDatabase.get(context).geoPlaceDao()

        // Each address is looked up once and remembered. A spot you set yourself is kept.
        suspend fun locate(address: String, near: LatLng?): LatLng? {
            val id = GeoStore.key(address)
            cache.find(id)?.let { return LatLng(it.lat, it.lng) }
            val found = OrsClient.geocode(key, address, near) ?: return null
            cache.put(GeoPlace(id, found.point.lat, found.point.lng, found.label))
            return found.point
        }

        val shop = locate(shopAddress, null)
            ?: throw RoutingException("Couldn't find the shop address on the map.")

        val placed = mutableListOf<Pair<Job, LatLng>>()
        val unplaced = mutableListOf<Job>()
        for (job in jobs) {
            val spot = locate(job.address, shop)
            if (spot == null) unplaced.add(job) else placed.add(job to spot)
        }

        if (placed.size < 2) {
            return PlanResult(placed.map { it.first }, unplaced, null)
        }

        val matrix = OrsClient.durations(key, listOf(shop) + placed.map { it.second })
        val order = RouteOptimizer.order(matrix, returnToShop)
        return PlanResult(
            ordered = order.map { placed[it - 1].first },
            unplaced = unplaced,
            driveSeconds = RouteOptimizer.cost(matrix, order, returnToShop)
        )
    }
}