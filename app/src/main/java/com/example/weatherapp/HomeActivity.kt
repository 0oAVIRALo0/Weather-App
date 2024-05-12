package com.example.weatherapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.currentWeather.currentWeather
import com.example.weatherapp.database.WeatherDatabase
import com.example.weatherapp.database.WeatherRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale
import okhttp3.*
import java.io.IOException
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var location: TextView
    private lateinit var temperature: TextView
    private lateinit var minTemp: TextView
    private lateinit var maxTemp: TextView
    private lateinit var weatherIcon: ImageView
//    private lateinit var backgroundImage: ImageView
    private lateinit var homeButton: Button
    private lateinit var searchButton: Button
    private lateinit var historyButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        location = findViewById(R.id.location)
        temperature = findViewById(R.id.temperature)
        weatherIcon = findViewById(R.id.weatherIcon)
//        backgroundImage = findViewById(R.id.backgroundImage)
        homeButton = findViewById(R.id.homeButton)
        searchButton = findViewById(R.id.searchButton)
        historyButton = findViewById(R.id.historyButton)
        minTemp = findViewById(R.id.minTemp)
        maxTemp = findViewById(R.id.maxTemp)

        // Check for location permissions
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        } else {
            getCurrentLocation(fusedLocationClient)
        }

        searchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("currentTemp", temperature.text)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        historyButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("currentTemp", temperature.text)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation(fusedLocationClient)
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLocation(fusedLocationProviderClient: FusedLocationProviderClient) {
        try {
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        getAddressFromLocation(latitude, longitude, this)
                        getCurrentWeatherData(latitude, longitude)
                    } else {
                        Toast.makeText(this, "Location is null", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: SecurityException) {
            Log.e("HomeActivity", "Failed to get location: ${e.message}")
            Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double, context: Context) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses: MutableList<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address: Address = addresses[0]
                val myLocation = address.locality
                if (!myLocation.isNullOrBlank()) {
                    updateUI(location, myLocation)
                } else {
                    updateUI(location, "No address found")
                }
            } else {
                updateUI(location, "No address found")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            updateUI(location, "No address found")
        }
    }


    private fun getCurrentWeatherData(latitude: Double, longitude: Double) {
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
                            Toast.makeText(this@HomeActivity, "Failed to get weather data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        if (response.isSuccessful && !body.isNullOrBlank()) {
                            val weatherData = parseWeatherData(body)

                            val temp = weatherData.current.temperature_2m
                            val isDay = weatherData.current.is_day
                            val tempMax = weatherData.daily.temperature_2m_max[0]
                            val tempMin = weatherData.daily.temperature_2m_min[0]
                            updateUI(temperature, "${temp}°C")
                            updateUI(minTemp, "Min: ${tempMin}°C")
                            updateUI(maxTemp, "Max: ${tempMax}°C")
                            updateWeatherIcon(weatherIcon, isDay)
                        } else {
                            Log.e("MainActivity", "Failed to get weather data: ${response.message}")
                            runOnUiThread {
                                Toast.makeText(this@HomeActivity, "Failed to get weather data: ${response.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to get weather data: ${e.message}")
            Toast.makeText(this, "Failed to get weather data: ${e.message}", Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val PERMISSION_REQUEST_LOCATION = 1001
    }
}
