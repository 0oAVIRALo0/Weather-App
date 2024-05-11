package com.example.weatherapp.database

class WeatherRepository(private val weatherDao: WeatherDao) {
    suspend fun insertWeatherData(weatherData: WeatherData) {
        weatherDao.insertWeatherData(weatherData)
    }
}