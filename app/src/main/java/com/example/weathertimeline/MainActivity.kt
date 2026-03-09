package com.example.weathertimeline

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class) // Clears the "Experimental API" error
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val settingsManager = remember { SettingsManager(context) }
                val weatherViewModel: WeatherViewModel = viewModel()
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                val savedApiKey by settingsManager.apiKeyFlow.collectAsState(initial = null)
                val savedAppKey by settingsManager.appKeyFlow.collectAsState(initial = null)
                val isMetric by settingsManager.isMetricFlow.collectAsState(initial = true)
                var showSettings by remember { mutableStateOf(false) }
                var isRefreshing by remember { mutableStateOf(false) }

                val speedUnit = if (isMetric) "km/h" else "mph"
                val rainUnit = if (isMetric) "mm" else "in"
                val snowUnit = if (isMetric) "cm" else "in"

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.any { it }) {
                        fetchCurrentLocation(fusedLocationClient) { lat, lon ->
                            weatherViewModel.fetchWeather(lat, lon, savedApiKey, savedAppKey, isMetric)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weather Timeline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    fetchCurrentLocation(fusedLocationClient) { lat, lon ->
                                        weatherViewModel.fetchWeather(lat, lon, savedApiKey, savedAppKey, isMetric)
                                    }
                                    isRefreshing = false
                                }
                            }
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item { CurrentConditionsCard(viewModel = weatherViewModel) }
                                item { EcowittCard(weatherViewModel.pwsData.value, speedUnit) }
                                items(weatherViewModel.tenDayForecast.value) { day ->
                                    TimelineItem(day, speedUnit, rainUnit, snowUnit)
                                }
                            }
                        }
                    }

                    if (showSettings) {
                        SettingsDialog(
                            currentApi = savedApiKey ?: "",
                            currentApp = savedAppKey ?: "",
                            currentMetric = isMetric,
                            onDismiss = { showSettings = false },
                            onSave = { api, app, metric ->
                                scope.launch {
                                    settingsManager.saveSettings(api, app, metric)
                                    showSettings = false
                                    fetchCurrentLocation(fusedLocationClient) { lat, lon ->
                                        weatherViewModel.fetchWeather(lat, lon, api, app, metric)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(client: com.google.android.gms.location.FusedLocationProviderClient, onFound: (Double, Double) -> Unit) {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { it?.let { onFound(it.latitude, it.longitude) } }
    }
}

@Composable
fun SettingsDialog(currentApi: String, currentApp: String, currentMetric: Boolean, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var api by remember { mutableStateOf(currentApi) }
    var app by remember { mutableStateOf(currentApp) }
    var metric by remember { mutableStateOf(currentMetric) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Settings") },
        text = {
            Column {
                OutlinedTextField(value = api, onValueChange = { api = it }, label = { Text("Ecowitt API Key") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = app, onValueChange = { app = it }, label = { Text("Ecowitt App Key") })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                    Checkbox(checked = metric, onCheckedChange = { metric = it })
                    Text("Use Metric Units")
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(api, app, metric) }) { Text("Save") } }
    )
}

@Composable
fun TimelineItem(day: ForecastDay, windUnit: String, rainUnit: String, snowUnit: String) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { expanded = !expanded }) {
        Canvas(modifier = Modifier.width(40.dp).height(if (expanded) 260.dp else 120.dp)) {
            drawLine(Color.White, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
            drawCircle(Color.White, 6.dp.toPx(), Offset(size.width / 2, size.height / 2))
            drawCircle(Color(day.color), 4.dp.toPx(), Offset(size.width / 2, size.height / 2))
        }
        Card(modifier = Modifier.weight(1f).padding(vertical = 6.dp), colors = CardDefaults.cardColors(Color(day.color))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day.dayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Text("${day.maxTemp}° / ${day.minTemp}°", fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(day.description.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.8f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Wind: ${day.windMax} $windUnit", fontSize = 11.sp, color = Color.White)
                        Text("Gust: ${day.gustMax} $windUnit", fontSize = 11.sp, color = Color.White.copy(0.6f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Chance: ${day.precipProb}%", fontSize = 11.sp, color = Color.White)
                        if (day.totalSnow > 0) Text("❄ ${day.totalSnow} $snowUnit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        else if (day.totalRain > 0) Text("💧 ${day.totalRain} $rainUnit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Cyan)
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.2f))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(day.hours) { hr ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(hr.timeLabel, fontSize = 10.sp, color = Color.White)
                                    Text("${hr.temp}°", fontWeight = FontWeight.Bold, color = Color.White)
                                    if (hr.snow > 0) Text("${hr.snow}", fontSize = 9.sp, color = Color.White)
                                    else if (hr.rain > 0) Text("${hr.rain}", fontSize = 9.sp, color = Color.Cyan)
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
fun EcowittCard(data: EcowittData?, unit: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(Color(0xFF2E7D32))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("LOCAL STATION DATA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.7f))
            if (data == null) {
                Text("No Ecowitt Keys Found", color = Color.White, fontSize = 14.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${data.outdoor?.temperature?.value}°", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Wind: ${data.wind?.wind_speed?.value} $unit", color = Color.White, fontSize = 13.sp)
                        Text("Rain: ${data.rain?.hourly?.value} mm", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentConditionsCard(viewModel: WeatherViewModel) {
    val data = viewModel.weatherData.value ?: return
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(Color(0xFFFBC02D))) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(data.name.uppercase(), fontWeight = FontWeight.Black, color = Color.Black, fontSize = 22.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${data.main.temp.toInt()}°", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                AsyncImage(model = "https://openweathermap.org/img/wn/${data.weather[0].icon}@4x.png", contentDescription = null, modifier = Modifier.size(100.dp))
            }
            Text(data.weather[0].description.uppercase(), color = Color.Black.copy(0.6f), fontWeight = FontWeight.Bold)
        }
    }
}