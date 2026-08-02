package com.necromagik.pureclock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, CityEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun cityDao(): CityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pure_clock_database"
                )
                    .fallbackToDestructiveMigration()
                    // Если положишь предзагруженный файл базы в assets/databases/cities_initial.db,
                    // раскомментируй следующую строчку:
                    // .createFromAsset("databases/cities_initial.db")
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}