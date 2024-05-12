package com.example.weatherapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.content.Context;
import android.content.Intent
import android.location.Geocoder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import android.location.Address;
import android.os.PersistableBundle
import android.widget.Toast
import com.example.weatherapp.currentWeather.currentWeather
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SearchActivity: AppCompatActivity() {
    private lateinit var inputCountry: EditText
    private lateinit var searchBut: Button
    private lateinit var location: TextView
    private lateinit var weather: TextView
    private lateinit var weatherIcon: ImageView
//    private lateinit var backgroundImage: ImageView
    private lateinit var tempMin: TextView
    private lateinit var tempMax: TextView
    private lateinit var homeButton: Button
    private lateinit var searchActivityButton: Button
    private lateinit var historyButton: Button

    // state variables to store the weather data
    private var locationText: String = ""
    private var weatherText: String = ""
    private var minTempText: String = ""
    private var maxTempText: String = ""
    private var weatherIconValue: Int = 0

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("location", locationText)
        outState.putString("weather", weatherText)
        outState.putString("minTemp", minTempText)
        outState.putString("maxTemp", maxTempText)
        outState.putInt("weatherIcon", weatherIconValue)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search)

        inputCountry = findViewById(R.id.inputCountry)
        searchBut = findViewById(R.id.searchBut)
        location = findViewById(R.id.location)
        weather = findViewById(R.id.weather)
        weatherIcon = findViewById(R.id.weatherIcon)
//        backgroundImage = findViewById(R.id.backgroundImage)
        tempMin = findViewById(R.id.minTemp)
        tempMax = findViewById(R.id.maxTemp)
        homeButton = findViewById(R.id.homeButton)
        searchActivityButton = findViewById(R.id.searchButton)
        historyButton = findViewById(R.id.historyButton)

        val temp = intent.getStringExtra("currentTemp")

        searchBut.setOnClickListener {
            val country = inputCountry.text.toString()

            if (country.isEmpty()) {
                showToast("Please enter a country")
                return@setOnClickListener
            }

            updateUI(location, country)
            val latLng = getLatLng(this, country)
            val latitude = latLng.first
            val longitude = latLng.second
            getWeather(latitude, longitude)
        }

        if (savedInstanceState != null) {
            locationText = savedInstanceState.getString("location", "")
            weatherText = savedInstanceState.getString("weather", "")
            minTempText = savedInstanceState.getString("minTemp", "")
            maxTempText = savedInstanceState.getString("maxTemp", "")
            weatherIconValue = savedInstanceState.getInt("weatherIcon", 0)
            updateUI(location, locationText)
            updateUI(weather, weatherText)
            updateUI(tempMin, minTempText)
            updateUI(tempMax, maxTempText)
            updateWeatherIcon(weatherIcon, weatherIconValue)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        historyButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("currentTemp", temp)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun getLatLng(context: Context, country: String): Pair<Double, Double>{
        var latitude: Double = 0.0
        var longitude: Double = 0.0
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: MutableList<Address>? = geocoder.getFromLocationName(country, 1)
        if (addresses != null) {
            if (addresses.isNotEmpty()) {
                latitude = addresses[0].latitude
                longitude = addresses[0].longitude
            }
        }
        return Pair(latitude, longitude)
    }

    private fun getWeather(latitude: Double, longitude: Double){
        try {
            val BASE_URL = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

            val coroutineScope = CoroutineScope(Dispatchers.Main)

            coroutineScope.launch {
                val request = Request.Builder()
                    .url(BASE_URL)
                    .build()

                val client = OkHttpClient()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("MainActivity", "Failed to get weather data: ${e.message}")
                        runOnUiThread {
                            showToast("Failed to get weather data: ${e.message}")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        if (response.isSuccessful && !body.isNullOrBlank()) {
                            val weatherData = parseWeatherData(body)

                            val temp = weatherData.current.temperature_2m
                            val isDay = weatherData.current.is_day
                            val minTemp = weatherData.daily.temperature_2m_min[0]
                            val maxTemp = weatherData.daily.temperature_2m_max[0]
                            updateUI(weather, "${temp}°C")
                            updateUI(tempMin, "Min: ${minTemp}°C")
                            updateUI(tempMax, "Max: ${maxTemp}°C")
                            updateWeatherIcon(weatherIcon, isDay)
                        } else {
                            Log.e("MainActivity", "Failed to get weather data: ${response.message}")
                            runOnUiThread {
                                showToast("Failed to get weather data: ${response.message}")
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get weather data: ${e.message}")
            showToast("Failed to get weather data: ${e.message}")
        }
    }

    private fun parseWeatherData(tempBody: String): currentWeather {
        val gson = Gson()
        return gson.fromJson(tempBody, currentWeather::class.java)
    }

    private fun updateUI(textView: TextView, data: String) {
        runOnUiThread {
            textView.text = data
        }
    }

    private fun updateWeatherIcon(weatherIcon: ImageView, value: Int) {
        runOnUiThread {
            if (value == 0) {
                weatherIcon.setImageResource(R.drawable.night)
//                backgroundImage.setImageResource(R.drawable.night_bg)
            } else {
                weatherIcon.setImageResource(R.drawable.day)
//                backgroundImage.setImageResource(R.drawable.sunny_bg)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}