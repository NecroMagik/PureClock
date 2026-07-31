package com.necromagik.pureclock.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    // ============================================================================
// СЕКЦИЯ 1: ЗАПРОСЫ ЧТЕНИЯ (FLOW & SUSPEND)
// ============================================================================
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarmsSync(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    // ============================================================================
// СЕКЦИЯ 2: ЗАПРОСЫ МОДИФИКАЦИИ (INSERT / UPDATE / DELETE)
// ============================================================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity): Int

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity): Int
}