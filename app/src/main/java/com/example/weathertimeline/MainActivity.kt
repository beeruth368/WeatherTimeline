package com.example.weathertimeline

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val settingsManager = remember { SettingsManager(context) }
                val weatherViewModel: WeatherViewModel = viewModel()
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                val sharedPref = context.getSharedPreferences("WeatherAppPrefs", Context.MODE_PRIVATE)
                var savedCustomLocName by remember { mutableStateOf(sharedPref.getString("custom_location_name", "") ?: "") }
                var savedCustomLat by remember { mutableStateOf(sharedPref.getFloat("custom_lat", Float.NaN)) }
                var savedCustomLon by remember { mutableStateOf(sharedPref.getFloat("custom_lon", Float.NaN)) }
                var savedAlertsKey by remember { mutableStateOf(sharedPref.getString("alerts_api_key", "") ?: "") }

                val savedApiKey by settingsManager.apiKeyFlow.collectAsState(initial = null)
                val savedAppKey by settingsManager.appKeyFlow.collectAsState(initial = null)
                val isMetric by settingsManager.isMetricFlow.collectAsState(initial = true)

                var showSettings by remember { mutableStateOf(false) }
                var isRefreshing by remember { mutableStateOf(false) }
                var showLocationPrompt by remember { mutableStateOf(false) }
                val pullState = rememberPullToRefreshState()

                val speedUnit = if (isMetric) "km/h" else "mph"
                val rainUnit = if (isMetric) "mm" else "in"
                val snowUnit = if (isMetric) "cm" else "in"

                val executeWeatherFetch = {
                    scope.launch {
                        isRefreshing = true
                        showLocationPrompt = false

                        if (!savedCustomLat.isNaN() && !savedCustomLon.isNaN()) {
                            weatherViewModel.fetchWeather(savedCustomLat.toDouble(), savedCustomLon.toDouble(), savedApiKey, savedAppKey, isMetric, savedAlertsKey) { isRefreshing = false }
                        } else {
                            fetchCurrentLocation(fusedLocationClient) { lat, lon ->
                                if (lat != null && lon != null) {
                                    weatherViewModel.fetchWeather(lat, lon, savedApiKey, savedAppKey, isMetric, savedAlertsKey) { isRefreshing = false }
                                } else {
                                    showLocationPrompt = true
                                    isRefreshing = false
                                }
                            }
                        }
                    }
                }

                val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    executeWeatherFetch()
                }

                LaunchedEffect(Unit) {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B263B)))).systemBarsPadding()) {
                        Column {
                            HeaderRow(onSettingsClick = { showSettings = true })
                            AlertBanner(weatherViewModel.activeAlerts.value)

                            if (showLocationPrompt) {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(64.dp))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Location Unavailable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Please enable GPS or manually enter your city in the Settings menu.", color = Color.White.copy(0.7f), textAlign = TextAlign.Center, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(onClick = { showSettings = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600), contentColor = Color.Black)) {
                                            Text("Open Settings", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                PullToRefreshBox(isRefreshing = isRefreshing, state = pullState, onRefresh = { executeWeatherFetch() }) {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                                        item { CurrentConditionsCard(weatherViewModel) }
                                        item { EcowittCard(weatherViewModel, speedUnit) }
                                        items(weatherViewModel.tenDayForecast.value) { day -> TimelineItem(day, speedUnit, rainUnit, snowUnit) }
                                    }
                                }
                            }
                        }
                    }

                    if (showSettings) {
                        SettingsDialog(
                            cApi = savedApiKey ?: "", cApp = savedAppKey ?: "", cMetric = isMetric,
                            cLocName = savedCustomLocName, cLat = savedCustomLat, cLon = savedCustomLon,
                            cAlertsKey = savedAlertsKey,
                            viewModel = weatherViewModel, onDismiss = { showSettings = false },
                            onSave = { api, app, metric, locName, lat, lon, alertsKey ->
                                scope.launch {
                                    settingsManager.saveSettings(api, app, metric)
                                    sharedPref.edit()
                                        .putString("custom_location_name", locName)
                                        .putFloat("custom_lat", lat?.toFloat() ?: Float.NaN)
                                        .putFloat("custom_lon", lon?.toFloat() ?: Float.NaN)
                                        .putString("alerts_api_key", alertsKey)
                                        .apply()

                                    savedCustomLocName = locName
                                    savedCustomLat = lat?.toFloat() ?: Float.NaN
                                    savedCustomLon = lon?.toFloat() ?: Float.NaN
                                    savedAlertsKey = alertsKey

                                    showSettings = false
                                    executeWeatherFetch()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(client: com.google.android.gms.location.FusedLocationProviderClient, onFound: (Double?, Double?) -> Unit) {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnCompleteListener { task ->
            val loc = task.result
            if (loc != null) onFound(loc.latitude, loc.longitude) else onFound(null, null)
        }
    }
}

@Composable
fun AlertBanner(alerts: List<WeatherAlert>) {
    if (alerts.isEmpty()) return
    alerts.forEach { alert ->
        val isSevere = alert.severity.equals("Severe", true) || alert.severity.equals("Extreme", true) || alert.title.contains("Warning", true)
        val bgColor = if (isSevere) Color(0xFFD32F2F) else Color(0xFFFFA000)

        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(bgColor)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(alert.title, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                    Text(alert.description.take(80) + "...", color = Color.White.copy(0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HeaderRow(onSettingsClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Weather Timeline", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null, tint = Color.White) }
    }
}

@Composable
fun EcowittCard(viewModel: WeatherViewModel, windUnit: String) {
    val data = viewModel.pwsData.value
    val time = viewModel.lastUpdatedPws.value
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(Color(0xFF2E7D32)), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BACKYARD STATION", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.6f))
                if (time.isNotEmpty()) Text("Synced: $time", fontSize = 10.sp, color = Color.White.copy(0.4f))
            }
            if (data == null) {
                Text("Waiting for PWS Data...", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${data.outdoor?.temperature?.value ?: "--"}°", fontSize = 52.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Humidity: ${data.outdoor?.humidity?.value ?: "--"}%", color = Color.White, fontSize = 14.sp)
                        Text("Wind: ${data.wind?.wind_speed?.value ?: "0"} $windUnit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val solarVal = data.solar_and_uv?.solar?.value?.toFloatOrNull() ?: 0f
                val progress = (solarVal / 1000f).coerceIn(0f, 1f)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sun Intensity", fontSize = 11.sp, color = Color.White.copy(0.7f))
                        Text("${solarVal.toInt()} W/m²", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(0.2f))) {
                        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFFFFD600), Color(0xFFFF6D00)))))
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(day: ForecastDay, windUnit: String, rainUnit: String, snowUnit: String) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { expanded = !expanded }) {
        Canvas(modifier = Modifier.width(40.dp).height(if (expanded) 320.dp else 140.dp)) {
            drawLine(Color.White.copy(0.3f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
            drawCircle(Color.White, 6.dp.toPx(), Offset(size.width / 2, size.height / 2))
            drawCircle(Color(day.color), 4.dp.toPx(), Offset(size.width / 2, size.height / 2))
        }
        Card(modifier = Modifier.weight(1f).padding(vertical = 8.dp), colors = CardDefaults.cardColors(Color(day.color).copy(alpha = 0.95f))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day.dayName, fontWeight = FontWeight.Black, color = Color.White, fontSize = 20.sp)
                    Text("${day.maxTemp}° / ${day.minTemp}°", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                }
                Text(day.description.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.7f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Wind: ${day.windMax} $windUnit", fontSize = 12.sp, color = Color.White)
                        Text("Gust: ${day.gustMax} $windUnit", fontSize = 12.sp, color = Color.White.copy(0.6f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Precip: ${day.precipProb}%", fontSize = 12.sp, color = Color.White)
                        if (day.totalSnow > 0) Text("❄ ${day.totalSnow} $snowUnit", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        else if (day.totalRain > 0) Text("💧 ${day.totalRain} $rainUnit", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.Cyan)
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(0.2f))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(day.hours) { hr ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(hr.timeLabel, fontSize = 11.sp, color = Color.White.copy(0.8f))
                                    Text("${hr.temp}°", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    if (hr.snow > 0) Text("${hr.snow}", fontSize = 10.sp, color = Color.White)
                                    else if (hr.rain > 0) Text("${hr.rain}", fontSize = 10.sp, color = Color.Cyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentConditionsCard(viewModel: WeatherViewModel) {
    val data = viewModel.weatherData.value ?: return
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(Color(0xFFFFD600)), elevation = CardDefaults.cardElevation(12.dp)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(data.name.uppercase(), fontWeight = FontWeight.Black, color = Color.Black, fontSize = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${data.main.temp.toInt()}°", fontSize = 80.sp, fontWeight = FontWeight.Black, color = Color.Black)
                AsyncImage(model = "https://openweathermap.org/img/wn/${data.weather[0].icon}@4x.png", contentDescription = null, modifier = Modifier.size(110.dp))
            }
        }
    }
}

@Composable
fun SettingsDialog(
    cApi: String, cApp: String, cMetric: Boolean,
    cLocName: String, cLat: Float, cLon: Float,
    cAlertsKey: String,
    viewModel: WeatherViewModel, onDismiss: () -> Unit,
    onSave: (String, String, Boolean, String, Double?, Double?, String) -> Unit
) {
    var api by remember { mutableStateOf(cApi) }
    var app by remember { mutableStateOf(cApp) }
    var metric by remember { mutableStateOf(cMetric) }
    var alertsKey by remember { mutableStateOf(cAlertsKey) }

    var locQuery by remember { mutableStateOf(cLocName) }
    var selectedLat by remember { mutableStateOf<Double?>(if (cLat.isNaN()) null else cLat.toDouble()) }
    var selectedLon by remember { mutableStateOf<Double?>(if (cLon.isNaN()) null else cLon.toDouble()) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(locQuery) {
        if (locQuery.length > 2 && selectedLat == null) {
            delay(600)
            viewModel.searchLocation(locQuery)
        } else {
            viewModel.locationSearchResults.value = emptyList()
        }
    }

    val isLocationValid = locQuery.isBlank() || selectedLat != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Settings", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                Text("Location Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = locQuery,
                    onValueChange = {
                        locQuery = it
                        selectedLat = null
                        selectedLon = null
                    },
                    label = { Text("Manual City (e.g. Toronto)") },
                    placeholder = { Text("Leave blank for GPS") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (viewModel.locationSearchResults.value.isNotEmpty() && selectedLat == null && locQuery.isNotBlank()) {
                    Card(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp), colors = CardDefaults.cardColors(Color.DarkGray)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            viewModel.locationSearchResults.value.forEach { result ->
                                Text(
                                    text = result.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        locQuery = result.displayName
                                        selectedLat = result.latitude
                                        selectedLon = result.longitude
                                        viewModel.locationSearchResults.value = emptyList()
                                    }.padding(12.dp)
                                )
                                HorizontalDivider(color = Color.Gray.copy(0.5f))
                            }
                        }
                    }
                }

                if (!isLocationValid && locQuery.isNotBlank()) {
                    Text("Please select a specific location from the dropdown.", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.Gray.copy(0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                Text("Weather Alerts", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alertsKey,
                    onValueChange = { alertsKey = it },
                    label = { Text("WeatherAPI.com Key") },
                    placeholder = { Text("Required for Active Alerts") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Get a free key at WeatherAPI.com", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, top = 2.dp))

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                Text("Ecowitt Station", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = api, onValueChange = { api = it; testResult = null }, label = { Text("Ecowitt API Key") }, isError = testResult == false, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = app, onValueChange = { app = it; testResult = null }, label = { Text("Ecowitt App Key") }, isError = testResult == false, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = metric, onCheckedChange = { metric = it })
                    Text("Use Metric (C, km/h)")
                }

                if (testResult == false) { Text("Connection failed. Check keys.", color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !testing && isLocationValid,
                onClick = {
                    scope.launch {
                        testing = true
                        if (api.isNotBlank() && app.isNotBlank()) {
                            val success = viewModel.testEcowittKeys(api, app)
                            testResult = success
                            if (success) onSave(api, app, metric, if (locQuery.isBlank()) "" else locQuery, selectedLat, selectedLon, alertsKey)
                        } else {
                            onSave(api, app, metric, if (locQuery.isBlank()) "" else locQuery, selectedLat, selectedLon, alertsKey)
                        }
                        testing = false
                    }
                }) { if (testing) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Save & Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}