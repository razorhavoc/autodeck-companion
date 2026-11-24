# AutoDeck Companion (Native Android Auto + Jetpack Compose)

A complete, Google Play-compliant native Android & Android Auto application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, and the official **Android for Cars App Library (`androidx.car.app:app:1.7.0`)** under the `POI` (`androidx.car.app.category.POI`) category.

## Implemented Features

1. **Customizable Dashboard & Widgets**: Toggle and reorder 8 widgets in the phone app; persisted in Room and synced to Android Auto's Home tab.
2. **Music & Synchronized Lyrics**: Live `NotificationListenerService` tracks active Spotify/YouTube Music playback with line-by-line LRCLIB synced lyrics on the phone and driver-safe Now Playing summaries on Android Auto.
3. **Islamic Prayer Times & Countdowns**: Official Malaysia JAKIM zones (`api.waktusolat.online`) with global AlAdhan fallback (Method 17), Qibla bearing, daily Room caching, and WorkManager pre-prayer reminders.
4. **Weather & Forecasts**: Live conditions, rain probability, UV index, and 4-day forecast via Open-Meteo (no API key required).
5. **EV Charging Stations**: Interactive Android Auto `PlaceListMapTemplate` with map pins, connector kW ratings, and one-tap navigation handoff (`Open Charge Map` API + sample Klang Valley fallback until key is added).
6. **Malaysia Fuel Prices**: Weekly official RON95, BUDI 95, RON97, and Peninsular/East Malaysia Diesel prices from `api.data.gov.my`.
7. **Sports Scores**: Free ESPN Premier League live/recent scoreboard by default, plus optional `football-data.org` v4 token support.
8. **Calendar Agenda**: Today's upcoming meetings via `CalendarContract` with one-tap Android Auto navigation to event addresses.
9. **Parking Location Saver**: Save current GPS coordinates, Level, Bay, and Notes from phone or car screen with one-tap navigation back to the car.
10. **Themes & Preferences**: 4 Material 3 accent themes, Dark/Light/System mode, Car Profile Name, and English + Bahasa Melayu resources.

## Setup & Build Instructions

1. Install **Android Studio Narwhal (2025.1)** or newer (JDK 17+).
2. In **SDK Manager**, install **Android 16 (API 36)** and **Android Auto Desktop Head Unit Emulator (DHU)** under *SDK Tools > Extras*.
3. Extract `AutoDeckCompanion.zip` and open the `AutoDeckCompanion/` folder in Android Studio.
4. Click **Sync Project with Gradle Files** (Android Studio automatically downloads Gradle `8.11.1` per `gradle/wrapper/gradle-wrapper.properties`).
5. *(Optional)* Copy keys from `local.properties.template` into `local.properties`, or enter your **Open Charge Map** / **Football-Data.org** API keys inside the app's **Settings** tab.

### Command-Line Verification
If running CLI commands before opening Android Studio, bootstrap the wrapper JAR once with a local Gradle installation (`gradle wrapper --gradle-version 8.11.1`), then run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## Testing on Android Auto (DHU & Real Car)

1. Install the debug APK on your phone (`app/build/outputs/apk/debug/app-debug.apk`).
2. On your phone: **Settings > Connected devices > Connection preferences > Android Auto**, tap **Version** 10 times to unlock Developer Mode, then open **Developer settings** and enable **Unknown sources**.
3. For **Desktop Head Unit (DHU)**:
   - In Android Auto Developer settings, tap **Start head unit server**.
   - Run `adb forward tcp:5277 tcp:5277`.
   - Launch `$ANDROID_HOME/extras/google/auto/desktop-head-unit` (or `%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe` on Windows).
4. Open **AutoDeck** in Android Auto to test all tabs, maps, and navigation handoffs.
