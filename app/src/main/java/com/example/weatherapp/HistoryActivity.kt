package com.example.weatherapp

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
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

class HistoryActivity: AppCompatActivity() {
    private lateinit var inputCountry: EditText
    private lateinit var searchBut: Button
    private lateinit var historicalWeatherGraph: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history)

        inputCountry = findViewById(R.id.inputCountry)
        searchBut = findViewById(R.id.searchButton)
        historicalWeatherGraph = findViewById(R.id.graph)

        searchBut.setOnClickListener {
            val country = inputCountry.text.toString()
            val latLng = getLatLng(this, country)
            val latitude = latLng.first
            val longitude = latLng.second
            getWeather(latitude, longitude)
        }
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

    private fun getWeather(latitude: Double, longitude: Double) {
        val BASE_URL =
            "https://archive-api.open-meteo.com/v1/archive?latitude=$latitude&longitude=$longitude&start_date=2014-01-01&end_date=2024-01-01&daily=temperature_2m_mean&timezone=auto"

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
                        plotGraph(time, avgTemp)
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


    private fun plotGraph(time: List<String>, avgTemp: List<Double>) {
        val entries = ArrayList<Entry>()
        for (i in time.indices) {
            entries.add(Entry(i.toFloat(), avgTemp[i].toFloat()))
        }

        val dataSet = LineDataSet(entries, "Average Temperature")
        val lineData = LineData(dataSet)
        historicalWeatherGraph.data = lineData
        historicalWeatherGraph.invalidate()
    }
}