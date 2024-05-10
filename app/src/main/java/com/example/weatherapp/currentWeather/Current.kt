package com.example.weatherapp.currentWeather

data class Current(
    val interval: Int,
    val is_day: Int,
    val temperature_2m: Double,
    val time: String
)