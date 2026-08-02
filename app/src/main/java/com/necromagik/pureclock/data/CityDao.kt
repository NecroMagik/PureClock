package com.necromagik.pureclock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityDao {

    @Query("""
        SELECT * FROM cities 
        WHERE searchKeywords LIKE '%' || :query || '%' 
        ORDER BY 
            CASE WHEN cityName LIKE :query || '%' THEN 1 ELSE 2 END,
            cityName ASC 
        LIMIT 50
    """)
    suspend fun searchCities(query: String): List<CityEntity>

    @Query("SELECT * FROM cities WHERE id = :id LIMIT 1")
    suspend fun getCityById(id: String): CityEntity?

    @Query("SELECT * FROM cities WHERE id IN (:ids)")
    suspend fun getCitiesByIds(ids: Set<String>): List<CityEntity>

    // Возвращаем List<Long> (массив созданных rowId) или Unit явно
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cities: List<CityEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM cities")
    suspend fun getCount(): Int

    // Возвращаем кол-во удаленных строк (Int) вместо void/Unit
    @Query("DELETE FROM cities")
    suspend fun clearAll(): Int
}