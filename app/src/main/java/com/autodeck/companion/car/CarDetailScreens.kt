package com.autodeck.companion.car

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.HostException
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.autodeck.companion.AutoDeckApplication
import com.autodeck.companion.R
import com.autodeck.companion.model.CalendarEvent
import com.autodeck.companion.model.EvChargerStation
import com.autodeck.companion.model.FuelPriceInfo
import com.autodeck.companion.model.ParkingSpot
import com.autodeck.companion.model.PrayerTimesDay
import com.autodeck.companion.model.SportsMatch
import com.autodeck.companion.model.WeatherSummary
import com.autodeck.companion.service.MediaPlaybackTracker
import com.autodeck.companion.util.PrayerCalculatorUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun launchCarNavigation(carContext: CarContext, lat: Double, lon: Double, label: String) {
    val uri = Uri.parse("geo:0,0?q=$lat,$lon(${Uri.encode(label)})")
    val intent = Intent(CarContext.ACTION_NAVIGATE, uri)
    try {
        carContext.startCarApp(intent)
    } catch (_: HostException) {
        CarToast.makeText(carContext, "Unable to launch navigation app", CarToast.LENGTH_SHORT).show()
    }
}

class EvChargersMapScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var isLoading = true
    private var stations: List<EvChargerStation> = emptyList()

    init {
        lifecycleScope.launch {
            stations = repo.fetchNearbyEvChargers()
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = PlaceListMapTemplate.Builder()
            .setTitle("Nearby EV Chargers")
            .setHeaderAction(Action.BACK)

        if (repo.locationProvider.hasLocationPermission()) {
            builder.setCurrentLocationEnabled(true)
        }

        if (isLoading) return builder.setLoading(true).build()

        val listBuilder = ItemList.Builder().setNoItemsMessage("No EV stations found nearby")
        stations.take(6).forEach { station ->
            val distText = SpannableString("  • ${station.connectorSummary}").apply {
                setSpan(
                    DistanceSpan.create(Distance.create(station.distanceKm.coerceAtLeast(0.1), Distance.UNIT_KILOMETERS)),
                    0,
                    1,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
            }
            val place = Place.Builder(CarLocation.create(station.latitude, station.longitude))
                .setMarker(PlaceMarker.Builder().build())
                .build()

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(station.name)
                    .addText(distText)
                    .addText(station.address)
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setOnClickListener { screenManager.push(EvChargerDetailScreen(carContext, station)) }
                    .build()
            )
        }

        return builder.setItemList(listBuilder.build()).build()
    }
}

