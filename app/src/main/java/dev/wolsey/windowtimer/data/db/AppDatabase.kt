package dev.wolsey.windowtimer.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Job::class, Run::class, Visit::class, WindowType::class, Person::class, SavedCrew::class, GeoPlace::class],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
    ],
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun runDao(): RunDao
    abstract fun visitDao(): VisitDao
    abstract fun windowTypeDao(): WindowTypeDao
    abstract fun personDao(): PersonDao
    abstract fun savedCrewDao(): SavedCrewDao
    abstract fun geoPlaceDao(): GeoPlaceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Adds the table that remembers where each address is on the map. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `places` (" +
                            "`address` TEXT NOT NULL, " +
                            "`lat` REAL NOT NULL, " +
                            "`lng` REAL NOT NULL, " +
                            "PRIMARY KEY(`address`))"
                )
            }
        }

        /** One shared database for the whole app, created the first time it's needed. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "windowtimer.db"
                ).addMigrations(MIGRATION_6_7).build().also { instance = it }
            }
    }
}