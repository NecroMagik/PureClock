package com.necromagik.pureclock.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cities",
    indices = [
        Index(value = ["cityName"]),
        Index(value = ["countryName"])
    ]
)
data class CityEntity(
    @PrimaryKey val id: String,         // e.g. "asia_shanghai_beijing" или "europe_moscow"
    val cityName: String,               // "Пекин"
    val cityNameEn: String,             // "Beijing"
    val countryName: String,            // "Китай"
    val countryCode: String,            // "CN"
    val timeZoneId: String,             // "Asia/Shanghai"
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val searchKeywords: String          // "пекин beijing китай cn asia/shanghai pekin"
)