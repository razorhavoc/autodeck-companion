package com.autodeck.companion.model

enum class WidgetType(val title: String, val subtitle: String) {
    PRAYER("Prayer Countdown", "JAKIM prayer times & next azan"),
    WEATHER("Weather", "Live conditions, rain & UV"),
    MUSIC("Now Playing", "Active media & phone synced lyrics"),
    EV_CHARGERS("EV Chargers", "Nearby charging stations on map"),
    FUEL_PRICES("MY Fuel Prices", "Weekly RON95, RON97 & Diesel"),
    PARKING("Parking Saver", "Saved bay, level & car GPS"),
    SPORTS("Sports Scores", "Live & recent football scores"),
    CALENDAR("Calendar", "Today's upcoming schedule")
}

enum class AppThemeOption(val displayName: String) {
    MIDNIGHT_CARBON("Midnight Carbon"),
    EMERALD_CRESCENT("Emerald Crescent"),
    OCEAN_DRIVE("Ocean Drive"),
    SUNSET_AMBER("Sunset Amber")
}

enum class DarkModePreference(val displayName: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

data class DashboardWidgetConfig(
    val type: WidgetType,
    val enabled: Boolean,
    val sortOrder: Int
)

data class UserPreferences(
    val jakimZone: String = "SGR01",
    val carProfileName: String = "My Car",
    val themeOption: AppThemeOption = AppThemeOption.EMERALD_CRESCENT,
    val darkMode: DarkModePreference = DarkModePreference.DARK,
    val azanReminderEnabled: Boolean = true,
    val openChargeMapApiKey: String = "",
    val footballDataApiKey: String = ""
)

data class PrayerTimesDay(
    val zone: String,
    val dateLabel: String,
    val fajr: String,
    val syuruk: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val source: String
)

data class NextPrayerInfo(
    val name: String,
    val timeText: String,
    val remainingText: String
)

data class WeatherSummary(
    val temperatureC: Double,
    val apparentTempC: Double,
    val conditionLabel: String,
    val humidityPercent: Int,
    val rainProbabilityPercent: Int,
    val uvIndexMax: Double,
    val windSpeedKmh: Double,
    val dailyForecast: List<DailyForecastItem>
)

data class DailyForecastItem(
    val date: String,
    val minTempC: Double,
    val maxTempC: Double,
    val rainProbPercent: Int,
    val conditionLabel: String
)

data class FuelPriceInfo(
    val effectiveDate: String,
    val ron95: Double,
    val ron95Budi: Double?,
    val ron95Skps: Double?,
    val ron97: Double,
    val dieselPeninsular: Double,
    val dieselEastMalaysia: Double?
)

data class EvChargerStation(
    val id: Long,
    val name: String,
    val operatorName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val maxPowerKw: Double,
    val connectorSummary: String,
    val isSampleFallback: Boolean = false
)

data class SportsMatch(
    val id: String,
    val league: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val status: String
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val location: String
)

data class ParkingSpot(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val level: String,
    val bay: String,
    val notes: String,
    val savedAtMillis: Long
)

data class NowPlayingTrack(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val packageName: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdatedElapsedMs: Long = 0L
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsResult(
    val trackTitle: String,
    val artistName: String,
    val syncedLines: List<LyricLine>,
    val plainLyrics: String,
    val source: String
)
