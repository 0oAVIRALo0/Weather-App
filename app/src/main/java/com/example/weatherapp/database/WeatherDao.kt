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

    @Query("SELECT temperature FROM weather_data WHERE date = :date AND location = :location")
    suspend fun getTemperatureByDate(date: String, location: String): Double?
}