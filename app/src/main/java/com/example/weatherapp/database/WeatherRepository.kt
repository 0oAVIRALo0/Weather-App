package com.example.weatherapp.database

class WeatherRepository(private val weatherDao: WeatherDao) {
    suspend fun insertWeatherData(weatherData: WeatherData) {
        weatherDao.insertWeatherData(weatherData)
    }

    suspend fun getAllWeatherData(location: String): List<WeatherData> {
        return weatherDao.getAllWeatherData(location)
    }

    suspend fun getTemperatureByDate(date: String, location: String): Double? {
        return weatherDao.getTemperatureByDate(date, location)
    }
}