package dev.wolsey.windowtimer.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.Job
import dev.wolsey.windowtimer.data.db.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.core.content.edit

/**
 * Copies jobs and runs saved by earlier versions of the app (kept as JSON in
 * SharedPreferences) into the Room database. Runs once; the old data is left in
 * place as a backup.
 */
object LegacyImport {
    private const val TAG = "LegacyImport"
    private const val FLAG_PREFS = "migration"
    private const val FLAG_KEY = "room_import_done"

    suspend fun runIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val flags = context.getSharedPreferences(FLAG_PREFS, Context.MODE_PRIVATE)
        if (flags.getBoolean(FLAG_KEY, false)) return@withContext

        try {
            val jobs = readJobs(context)
            val runs = readRuns(context)

            // All or nothing: a failure part-way leaves the database empty and the flag unset,
            // so the import is simply tried again next launch.
            val db = AppDatabase.get(context)
            db.withTransaction {
                db.jobDao().insertAll(jobs)
                db.runDao().insertAll(runs)
            }

            flags.edit { putBoolean(FLAG_KEY, true) }
            Log.i(TAG, "Imported ${jobs.size} jobs and ${runs.size} runs")
        } catch (e: Exception) {
            Log.e(TAG, "Import failed; will retry next launch", e)
        }
    }

    private fun readJobs(context: Context): List<Job> {
        val raw = context.getSharedPreferences("jobs", Context.MODE_PRIVATE)
            .getString("list", null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Job(
                name = o.getString("name"),
                interior = o.getInt("interior"),
                exterior = o.getInt("exterior"),
                savedAt = o.optLong("savedAt", 0L),
                address = o.optString("address", "")
            )
        }
    }

    private fun readRuns(context: Context): List<Run> {
        val raw = context.getSharedPreferences("runs", Context.MODE_PRIVATE)
            .getString("list", null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            val interior = o.getInt("interior")
            val exterior = o.getInt("exterior")
            Run(
                jobName = o.getString("name"),
                startedAt = o.getLong("startedAt"),
                plannedMs = o.getLong("plannedMs"),
                actualMs = o.getLong("actualMs"),
                interior = interior,
                exterior = exterior,
                plannedInterior = o.optInt("plannedInterior", interior),
                plannedExterior = o.optInt("plannedExterior", exterior)
            )
        }
    }
}