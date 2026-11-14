package dev.wolsey.windowtimer.data.store

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import dev.wolsey.windowtimer.data.PaceSettings
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.WindowType
import kotlinx.coroutines.flow.Flow

object TypeStore {

    fun types(context: Context): Flow<List<WindowType>> =
        AppDatabase.get(context).windowTypeDao().observeAll()

    suspend fun all(context: Context): List<WindowType> =
        AppDatabase.get(context).windowTypeDao().getAll()

    suspend fun standard(context: Context): WindowType? =
        AppDatabase.get(context).windowTypeDao().findStandard()

    /** Creates the Standard type from the current pace settings the first time it's needed. */
    suspend fun ensureStandard(context: Context) {
        val dao = AppDatabase.get(context).windowTypeDao()
        if (dao.findStandard() != null) return
        val pace = PaceSettings.load(context)
        dao.insert(
            WindowType(
                name = "Standard",
                interiorPace = pace.interior,
                exteriorPace = pace.exterior,
                isStandard = true
            )
        )
    }

    /** Adds or updates a type. Returns false if another type already has that name. */
    suspend fun save(context: Context, type: WindowType): Boolean {
        val dao = AppDatabase.get(context).windowTypeDao()
        return try {
            if (type.id == 0L) dao.insert(type) else dao.update(type)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun delete(context: Context, type: WindowType) {
        AppDatabase.get(context).windowTypeDao().deleteById(type.id)
    }
}