package com.example.weatherapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.database.WeatherData
import com.example.weatherapp.database.WeatherDatabase
import com.example.weatherapp.database.WeatherRepository
import com.example.weatherapp.historicalWeather.historicalWeather
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
import java.util.Locale
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class HistoryActivity: AppCompatActivity() {
    private lateinit var inputCountry: EditText
    private lateinit var searchBut: Button
    private lateinit var historicalWeatherGraph: LineChart
    private lateinit var homeButton: Button
    private lateinit var searchButton: Button
    private lateinit var weatherDataTextView: TextView

    // database repository variable initialization
    private lateinit var weatherRepository: WeatherRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history)

        inputCountry = findViewById(R.id.inputCountry)
        searchBut = findViewById(R.id.searchBut)
        historicalWeatherGraph = findViewById(R.id.graph)
        homeButton = findViewById(R.id.homeButton)
        searchButton = findViewById(R.id.searchButton)
        weatherDataTextView = findViewById(R.id.weatherDataTextView)

        // database repository
        weatherRepository = WeatherRepository(WeatherDatabase.getInstance(this).weatherDao())

        searchBut.setOnClickListener {
            val country = inputCountry.text.toString()
            val latLng = getLatLng(this, country)
            val latitude = latLng.first
            val longitude = latLng.second
            getWeather(latitude, longitude, country)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        searchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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
        try {
            val addresses: MutableList<Address>? = geocoder.getFromLocationName(country, 1)
            if (addresses != null) {
                if (addresses.isNotEmpty()) {
                    latitude = addresses[0].latitude
                    longitude = addresses[0].longitude
                }
            }
        } catch (e: IOException) {
            Log.e("HistoryActivity", "Geocoder error: ${e.message}")
        }
        return Pair(latitude, longitude)
    }

    private fun getWeather(latitude: Double, longitude: Double, country: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val c = Calendar.getInstance()
        c.time = sdf.parse(currentDate)
        c.add(Calendar.DATE, -10)
        val previousDate = sdf.format(c.time)
        Log.d("HistoryActivity", "Previous Date: $previousDate")

        val BASE_URL = "https://archive-api.open-meteo.com/v1/archive?latitude=$latitude&longitude=$longitude&start_date=2013-01-01&end_date=$previousDate&daily=temperature_2m_max,temperature_2m_min,temperature_2m_mean&timezone=auto"

        val coroutineScope = CoroutineScope(Dispatchers.Main)

        coroutineScope.launch {
            val request = Request.Builder()
                .url(BASE_URL)
                .build()

            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MainActivity", "Failed to get weather data: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (response.isSuccessful && !body.isNullOrBlank()) {
                        val historicalWeatherData = parseWeatherData(body)

                        val time = historicalWeatherData.daily.time
//                        Log.d("HistoryActivity", "Time: $time")
                        val avgTemp = historicalWeatherData.daily.temperature_2m_mean
//                        Log.d("HistoryActivity", "Avg Temp: $avgTemp")
                        val maxTemp = historicalWeatherData.daily.temperature_2m_max
//                        Log.d("HistoryActivity", "Max Temp: $maxTemp")
                        val minTemp = historicalWeatherData.daily.temperature_2m_min
//                        Log.d("HistoryActivity", "Min Temp: $minTemp")

                        for (i in time.indices) {
                            val weatherData = WeatherData(
                                location = country,
                                date = time[i],
                                temperature = avgTemp[i] ,
                                minTemp = minTemp[i],
                                maxTemp = maxTemp[i]
                            )

                            coroutineScope.launch {
                                weatherRepository.insertWeatherData(weatherData)
//                                Log.d("HistoryActivity", "Inserted weather data: $weatherData")
                            }
                        }

                        coroutineScope.launch {
                            plotGraph()
                        }
                    } else {
                        Log.e("MainActivity", "Failed to get weather data: ${response.message}")
                    }
                }
            })
        }
    }

    private fun parseWeatherData(tempBody: String): historicalWeather {
        val gson = Gson()
        return gson.fromJson(tempBody, historicalWeather::class.java)
    }

    private suspend fun plotGraph() {
        weatherRepository.getAllWeatherData().let { weatherData ->
            val entries = ArrayList<Entry>()
            Log.d("HistoryActivity", "Weather data length: ${weatherData.size}")
            for (i in weatherData.indices) {
                entries.add(Entry(i.toFloat(), weatherData[i].temperature.toFloat()))
            }

            val lineDataSet = LineDataSet(entries, "Temperature")
            lineDataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toString() + "°C"
                }
            }

            val lineData = LineData(lineDataSet)
            historicalWeatherGraph.data = lineData
            historicalWeatherGraph.invalidate()
        }

        calculateAverage()

    }

    private suspend fun calculateAverage() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val c = Calendar.getInstance()
        c.time = sdf.parse(currentDate)
        c.add(Calendar.YEAR, -1)
        val previousDate = sdf.format(c.time)
        Log.d("HistoryActivity", "Previous Date: $previousDate")

        val currentYear = previousDate.substring(0, 4).toInt()
        Log.d("HistoryActivity", "Current Year: $currentYear")
        val startYear = currentYear
        Log.d("HistoryActivity", "Start Year: $startYear")
        val endYear = currentYear - 10
        Log.d("HistoryActivity", "End Year: $endYear")

        // Loop through each year from the current year to 10 years ago
        val averageTemperatures = HashMap<String, Float>()
        for (year in startYear downTo endYear) {
            val date = "$year-" + previousDate.substring(5, 10)
            Log.d("HistoryActivity", "Date: $date")

            runBlocking {
                // Retrieve temperature data for the current year
                val data = weatherRepository.getWeatherDataByDate(date)
                weatherDataTextView.text = "Average temperature for $date: ${data.temperature}°C"
            }
        }
    }

    private fun updateUI(textView: TextView, data: String) {
        runOnUiThread {
            textView.text = data
        }
    }
}