package dev.wolsey.windowtimer.data.store

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import dev.wolsey.windowtimer.data.PaceSettings
import dev.wolsey.windowtimer.data.db.AppDatabase
import dev.wolsey.windowtimer.data.db.Person
import kotlinx.coroutines.flow.Flow
import androidx.core.content.edit

object PersonStore {
    private const val PREFS = "crew"
    private const val KEY_LAST = "last"

    fun people(context: Context): Flow<List<Person>> =
        AppDatabase.get(context).personDao().observeAll()

    suspend fun all(context: Context): List<Person> =
        AppDatabase.get(context).personDao().getAll()

    /** Creates "Me" from the Standard paces the first time it's needed. */
    suspend fun ensureOwner(context: Context) {
        val dao = AppDatabase.get(context).personDao()
        if (dao.findOwner() != null) return
        val standard = TypeStore.standard(context)
        val pace = PaceSettings.load(context)
        dao.insert(
            Person(
                name = "Me",
                interiorPace = standard?.interiorPace ?: pace.interior,
                exteriorPace = standard?.exteriorPace ?: pace.exterior,
                isOwner = true
            )
        )
    }

    /** Adds or updates a person. Returns false if another person already has that name. */
    suspend fun save(context: Context, person: Person): Boolean {
        val dao = AppDatabase.get(context).personDao()
        return try {
            if (person.id == 0L) dao.insert(person) else dao.update(person)
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    suspend fun delete(context: Context, person: Person) {
        AppDatabase.get(context).personDao().deleteById(person.id)
    }

    /** The people who worked the last run, so the next one starts with the same crew. */
    fun lastCrewIds(context: Context): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    fun saveLastCrew(context: Context, ids: List<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST, ids.joinToString(","))
        }
    }
}