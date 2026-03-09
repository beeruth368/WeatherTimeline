package com.example.weathertimeline

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

// --- 1. GENERAL WEATHER MODELS (OpenWeatherMap) ---
data class WeatherResponse(val main: MainData, val weather: List<WeatherData>, val name: String)
data class MainData(val temp: Double)
data class WeatherData(val main: String, val description: String, val icon: String)

// --- 2. 10-DAY FORECAST MODELS (Open-Meteo) ---
data class ForecastResponse(val daily: DailyData, val hourly: HourlyData)
data class DailyData(
    val time: List<String>,
    val weathercode: List<Int>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val precipitation_sum: List<Double>,
    val snowfall_sum: List<Double>,
    val windspeed_10m_max: List<Double>,
    val windgusts_10m_max: List<Double>,
    val precipitation_probability_max: List<Int>
)
data class HourlyData(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val precipitation: List<Double>,
    val snowfall: List<Double>
)

// Our internal UI models for the high-density cards
data class HourItem(val timeLabel: String, val temp: Int, val rain: Double, val snow: Double)
data class ForecastDay(
    val dayName: String,
    val maxTemp: Int,
    val minTemp: Int,
    val description: String,
    val color: Long,
    val totalRain: Double,
    val totalSnow: Double,
    val windMax: Int,
    val gustMax: Int,
    val precipProb: Int,
    val hours: List<HourItem>
)

// --- 3. PERSONAL STATION MODELS (Ecowitt) ---
data class EcowittResponse(val data: EcowittData?, val msg: String)
data class EcowittData(val outdoor: EcowittOutdoor?, val rain: EcowittRain?, val wind: EcowittWind?)
data class EcowittOutdoor(val temperature: EcowittVal?)
data class EcowittRain(val hourly: EcowittVal?)
data class EcowittWind(val wind_speed: EcowittVal?)
data class EcowittVal(val value: String)

// --- 4. API INTERFACES ---
interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getFullForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,snowfall_sum,windspeed_10m_max,windgusts_10m_max,precipitation_probability_max",
        @Query("hourly") hourly: String = "temperature_2m,precipitation,snowfall",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 10,
        @Query("temperature_unit") tempUnit: String,
        @Query("wind_speed_unit") windUnit: String,
        @Query("precipitation_unit") precipUnit: String
    ): ForecastResponse
}

interface EcowittApi {
    @GET("api/v3/device/real_time")
    suspend fun getPwsData(
        @Query("application_key") appKey: String,
        @Query("api_key") apiKey: String,
        @Query("call_back") callBack: String = "all",
        @Query("temp_unitid") tempUnit: Int,
        @Query("wind_speed_unitid") windUnit: Int
    ): EcowittResponse
}

// --- 5. VIEWMODEL ---
class WeatherViewModel : ViewModel() {
    var weatherData = mutableStateOf<WeatherResponse?>(null)
    var tenDayForecast = mutableStateOf<List<ForecastDay>>(emptyList())
    var pwsData = mutableStateOf<EcowittData?>(null)

    private val currentApi = Retrofit.Builder().baseUrl("https://api.openweathermap.org/").addConverterFactory(GsonConverterFactory.create()).build().create(WeatherApi::class.java)
    private val forecastApi = Retrofit.Builder().baseUrl("https://api.open-meteo.com/").addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)
    private val ecowittApi = Retrofit.Builder().baseUrl("https://api.ecowitt.net/").addConverterFactory(GsonConverterFactory.create()).build().create(EcowittApi::class.java)

    fun fetchWeather(lat: Double, lon: Double, ecowittApi: String?, ecowittApp: String?, isMetric: Boolean) {
        viewModelScope.launch {
            try {
                // Fetch OWM Current
                weatherData.value = currentApi.getCurrentWeather(lat, lon, BuildConfig.WEATHER_API_KEY)

                // Fetch Ecowitt (If user provided keys)
                if (!ecowittApi.isNullOrBlank() && !ecowittApp.isNullOrBlank()) {
                    val ecowitt = this@WeatherViewModel.ecowittApi.getPwsData(
                        ecowittApp, ecowittApi,
                        tempUnit = if (isMetric) 1 else 2,
                        windUnit = if (isMetric) 7 else 1
                    )
                    pwsData.value = ecowitt.data
                }

                // Fetch 10-Day Dense Forecast
                val response = forecastApi.getFullForecast(
                    lat, lon,
                    tempUnit = if (isMetric) "celsius" else "fahrenheit",
                    windUnit = if (isMetric) "kmh" else "mph",
                    precipUnit = if (isMetric) "mm" else "inch"
                )

                val dayList = mutableListOf<ForecastDay>()
                for (i in 0 until 10) {
                    val date = response.daily.time[i]

                    // FIXED: Explicitly stating the Int type for the index
                    val dayHours = response.hourly.time.indices.filter { index: Int ->
                        response.hourly.time[index].startsWith(date)
                    }.map { index: Int ->
                        HourItem(
                            timeLabel = response.hourly.time[index].split("T")[1],
                            temp = response.hourly.temperature_2m[index].toInt(),
                            rain = response.hourly.precipitation[index],
                            snow = response.hourly.snowfall[index]
                        )
                    }

                    val (desc, color) = getWeatherAppearance(response.daily.weathercode[i])
                    dayList.add(ForecastDay(
                        dayName = if (i == 0) "Today" else if (i == 1) "Tomorrow" else formatDay(date),
                        maxTemp = response.daily.temperature_2m_max[i].toInt(),
                        minTemp = response.daily.temperature_2m_min[i].toInt(),
                        description = desc,
                        color = color,
                        totalRain = response.daily.precipitation_sum[i],
                        totalSnow = response.daily.snowfall_sum[i],
                        windMax = response.daily.windspeed_10m_max[i].toInt(),
                        gustMax = response.daily.windgusts_10m_max[i].toInt(),
                        precipProb = response.daily.precipitation_probability_max[i],
                        hours = dayHours
                    ))
                }
                tenDayForecast.value = dayList
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun formatDay(dateStr: String) = SimpleDateFormat("EEEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)!!)

    private fun getWeatherAppearance(code: Int): Pair<String, Long> = when (code) {
        0 -> "Sunny" to 0xFFFBC02D
        1, 2, 3 -> "Partly Cloudy" to 0xFF90A4AE
        61, 63, 65 -> "Rainy" to 0xFF1976D2
        71, 73, 75 -> "Snowy" to 0xFFB0BEC5
        95, 96, 99 -> "Thunderstorms" to 0xFF5E35B1
        else -> "Cloudy" to 0xFF607D8B
    }
}