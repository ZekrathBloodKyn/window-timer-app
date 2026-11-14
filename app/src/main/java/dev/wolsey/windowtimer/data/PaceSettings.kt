package dev.wolsey.windowtimer.data

import android.content.Context
import androidx.core.content.edit

/** Windows per hour for each kind of window. */
data class Pace(val interior: Int, val exterior: Int)

object PaceSettings {
    private const val PREFS = "pace_settings"

    const val DEFAULT_INTERIOR = 24
    const val DEFAULT_EXTERIOR = 22

    fun load(context: Context): Pace {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pace(
            interior = prefs.getInt("interior", DEFAULT_INTERIOR),
            exterior = prefs.getInt("exterior", DEFAULT_EXTERIOR)
        )
    }

    fun save(context: Context, pace: Pace) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("interior", pace.interior)
                .putInt("exterior", pace.exterior)
        }
    }
}