class EvChargerDetailScreen(
    carContext: CarContext,
    private val station: EvChargerStation
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val navIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_navigate)).build()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Operator").addText(station.operatorName).build())
            .addRow(Row.Builder().setTitle("Connectors").addText(station.connectorSummary).build())
            .addRow(Row.Builder().setTitle("Address").addText(station.address).build())
            .addAction(
                Action.Builder()
                    .setTitle("Navigate")
                    .setIcon(navIcon)
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener {
                        launchCarNavigation(carContext, station.latitude, station.longitude, station.name)
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(station.name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class ParkingCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var spot: ParkingSpot? = null

    init {
        lifecycleScope.launch {
            spot = repo.getLatestParkingSnapshot()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val current = spot
        val paneBuilder = Pane.Builder()
        if (current != null) {
            val savedTime = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(current.savedAtMillis))
            paneBuilder
                .addRow(Row.Builder().setTitle("Level ${current.level} • Bay ${current.bay}").addText("Saved $savedTime").build())
                .addRow(
                    Row.Builder()
                        .setTitle("GPS Coordinates")
                        .addText("${"%.5f".format(Locale.US, current.latitude)}, ${"%.5f".format(Locale.US, current.longitude)}")
                        .build()
                )
                .addRow(Row.Builder().setTitle("Notes").addText(current.notes.ifBlank { "Edit bay/level details in phone app" }).build())
                .addAction(
                    Action.Builder()
                        .setTitle("Navigate")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener {
                            launchCarNavigation(carContext, current.latitude, current.longitude, "Parked Car (${current.level}/${current.bay})")
                        }
                        .build()
                )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("No Parking Spot Saved")
                    .addText("Tap Save GPS to remember where you parked")
                    .build()
            )
        }

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Save GPS")
                .setOnClickListener {
                    lifecycleScope.launch {
                        spot = repo.saveParkingSpot("Auto", "GPS", "Saved from Android Auto")
                        CarToast.makeText(carContext, "Parking location saved", CarToast.LENGTH_SHORT).show()
                        invalidate()
                    }
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Parking Saver")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class PrayerCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var prayerDay: PrayerTimesDay? = null

    init {
        lifecycleScope.launch {
            val prefs = repo.getPreferencesSnapshot()
            prayerDay = repo.fetchPrayerTimes(prefs.jakimZone)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val day = prayerDay
        val listBuilder = ItemList.Builder().setNoItemsMessage("Unable to load JAKIM prayer times")
        if (day != null) {
            val next = PrayerCalculatorUtil.findNextPrayer(day)
            listBuilder.addItem(Row.Builder().setTitle("Next: ${next.name} (${next.timeText})").addText("Remaining: ${next.remainingText} • ${day.source}").build())
            listBuilder.addItem(Row.Builder().setTitle("Subuh: ${day.fajr}").addText("Syuruk: ${day.syuruk}").build())
            listBuilder.addItem(Row.Builder().setTitle("Zohor: ${day.dhuhr}").addText("Asar: ${day.asr}").build())
            listBuilder.addItem(Row.Builder().setTitle("Maghrib: ${day.maghrib}").addText("Isyak: ${day.isha}").build())
        }
        return ListTemplate.Builder()
            .setTitle("Prayer Times")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class WeatherCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var weather: WeatherSummary? = null

    init {
        lifecycleScope.launch {
            weather = repo.fetchWeather()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val w = weather
        val listBuilder = ItemList.Builder().setNoItemsMessage("Weather unavailable")
        if (w != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Now: ${w.temperatureC.toInt()}°C • ${w.conditionLabel}")
                    .addText("Feels ${w.apparentTempC.toInt()}°C • Rain ${w.rainProbabilityPercent}% • UV ${w.uvIndexMax.toInt()}")
                    .build()
            )
            w.dailyForecast.take(4).forEach { day ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${day.date}: ${day.conditionLabel}")
                        .addText("${day.minTempC.toInt()}°C – ${day.maxTempC.toInt()}°C • Rain ${day.rainProbPercent}%")
                        .build()
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("Weather Forecast")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class FuelCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var fuel: FuelPriceInfo? = null

    init {
        lifecycleScope.launch {
            fuel = repo.fetchMalaysiaFuelPrices()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val f = fuel
        val listBuilder = ItemList.Builder().setNoItemsMessage("Fuel prices unavailable")
        if (f != null) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("RON95: RM ${"%.2f".format(Locale.US, f.ron95)} / L")
                    .addText(f.ron95Budi?.let { "BUDI 95 Subsidy: RM ${"%.2f".format(Locale.US, it)} / L" } ?: "Official data.gov.my")
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("RON97: RM ${"%.2f".format(Locale.US, f.ron97)} / L")
                    .addText("Effective ${f.effectiveDate}")
                    .build()
            )
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Diesel (Peninsular): RM ${"%.2f".format(Locale.US, f.dieselPeninsular)} / L")
                    .addText(f.dieselEastMalaysia?.let { "Sabah/Sarawak: RM ${"%.2f".format(Locale.US, it)} / L" } ?: "Weekly MY retail price")
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("Malaysia Fuel Prices")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class SportsCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var matches: List<SportsMatch> = emptyList()

    init {
        lifecycleScope.launch {
            matches = repo.fetchSportsScores()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder().setNoItemsMessage("No live matches right now")
        matches.take(6).forEach { m ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("${m.homeTeam} ${m.homeScore} - ${m.awayScore} ${m.awayTeam}")
                    .addText("${m.league} • ${m.status}")
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("Sports Scores")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class CalendarCarScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository
    private var events: List<CalendarEvent> = emptyList()

    init {
        lifecycleScope.launch {
            events = repo.fetchTodayCalendarEvents()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val listBuilder = ItemList.Builder().setNoItemsMessage("No upcoming events or calendar permission not granted")
        events.take(6).forEach { ev ->
            val rowBuilder = Row.Builder()
                .setTitle(ev.title)
                .addText("${fmt.format(Date(ev.startTimeMillis))} – ${fmt.format(Date(ev.endTimeMillis))}${if (ev.location.isNotBlank()) " • ${ev.location}" else ""}")
            if (ev.location.isNotBlank()) {
                rowBuilder.setOnClickListener {
                    val intent = Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=${Uri.encode(ev.location)}"))
                    runCatching { carContext.startCarApp(intent) }
                }
            }
            listBuilder.addItem(rowBuilder.build())
        }
        return ListTemplate.Builder()
            .setTitle("Today's Calendar")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class NowPlayingCarScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val track = MediaPlaybackTracker.nowPlaying.value
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(track.title.ifBlank { "No Active Track" }).addText(track.artist.ifBlank { "Start Spotify or YouTube Music" }).build())
            .addRow(Row.Builder().setTitle("Status").addText(if (track.isPlaying) "Playing" else "Paused / Idle").build())
            .addRow(Row.Builder().setTitle("Synchronized Lyrics").addText("Scrolling lyrics are displayed in the phone app to comply with Android Auto driving safety rules.").build())
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener { invalidate() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Now Playing")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
