package com.example.weatherapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomWarnings

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeatherData(weatherData: WeatherData)

    @Query("SELECT * FROM weather_data")
    suspend fun getAllWeatherData(): List<WeatherData>

    @Query("SELECT * FROM weather_data WHERE date = :date LIMIT 1")
    suspend fun getWeatherDataByDate(date: String): WeatherData
}