package dev.wolsey.windowtimer.data.store

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.SavedCrew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object SavedCrewStore {
    fun crews(context: Context): Flow<List<SavedCrew>> =
        AppDatabase.get(context).savedCrewDao().observeAll()

    /** Returns false if another crew already has that name. */
    suspend fun save(context: Context, crew: SavedCrew): Boolean = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).savedCrewDao()
        try {
            if (crew.id == 0L) dao.insert(crew) else dao.update(crew)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun delete(context: Context, id: Long) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).savedCrewDao().deleteById(id)
    }
}