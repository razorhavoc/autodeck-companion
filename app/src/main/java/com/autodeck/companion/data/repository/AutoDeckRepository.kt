package com.autodeck.companion.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.autodeck.companion.BuildConfig
import com.autodeck.companion.data.local.AutoDeckDatabase
import com.autodeck.companion.data.local.CachedFuelEntity
import com.autodeck.companion.data.local.CachedPrayerEntity
import com.autodeck.companion.data.local.ParkingSpotEntity
import com.autodeck.companion.data.local.UserPreferencesStore
import com.autodeck.companion.data.local.WidgetConfigEntity
import com.autodeck.companion.data.remote.NetworkModule
import com.autodeck.companion.model.CalendarEvent
import com.autodeck.companion.model.DailyForecastItem
import com.autodeck.companion.model.DashboardWidgetConfig
import com.autodeck.companion.model.EvChargerStation
import com.autodeck.companion.model.FuelPriceInfo
import com.autodeck.companion.model.LyricsResult
import com.autodeck.companion.model.ParkingSpot
import com.autodeck.companion.model.PrayerTimesDay
import com.autodeck.companion.model.SportsMatch
import com.autodeck.companion.model.UserPreferences
import com.autodeck.companion.model.WeatherSummary
import com.autodeck.companion.model.WidgetType
import com.autodeck.companion.service.LocationProvider
import com.autodeck.companion.util.DistanceCalculator
import com.autodeck.companion.util.LrcParser
import com.autodeck.companion.util.WeatherCodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AutoDeckRepository(
    private val context: Context,
    private val database: AutoDeckDatabase,
    val preferencesStore: UserPreferencesStore,
    val locationProvider: LocationProvider
) {
    private val widgetDao = database.widgetConfigDao()
    private val parkingDao = database.parkingSpotDao()
    private val cacheDao = database.cachedDataDao()

    val widgetConfigsFlow: Flow<List<DashboardWidgetConfig>> = widgetDao.observeAll().map { rows ->
        if (rows.isEmpty()) defaultWidgets()
        else rows.mapNotNull { entity ->
            runCatching { WidgetType.valueOf(entity.widgetKey) }.getOrNull()?.let { type ->
                DashboardWidgetConfig(type = type, enabled = entity.enabled, sortOrder = entity.sortOrder)
            }
        }
    }

    val latestParkingFlow: Flow<ParkingSpot?> = parkingDao.observeLatest().map { it?.toDomain() }
    val parkingHistoryFlow: Flow<List<ParkingSpot>> = parkingDao.observeHistory().map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaultWidgetsSeeded() = withContext(Dispatchers.IO) {
        if (widgetDao.getAll().isEmpty()) {
            widgetDao.upsertAll(defaultWidgets().map {
                WidgetConfigEntity(it.type.name, it.enabled, it.sortOrder)
            })
        }
    }

    suspend fun getWidgetsSnapshot(): List<DashboardWidgetConfig> = withContext(Dispatchers.IO) {
        ensureDefaultWidgetsSeeded()
        widgetDao.getAll().mapNotNull { entity ->
            runCatching { WidgetType.valueOf(entity.widgetKey) }.getOrNull()?.let {
                DashboardWidgetConfig(it, entity.enabled, entity.sortOrder)
            }
        }
    }

    suspend fun saveWidgets(widgets: List<DashboardWidgetConfig>) = withContext(Dispatchers.IO) {
        widgetDao.upsertAll(
            widgets.mapIndexed { idx, item ->
                WidgetConfigEntity(item.type.name, item.enabled, idx)
            }
        )
    }

    suspend fun getPreferencesSnapshot(): UserPreferences = preferencesStore.preferencesFlow.first()

    suspend fun saveParkingSpot(level: String, bay: String, notes: String): ParkingSpot = withContext(Dispatchers.IO) {
        val coords = locationProvider.getCurrentCoordinates()
        val now = System.currentTimeMillis()
        val resolvedLevel = level.trim().ifEmpty { "G" }
        val resolvedBay = bay.trim().ifEmpty { "Saved GPS" }
        val resolvedNotes = notes.trim()
        val id = parkingDao.insert(
            ParkingSpotEntity(
                latitude = coords.latitude,
                longitude = coords.longitude,
                level = resolvedLevel,
                bay = resolvedBay,
                notes = resolvedNotes,
                savedAtMillis = now
            )
        )
        ParkingSpot(id, coords.latitude, coords.longitude, resolvedLevel, resolvedBay, resolvedNotes, now)
    }

    suspend fun deleteParkingSpot(id: Long) = withContext(Dispatchers.IO) {
        parkingDao.deleteById(id)
    }

    suspend fun getLatestParkingSnapshot(): ParkingSpot? = withContext(Dispatchers.IO) {
        parkingDao.getLatest()?.toDomain()
    }

    suspend fun fetchPrayerTimes(zone: String): PrayerTimesDay? = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val key = "${zone}_${today}"
        cacheDao.getPrayer(key)?.let { cached ->
            return@withContext PrayerTimesDay(
                zone = cached.zone,
                dateLabel = cached.dateLabel,
                fajr = cached.fajr,
                syuruk = cached.syuruk,
                dhuhr = cached.dhuhr,
                asr = cached.asr,
                maghrib = cached.maghrib,
                isha = cached.isha,
                source = cached.source
            )
        }

        val jakimDay = runCatching {
            val response = NetworkModule.waktuSolatApi.getMonthlyPrayerTimes(zone, today.monthValue, today.year)
            val targetDate = today.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH))
            val row = response.prayerTime?.firstOrNull { it.date.equals(targetDate, ignoreCase = true) }
                ?: response.prayerTime?.getOrNull((today.dayOfMonth - 1).coerceAtLeast(0))
            if (row != null && row.fajr != null) {
                PrayerTimesDay(
                    zone = zone,
                    dateLabel = row.date ?: today.toString(),
                    fajr = row.fajr.take(5),
                    syuruk = (row.syuruk ?: "07:00").take(5),
                    dhuhr = (row.dhuhr ?: "13:10").take(5),
                    asr = (row.asr ?: "16:30").take(5),
                    maghrib = (row.maghrib ?: "19:10").take(5),
                    isha = (row.isha ?: "20:25").take(5),
                    source = "JAKIM ($zone)"
                )
            } else null
        }.getOrNull()

        val resolved = jakimDay ?: runCatching {
            val coords = locationProvider.getCurrentCoordinates()
            val ddMmYyyy = today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH))
            val res = NetworkModule.alAdhanApi.getTimings(ddMmYyyy, coords.latitude, coords.longitude, 17)
            val t = res.data?.timings
            if (t?.fajr != null) {
                PrayerTimesDay(
                    zone = zone,
                    dateLabel = today.toString(),
                    fajr = t.fajr.take(5),
                    syuruk = (t.sunrise ?: "07:00").take(5),
                    dhuhr = (t.dhuhr ?: "13:10").take(5),
                    asr = (t.asr ?: "16:30").take(5),
                    maghrib = (t.maghrib ?: "19:10").take(5),
                    isha = (t.isha ?: "20:25").take(5),
                    source = "AlAdhan (JAKIM Method 17)"
                )
            } else null
        }.getOrNull()

        if (resolved != null) {
            cacheDao.upsertPrayer(
                CachedPrayerEntity(
                    zoneAndDate = key,
                    zone = resolved.zone,
                    dateLabel = resolved.dateLabel,
                    fajr = resolved.fajr,
                    syuruk = resolved.syuruk,
                    dhuhr = resolved.dhuhr,
                    asr = resolved.asr,
                    maghrib = resolved.maghrib,
                    isha = resolved.isha,
                    source = resolved.source,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
        resolved
    }

    suspend fun fetchWeather(): WeatherSummary? = withContext(Dispatchers.IO) {
        val coords = locationProvider.getCurrentCoordinates()
        runCatching {
            val dto = NetworkModule.openMeteoApi.getForecast(coords.latitude, coords.longitude)
            val current = dto.current ?: return@runCatching null
            val daily = dto.daily
            val dates = daily?.time.orEmpty()
            val forecastItems = dates.indices.map { idx ->
                DailyForecastItem(
                    date = dates[idx],
                    minTempC = daily?.tempMin?.getOrNull(idx) ?: 24.0,
                    maxTempC = daily?.tempMax?.getOrNull(idx) ?: 33.0,
                    rainProbPercent = daily?.precipProbMax?.getOrNull(idx) ?: 0,
                    conditionLabel = WeatherCodeMapper.describe(daily?.weatherCode?.getOrNull(idx) ?: 0)
                )
            }
            WeatherSummary(
                temperatureC = current.temperature2m ?: 30.0,
                apparentTempC = current.apparentTemperature ?: 33.0,
                conditionLabel = WeatherCodeMapper.describe(current.weatherCode ?: 0),
                humidityPercent = current.relativeHumidity2m ?: 70,
                rainProbabilityPercent = daily?.precipProbMax?.firstOrNull() ?: 0,
                uvIndexMax = daily?.uvIndexMax?.firstOrNull() ?: 0.0,
                windSpeedKmh = current.windSpeed10m ?: 0.0,
                dailyForecast = forecastItems
            )
        }.getOrNull()
    }

    suspend fun fetchMalaysiaFuelPrices(): FuelPriceInfo? = withContext(Dispatchers.IO) {
        val remote = runCatching {
            val rows = NetworkModule.malaysiaFuelApi.getFuelPrices()
            val latest = rows.firstOrNull { it.seriesType == "level" && it.ron95 != null } ?: rows.firstOrNull()
            latest?.let {
                FuelPriceInfo(
                    effectiveDate = it.date ?: "Latest",
                    ron95 = it.ron95 ?: 2.05,
                    ron95Budi = it.ron95Budi95,
                    ron95Skps = it.ron95Skps,
                    ron97 = it.ron97 ?: 3.18,
                    dieselPeninsular = it.diesel ?: 2.95,
                    dieselEastMalaysia = it.dieselEastMsia
                )
            }
        }.getOrNull()

        if (remote != null) {
            cacheDao.upsertFuel(
                CachedFuelEntity(
                    effectiveDate = remote.effectiveDate,
                    ron95 = remote.ron95,
                    ron95Budi = remote.ron95Budi,
                    ron95Skps = remote.ron95Skps,
                    ron97 = remote.ron97,
                    dieselPeninsular = remote.dieselPeninsular,
                    dieselEastMalaysia = remote.dieselEastMalaysia,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            return@withContext remote
        }

        cacheDao.getFuel()?.let {
            FuelPriceInfo(
                effectiveDate = it.effectiveDate,
                ron95 = it.ron95,
                ron95Budi = it.ron95Budi,
                ron95Skps = it.ron95Skps,
                ron97 = it.ron97,
                dieselPeninsular = it.dieselPeninsular,
                dieselEastMalaysia = it.dieselEastMalaysia
            )
        }
    }

    suspend fun fetchNearbyEvChargers(): List<EvChargerStation> = withContext(Dispatchers.IO) {
        val coords = locationProvider.getCurrentCoordinates()
        val prefs = getPreferencesSnapshot()
        val apiKey = prefs.openChargeMapApiKey.ifBlank { BuildConfig.OPEN_CHARGE_MAP_API_KEY }

        if (apiKey.isNotBlank()) {
            val live = runCatching {
                NetworkModule.openChargeMapApi.getNearbyChargers(
                    latitude = coords.latitude,
                    longitude = coords.longitude,
                    apiKey = apiKey
                ).mapNotNull { poi ->
                    val addr = poi.addressInfo ?: return@mapNotNull null
                    val lat = addr.latitude ?: return@mapNotNull null
                    val lon = addr.longitude ?: return@mapNotNull null
                    val maxKw = poi.connections.orEmpty().mapNotNull { it.powerKw }.maxOrNull() ?: 22.0
                    val connectors = poi.connections.orEmpty()
                        .mapNotNull { it.connectionType?.title }
                        .distinct()
                        .take(2)
                        .joinToString(", ")
                        .ifBlank { "CCS2 / Type 2" }
                    val dist = addr.distance ?: DistanceCalculator.haversineKm(coords.latitude, coords.longitude, lat, lon)
                    EvChargerStation(
                        id = poi.id ?: lat.hashCode().toLong(),
                        name = addr.title ?: "EV Charging Station",
                        operatorName = poi.operatorInfo?.title ?: "Public Network",
                        address = listOfNotNull(addr.addressLine1, addr.town).joinToString(", ").ifBlank { "Malaysia" },
                        latitude = lat,
                        longitude = lon,
                        distanceKm = dist,
                        maxPowerKw = maxKw,
                        connectorSummary = "$connectors (${maxKw.toInt()} kW)",
                        isSampleFallback = false
                    )
                }
            }.getOrNull()
            if (!live.isNullOrEmpty()) return@withContext live.sortedBy { it.distanceKm }
        }

        val samples = listOf(
            EvChargerStation(101L, "Setia City Mall DC Hub (Sample)", "Gentari / JomCharge", "Setia Alam, Shah Alam", 3.1095, 101.4601, 0.0, 120.0, "CCS2 (120 kW) • Add OCM API key for live data", true),
            EvChargerStation(102L, "i-City Central Mall Chargers (Sample)", "ChargEV", "Seksyen 7, Shah Alam", 3.0642, 101.4847, 0.0, 50.0, "CCS2 / Type 2 (50 kW) • Sample", true),
            EvChargerStation(103L, "Sunway Pyramid EV Bay (Sample)", "Shell Recharge", "Bandar Sunway, Subang Jaya", 3.0733, 101.6075, 0.0, 180.0, "CCS2 HPC (180 kW) • Sample", true)
        )
        samples.map {
            it.copy(distanceKm = DistanceCalculator.haversineKm(coords.latitude, coords.longitude, it.latitude, it.longitude))
        }.sortedBy { it.distanceKm }
    }

    suspend fun fetchSportsScores(): List<SportsMatch> = withContext(Dispatchers.IO) {
        val prefs = getPreferencesSnapshot()
        val fdKey = prefs.footballDataApiKey.ifBlank { BuildConfig.FOOTBALL_DATA_API_KEY }
        if (fdKey.isNotBlank()) {
            val fromFd = runCatching {
                NetworkModule.footballDataApi.getTodayMatches(fdKey).matches.orEmpty().take(8).map { m ->
                    SportsMatch(
                        id = (m.id ?: 0L).toString(),
                        league = m.competition?.name ?: "Football",
                        homeTeam = m.homeTeam?.shortName ?: m.homeTeam?.name ?: "Home",
                        awayTeam = m.awayTeam?.shortName ?: m.awayTeam?.name ?: "Away",
                        homeScore = m.score?.fullTime?.home?.toString() ?: "-",
                        awayScore = m.score?.fullTime?.away?.toString() ?: "-",
                        status = m.status ?: "SCHEDULED"
                    )
                }
            }.getOrNull()
            if (!fromFd.isNullOrEmpty()) return@withContext fromFd
        }

        runCatching {
            NetworkModule.espnSportsApi.getEplScoreboard().events.orEmpty().take(8).mapNotNull { event ->
                val comp = event.competitions?.firstOrNull() ?: return@mapNotNull null
                val home = comp.competitors?.firstOrNull { it.homeAway == "home" } ?: return@mapNotNull null
                val away = comp.competitors?.firstOrNull { it.homeAway == "away" } ?: return@mapNotNull null
                SportsMatch(
                    id = event.id ?: "${home.team?.shortDisplayName}_${away.team?.shortDisplayName}",
                    league = "Premier League",
                    homeTeam = home.team?.shortDisplayName ?: home.team?.displayName ?: "Home",
                    awayTeam = away.team?.shortDisplayName ?: away.team?.displayName ?: "Away",
                    homeScore = home.score ?: "0",
                    awayScore = away.score ?: "0",
                    status = event.status?.type?.shortDetail ?: "Scheduled"
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchTodayCalendarEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val now = System.currentTimeMillis()
        val endOfWindow = now + 24L * 60L * 60L * 1000L
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, now)
        ContentUris.appendId(uriBuilder, endOfWindow)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val events = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 10) {
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(0),
                            title = cursor.getString(1).orEmpty().ifBlank { "Untitled Event" },
                            startTimeMillis = cursor.getLong(2),
                            endTimeMillis = cursor.getLong(3),
                            location = cursor.getString(4).orEmpty()
                        )
                    )
                }
            }
        }
        events
    }

    suspend fun fetchLyrics(trackName: String, artistName: String): LyricsResult? = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext null
        val exact = runCatching {
            NetworkModule.lrcLibApi.getLyrics(trackName, artistName)
        }.getOrNull()

        val dto = exact ?: runCatching {
            NetworkModule.lrcLibApi.searchLyrics("$trackName $artistName".trim()).firstOrNull()
        }.getOrNull() ?: return@withContext null

        val synced = dto.syncedLyrics?.let { LrcParser.parse(it) }.orEmpty()
        LyricsResult(
            trackTitle = dto.trackName ?: trackName,
            artistName = dto.artistName ?: artistName,
            syncedLines = synced,
            plainLyrics = dto.plainLyrics.orEmpty(),
            source = "LRCLIB"
        )
    }

    private fun defaultWidgets(): List<DashboardWidgetConfig> =
        WidgetType.entries.mapIndexed { idx, type -> DashboardWidgetConfig(type, true, idx) }

    private fun ParkingSpotEntity.toDomain() = ParkingSpot(
        id = id,
        latitude = latitude,
        longitude = longitude,
        level = level,
        bay = bay,
        notes = notes,
        savedAtMillis = savedAtMillis
    )
}
