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

// --- DATA MODELS ---
data class WeatherResponse(val main: MainData, val weather: List<WeatherData>, val name: String)
data class MainData(val temp: Double)
data class WeatherData(val main: String, val description: String, val icon: String)

data class ForecastResponse(val daily: DailyData, val hourly: HourlyData)
data class DailyData(
    val time: List<String>, val weathercode: List<Int>, val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>, val precipitation_sum: List<Double>,
    val snowfall_sum: List<Double>, val windspeed_10m_max: List<Double>,
    val windgusts_10m_max: List<Double>, val precipitation_probability_max: List<Int>
)
data class HourlyData(val time: List<String>, val temperature_2m: List<Double>, val precipitation: List<Double>, val snowfall: List<Double>)

// --- WEATHERAPI.COM ALERT MODELS (Bulletproofed) ---
data class WeatherApiAlertsResponse(val alerts: WeatherApiAlertsData?)
data class WeatherApiAlertsData(val alert: List<WeatherApiAlertItem>?)
data class WeatherApiAlertItem(val event: String?, val headline: String?, val desc: String?, val severity: String?)
data class WeatherAlert(val title: String, val description: String, val severity: String)

data class ForecastDay(
    val dayName: String, val maxTemp: Int, val minTemp: Int, val description: String,
    val color: Long, val totalRain: Double, val totalSnow: Double, val windMax: Int,
    val gustMax: Int, val precipProb: Int, val hours: List<HourItem>
)
data class HourItem(val timeLabel: String, val temp: Int, val rain: Double, val snow: Double)

// --- GEOCODING MODELS ---
data class GeocodingResponse(val results: List<GeocodingResult>?)
data class GeocodingResult(val latitude: Double, val longitude: Double, val name: String, val admin1: String?, val country: String?) {
    val displayName: String get() = listOfNotNull(name, admin1, country).joinToString(", ")
}

// --- ECOWITT MODELS ---
data class EcowittListResponse(val data: EcowittListData?, val code: Int, val msg: String)
data class EcowittListData(val list: List<EcowittDeviceBrief>?)
data class EcowittDeviceBrief(val mac: String)
data class EcowittRealTimeResponse(val data: EcowittRealTimeData?, val code: Int, val msg: String)
data class EcowittRealTimeData(val outdoor: EcowittOutdoor?, val rain: EcowittRain?, val wind: EcowittWind?, val solar_and_uv: EcowittSolar?)
data class EcowittOutdoor(val temperature: EcowittVal?, val humidity: EcowittVal?)
data class EcowittRain(val hourly: EcowittVal?)
data class EcowittWind(val wind_speed: EcowittVal?)
data class EcowittSolar(val solar: EcowittVal?, val uv: EcowittVal?)
data class EcowittVal(val value: String)

// --- INTERFACES ---
interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(@Query("lat") lat: Double, @Query("lon") lon: Double, @Query("appid") apiKey: String, @Query("units") units: String = "metric"): WeatherResponse
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getFullForecast(@Query("latitude") lat: Double, @Query("longitude") lon: Double, @Query("daily") d: String = "weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,snowfall_sum,windspeed_10m_max,windgusts_10m_max,precipitation_probability_max", @Query("hourly") h: String = "temperature_2m,precipitation,snowfall", @Query("timezone") z: String = "auto", @Query("forecast_days") f: Int = 10, @Query("temperature_unit") t: String, @Query("wind_speed_unit") w: String, @Query("precipitation_unit") p: String): ForecastResponse
}

interface WeatherApiDotCom {
    @GET("v1/forecast.json")
    suspend fun getAlerts(@Query("key") apiKey: String, @Query("q") query: String, @Query("days") days: Int = 1, @Query("alerts") alerts: String = "yes", @Query("aqi") aqi: String = "no"): WeatherApiAlertsResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun searchCity(@Query("name") name: String, @Query("count") count: Int = 5): GeocodingResponse
}

interface EcowittApi {
    @GET("api/v3/device/list")
    suspend fun getDeviceList(@Query("application_key") appKey: String, @Query("api_key") apiKey: String): EcowittListResponse
    @GET("api/v3/device/real_time")
    suspend fun getRealTimeData(@Query("application_key") appKey: String, @Query("api_key") apiKey: String, @Query("mac") mac: String, @Query("call_back") cb: String = "all", @Query("temp_unitid") t: Int, @Query("wind_speed_unitid") w: Int): EcowittRealTimeResponse
}

class WeatherViewModel : ViewModel() {
    var weatherData = mutableStateOf<WeatherResponse?>(null)
    var tenDayForecast = mutableStateOf<List<ForecastDay>>(emptyList())
    var pwsData = mutableStateOf<EcowittRealTimeData?>(null)
    var activeAlerts = mutableStateOf<List<WeatherAlert>>(emptyList())
    var lastUpdatedPws = mutableStateOf("")
    var locationSearchResults = mutableStateOf<List<GeocodingResult>>(emptyList())

