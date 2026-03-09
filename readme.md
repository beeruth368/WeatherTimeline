# Weather Timeline - High Density Personal Weather Dashboard

A modern Android weather application built with Jetpack Compose that combines general meteorological data with local Personal Weather Station (PWS) hardware. Inspired by the legendary Weather Timeline aesthetic.

## Features

- **Multi-Source Data:** Pulls from OpenWeatherMap (Current), Open-Meteo (10-Day Forecast), and Ecowitt.net (Personal Hardware).
- **High-Density Timeline:** Detailed daily cards showing Max Wind, Gusts, and Precipitation Probability.
- **Dynamic Unit Switching:** Users can toggle between Metric (Celsius/kmh/mm) and Imperial (Fahrenheit/mph/in) globally.
- **Expandable Hourly View:** Tap any day to see a 24-hour granular breakdown of temperature and rain/snow accumulation.
- **Security First:** Utilizes `local.properties` and `BuildConfig` to keep API keys private, and Android DataStore to store user-specific hardware keys securely on-device.

## Tech Stack

- **UI:** Jetpack Compose (Material 3)
- **Networking:** Retrofit & Gson
- **Concurrency:** Kotlin Coroutines & Flow
- **Persistence:** Android DataStore Preferences
- **Images:** Coil (Asynchronous Image Loading)
- **Location:** Google Play Services Fused Location Provider

## Getting Started

1. **Obtain API Keys:**
    - [OpenWeatherMap API Key](https://openweathermap.org/api)
    - [Ecowitt API & Application Keys](https://www.ecowitt.net/user/api)

2. **Configure Security:**
   Add the following to your `local.properties` file:
   ```properties
   WEATHER_API_KEY=your_key_here