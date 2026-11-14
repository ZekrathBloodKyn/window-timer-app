package dev.wolsey.windowtimer.data

import android.content.Context
import androidx.core.content.edit

/** Settings for ordering the day's route. */
object RoutingSettings {
    private const val PREFS = "routing"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String = prefs(context).getString("key", "").orEmpty()
    fun saveApiKey(context: Context, key: String) = prefs(context).edit { putString("key", key.trim()) }

    /** Whether the route ends back at the shop. */
    fun returnToShop(context: Context): Boolean = prefs(context).getBoolean("return", true)
    fun saveReturnToShop(context: Context, value: Boolean) = prefs(context).edit { putBoolean("return", value) }

    /** Whether the route is re-ordered by itself whenever the stops change. */
    fun autoOrder(context: Context): Boolean = prefs(context).getBoolean("auto", true)
    fun saveAutoOrder(context: Context, value: Boolean) = prefs(context).edit { putBoolean("auto", value) }
}