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
import com.example.weatherapp.currentWeather.currentWeather
import com.example.weatherapp.database.WeatherData
import com.example.weatherapp.database.WeatherDatabase
import com.example.weatherapp.database.WeatherRepository
import com.example.weatherapp.historicalWeather.historicalWeather
import com.example.weatherapp.weatherFor10Years.weatherForEachYear
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
import kotlinx.coroutines.coroutineScope
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

    // Current Temperature
    private var temp: String? = null

    // lat and long
    private var latitude: Double? = null
    private var longitude: Double? = null

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
            latitude = latLng.first
            longitude = latLng.second
            getCurrentWeatherData(latitude!!, longitude!!)
            getWeather(latitude!!, longitude!!, country)
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

    private fun getCurrentWeatherData(latitude: Double, longitude: Double) {
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
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (response.isSuccessful && !body.isNullOrBlank()) {
                        val weatherData = parseCurrentWeatherData(body)

                        temp = weatherData.current.temperature_2m.toString() + "°C"
                    } else {
                        Log.e("MainActivity", "Failed to get weather data: ${response.message}")
                    }
                }
            })
        }
    }

    private fun parseCurrentWeatherData(tempBody: String): currentWeather {
        val gson = Gson()
        return gson.fromJson(tempBody, currentWeather::class.java)
    }

    private fun getWeather(latitude: Double, longitude: Double, country: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val c = Calendar.getInstance()
        c.time = sdf.parse(currentDate)
        c.add(Calendar.DATE, -10)
        val previousDate = sdf.format(c.time)

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
                        val avgTemp = historicalWeatherData.daily.temperature_2m_mean
                        val maxTemp = historicalWeatherData.daily.temperature_2m_max
                        val minTemp = historicalWeatherData.daily.temperature_2m_min

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
        weatherRepository.getAllWeatherData(inputCountry.text.toString()).let { weatherData ->
            val entries = ArrayList<Entry>()
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

        getWeatherFor10Years()

    }

    private fun getWeatherFor10Years() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val c = Calendar.getInstance()
        c.time = sdf.parse(currentDate)
        c.add(Calendar.YEAR, -1)
        val previousDate = sdf.format(c.time)

        val currentYear = previousDate.substring(0, 4).toInt()
        val startYear = currentYear
        val endYear = currentYear - 10

        val coroutineScope = CoroutineScope(Dispatchers.Main)

        // Loop through each year from the current year to 10 years ago
        val averageTemperatures = HashMap<String, Float>()
        for (year in endYear..startYear) {
            val date = "$year-" + previousDate.substring(5, 10)

            coroutineScope.launch {

                val BASE_URL =
                    "https://archive-api.open-meteo.com/v1/archive?latitude=$latitude&longitude=$longitude&start_date=$date&end_date=$date&daily=temperature_2m_mean&timezone=auto"

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
                            val weatherData = parseWeatherForEachYear(body)

                            val avgTemp = weatherData.daily.temperature_2m_mean[0]
                            averageTemperatures[date] = avgTemp.toFloat()
                            Log.d("MainActivity", "Average temperature for $date: $avgTemp")

                            if (averageTemperatures.size == 10) {
                                calculateAverageTemperature(averageTemperatures)
                            }
                        } else {
                            Log.e("MainActivity", "Failed to get weather data: ${response.message}")
                        }
                    }
                })
            }
        }

    }

    private fun parseWeatherForEachYear(tempBody: String): weatherForEachYear {
        val gson = Gson()
        return gson.fromJson(tempBody, weatherForEachYear::class.java)
    }

    private fun calculateAverageTemperature(averageTemperatures: HashMap<String, Float>) {
        var sum = 0.0f
        for (temperature in averageTemperatures.values) {
            sum += temperature
        }
        val avg = sum / 10

        val percentChange = ((temp!!.substring(0, temp!!.length - 2).toFloat() - avg) / avg) * 100

        updateUI(weatherDataTextView, "Average temperature for the last 10 years: $avg°C \nCurrent temperature: $temp  \nChange in temperature: $percentChange%")
    }

    private fun updateUI(textView: TextView, data: String) {
        runOnUiThread {
            textView.text = data
        }
    }
}