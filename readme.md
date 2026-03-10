# Weather Timeline

A hyper-local, Jetpack Compose weather application designed for real-time accuracy. It merges global weather models with hyper-local Personal Weather Station (PWS) data to create a seamless, expandable 10-day forecast.

## Features
* **Hyper-Local PWS Integration:** Connects directly to Ecowitt personal weather stations via the v3 API (`device/list` -> `real_time` handshake) for backyard-accurate temperature, humidity, wind, and solar radiation.
* **Sun Intensity Meter:** A dynamic visual progress bar mapping solar radiation ($W/m^2$).
* **Expandable 10-Day Timeline:** High-contrast daily cards that expand to reveal a horizontal scroll of hourly temperature and precipitation breakdowns.
* **Smart Location Handling:** Utilizes Google's `FusedLocationClient` for GPS, with a built-in Open-Meteo Geocoding fallback. Users can manually search and save specific cities via a debounced autocomplete dropdown.
* **Midnight Blue UI:** A fully custom, edge-to-edge Jetpack Compose interface with system bar padding and seamless Pull-to-Refresh coordination.

## Tech Stack
* **UI:** Kotlin, Jetpack Compose, Material 3
* **Networking:** Retrofit, Gson, Kotlin Coroutines
* **Location:** Google Play Services Location, Geocoding API
* **Local Storage:** Android SharedPreferences

## API Requirements
To run this application fully, you will need to add your keys to the in-app Settings menu:
1. **OpenWeatherMap API:** Powers the current conditions card.
2. **Ecowitt API & App Keys:** Powers the Backyard Station card.
3. **WeatherAPI.com:** Powers the live alert banner.
4. *Open-Meteo API:* Used for 10-Day forecasting and Geocoding (Free, no key required).