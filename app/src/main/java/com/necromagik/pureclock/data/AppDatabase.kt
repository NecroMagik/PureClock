package com.necromagik.pureclock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ============================================================================
// СЕКЦИЯ 1: КОНФИГУРАЦИЯ БАЗЫ ДАННЫХ ROOM
// ============================================================================
@Database(entities = [AlarmEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    // ============================================================================
// СЕКЦИЯ 2: SINGLETON ИНИЦИАЛИЗАЦИЯ И МИГРАЦИИ
// ============================================================================
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
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}