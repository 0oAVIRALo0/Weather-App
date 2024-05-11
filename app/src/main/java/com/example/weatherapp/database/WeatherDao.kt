package com.example.weatherapp.database

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeatherData(weatherData: WeatherData)
}