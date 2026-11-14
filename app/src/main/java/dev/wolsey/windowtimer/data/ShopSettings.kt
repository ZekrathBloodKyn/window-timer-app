package dev.wolsey.windowtimer.data

import android.content.Context
import androidx.core.content.edit

/** Where "Shop" navigates to. Empty until set in Settings. */
object ShopSettings {
    private const val PREFS = "shop"
    private const val KEY = "address"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun save(context: Context, address: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, address.trim())
        }
    }
}