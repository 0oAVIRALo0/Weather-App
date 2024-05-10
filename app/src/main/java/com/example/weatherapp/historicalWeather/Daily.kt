package com.example.weatherapp.historicalWeather

data class Daily(
    val temperature_2m_mean: List<Double>,
    val time: List<String>
)