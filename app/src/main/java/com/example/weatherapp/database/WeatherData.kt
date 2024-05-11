package com.example.weatherapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.Double

@Entity(tableName = "weather_data")
data class WeatherData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val location: String,
    val date: String,
    val temperature: Double,
    val minTemp: Double,
    val maxTemp: Double
)