    private val currentApi = Retrofit.Builder().baseUrl("https://api.openweathermap.org/").addConverterFactory(GsonConverterFactory.create()).build().create(WeatherApi::class.java)
    private val forecastApi = Retrofit.Builder().baseUrl("https://api.open-meteo.com/").addConverterFactory(GsonConverterFactory.create()).build().create(OpenMeteoApi::class.java)
    private val geocodingApi = Retrofit.Builder().baseUrl("https://geocoding-api.open-meteo.com/").addConverterFactory(GsonConverterFactory.create()).build().create(GeocodingApi::class.java)
    private val ecowittApi = Retrofit.Builder().baseUrl("https://api.ecowitt.net/").addConverterFactory(GsonConverterFactory.create()).build().create(EcowittApi::class.java)
    private val alertsApi = Retrofit.Builder().baseUrl("https://api.weatherapi.com/").addConverterFactory(GsonConverterFactory.create()).build().create(WeatherApiDotCom::class.java)

    fun searchLocation(query: String) {
        viewModelScope.launch {
            try {
                val res = geocodingApi.searchCity(query, 5)
                locationSearchResults.value = res.results ?: emptyList()
            } catch (e: Exception) { locationSearchResults.value = emptyList() }
        }
    }

    suspend fun testEcowittKeys(api: String, app: String): Boolean {
        return try {
            val response = ecowittApi.getDeviceList(app, api)
            response.code == 0 && response.data?.list?.isNotEmpty() == true
        } catch (e: Exception) { false }
    }

    fun fetchWeather(lat: Double, lon: Double, eApi: String?, eApp: String?, isMetric: Boolean, alertsKey: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // 1. Current Weather
                weatherData.value = currentApi.getCurrentWeather(lat, lon, BuildConfig.WEATHER_API_KEY)

                // 2. Ecowitt
                if (!eApi.isNullOrBlank() && !eApp.isNullOrBlank()) {
                    try {
                        val list = ecowittApi.getDeviceList(eApp, eApi)
                        val mac = list.data?.list?.firstOrNull()?.mac
                        if (mac != null) {
                            val realTime = ecowittApi.getRealTimeData(eApp, eApi, mac, t = if (isMetric) 1 else 2, w = if (isMetric) 7 else 1)
                            if (realTime.code == 0) {
                                pwsData.value = realTime.data
                                lastUpdatedPws.value = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 3. WeatherAPI Alerts (with Debug Logging)
                if (!alertsKey.isNullOrBlank()) {
                    try {
                        println("DEBUG: Checking WeatherAPI for alerts at $lat,$lon...")
                        val alertRes = alertsApi.getAlerts(alertsKey, "$lat,$lon")
                        val fetchedAlerts = alertRes.alerts?.alert

                        if (fetchedAlerts != null && fetchedAlerts.isNotEmpty()) {
                            println("DEBUG: SUCCESS! Found ${fetchedAlerts.size} active alerts.")
                            activeAlerts.value = fetchedAlerts.map {
                                WeatherAlert(
                                    title = it.event ?: it.headline ?: "Weather Alert",
                                    description = it.desc ?: "Please check local weather services for details.",
                                    severity = it.severity ?: "Unknown"
                                )
                            }
                        } else {
                            println("DEBUG: WeatherAPI connected successfully, but reports 0 active alerts for these exact coordinates.")
                            activeAlerts.value = emptyList()
                        }
                    } catch (e: Exception) {
                        println("DEBUG: WEATHERAPI CRASH: ${e.message}")
                        e.printStackTrace()
                        activeAlerts.value = emptyList()
                    }
                } else {
                    activeAlerts.value = emptyList()
                }

                // 4. Forecast
                val res = forecastApi.getFullForecast(lat, lon, t = if (isMetric) "celsius" else "fahrenheit", w = if (isMetric) "kmh" else "mph", p = if (isMetric) "mm" else "inch")
                val dayList = mutableListOf<ForecastDay>()
                for (i in 0 until 10) {
                    val date = res.daily.time[i]
                    val hours = res.hourly.time.indices.filter { res.hourly.time[it].startsWith(date) }.map {
                        HourItem(res.hourly.time[it].split("T")[1], res.hourly.temperature_2m[it].toInt(), res.hourly.precipitation[it], res.hourly.snowfall[it])
                    }
                    val (desc, color) = getWeatherAppearance(res.daily.weathercode[i])
                    dayList.add(ForecastDay(if (i == 0) "Today" else if (i == 1) "Tomorrow" else formatDay(date), res.daily.temperature_2m_max[i].toInt(), res.daily.temperature_2m_min[i].toInt(), desc, color, res.daily.precipitation_sum[i], res.daily.snowfall_sum[i], res.daily.windspeed_10m_max[i].toInt(), res.daily.windgusts_10m_max[i].toInt(), res.daily.precipitation_probability_max[i], hours))
                }
                tenDayForecast.value = dayList
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onComplete()
            }
        }
    }

    private fun formatDay(dateStr: String) = SimpleDateFormat("EEEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)!!)

    private fun getWeatherAppearance(code: Int): Pair<String, Long> = when (code) {
        0 -> "Sunny" to 0xFFFBC02D
        1, 2, 3 -> "Partly Cloudy" to 0xFF90A4AE
        61, 63, 65 -> "Rainy" to 0xFF1976D2
        71, 73, 75 -> "Snowy" to 0xFFB0BEC5
        95, 96, 99 -> "Stormy" to 0xFF5E35B1
        else -> "Cloudy" to 0xFF607D8B
    }
}