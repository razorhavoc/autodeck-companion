package com.autodeck.companion.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.autodeck.companion.AutoDeckApplication
import com.autodeck.companion.R
import com.autodeck.companion.model.CalendarEvent
import com.autodeck.companion.model.DashboardWidgetConfig
import com.autodeck.companion.model.FuelPriceInfo
import com.autodeck.companion.model.ParkingSpot
import com.autodeck.companion.model.PrayerTimesDay
import com.autodeck.companion.model.SportsMatch
import com.autodeck.companion.model.WeatherSummary
import com.autodeck.companion.model.WidgetType
import com.autodeck.companion.service.MediaPlaybackTracker
import com.autodeck.companion.util.PrayerCalculatorUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardTabScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        private const val TAB_HOME = "tab_home"
        private const val TAB_MOBILITY = "tab_mobility"
        private const val TAB_PRAYER = "tab_prayer"
        private const val TAB_HUB = "tab_hub"
    }

    private val repo = (carContext.applicationContext as AutoDeckApplication).container.repository

    private var activeTabId: String = TAB_HOME
    private var isLoading: Boolean = true
    private var widgets: List<DashboardWidgetConfig> = emptyList()
    private var prayerDay: PrayerTimesDay? = null
    private var weather: WeatherSummary? = null
    private var fuel: FuelPriceInfo? = null
    private var parking: ParkingSpot? = null
    private var sports: List<SportsMatch> = emptyList()
    private var calendarEvents: List<CalendarEvent> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()
            val prefs = repo.getPreferencesSnapshot()
            widgets = repo.getWidgetsSnapshot().filter { it.enabled }.sortedBy { it.sortOrder }
            prayerDay = repo.fetchPrayerTimes(prefs.jakimZone)
            weather = repo.fetchWeather()
            fuel = repo.fetchMalaysiaFuelPrices()
            parking = repo.getLatestParkingSnapshot()
            sports = repo.fetchSportsScores()
            calendarEvents = repo.fetchTodayCalendarEvents()
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTabId = tabContentId
                invalidate()
            }
        })
            .setHeaderAction(Action.APP_ICON)
            .addTab(buildTab(TAB_HOME, carContext.getString(R.string.tab_home), R.drawable.ic_car_home))
            .addTab(buildTab(TAB_MOBILITY, carContext.getString(R.string.tab_mobility), R.drawable.ic_car_ev))
            .addTab(buildTab(TAB_PRAYER, carContext.getString(R.string.tab_prayer), R.drawable.ic_car_prayer))
            .addTab(buildTab(TAB_HUB, carContext.getString(R.string.tab_hub), R.drawable.ic_car_hub))
            .setActiveTabContentId(activeTabId)

        if (isLoading) {
            return builder.setLoading(true).build()
        }

        val contentTemplate = when (activeTabId) {
            TAB_MOBILITY -> buildMobilityContent()
            TAB_PRAYER -> buildPrayerWeatherContent()
            TAB_HUB -> buildDriverHubContent()
            else -> buildHomeDashboardContent()
        }

        return builder
            .setLoading(false)
            .setTabContents(TabContents.Builder(contentTemplate).build())
            .build()
    }

    private fun buildTab(id: String, title: String, iconRes: Int): Tab =
        Tab.Builder()
            .setContentId(id)
            .setTitle(title)
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .build()

    // Templates inside TabContents must not set title or headerAction.
    private fun buildHomeDashboardContent(): ListTemplate {
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("Enable widgets in the AutoDeck phone app")

        widgets.take(6).forEach { widget ->
            when (widget.type) {
                WidgetType.PRAYER -> {
                    val next = prayerDay?.let { PrayerCalculatorUtil.findNextPrayer(it) }
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(next?.let { "Next Prayer: ${it.name} (${it.timeText})" } ?: "Prayer Times")
                            .addText(next?.let { "In ${it.remainingText} • ${prayerDay?.source.orEmpty()}" } ?: "Tap to view JAKIM schedule")
                            .setOnClickListener { screenManager.push(PrayerCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.WEATHER -> {
                    val w = weather
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(w?.let { "Weather: ${it.temperatureC.toInt()}°C • ${it.conditionLabel}" } ?: "Weather")
                            .addText(w?.let { "Rain ${it.rainProbabilityPercent}% • UV ${it.uvIndexMax.toInt()}" } ?: "Tap for forecast")
                            .setOnClickListener { screenManager.push(WeatherCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.EV_CHARGERS -> {
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle("Nearby EV Chargers")
                            .addText("Open interactive map & connector kW ratings")
                            .setOnClickListener { screenManager.push(EvChargersMapScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.FUEL_PRICES -> {
                    val f = fuel
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(
                                f?.let {
                                    "Fuel MY: RON95 RM${"%.2f".format(Locale.US, it.ron95)} | RON97 RM${"%.2f".format(Locale.US, it.ron97)}"
                                } ?: "Malaysia Fuel Prices"
                            )
                            .addText(
                                f?.let {
                                    "Diesel RM${"%.2f".format(Locale.US, it.dieselPeninsular)} • Effective ${it.effectiveDate}"
                                } ?: "Tap for weekly prices"
                            )
                            .setOnClickListener { screenManager.push(FuelCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.PARKING -> {
                    val p = parking
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(p?.let { "Parked: Level ${it.level}, Bay ${it.bay}" } ?: "Parking Saver")
                            .addText(p?.notes?.ifBlank { "Tap to navigate back or update spot" } ?: "Tap to save current GPS parking spot")
                            .setOnClickListener { screenManager.push(ParkingCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.MUSIC -> {
                    val track = MediaPlaybackTracker.nowPlaying.value
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(track.title.ifBlank { "Now Playing" })
                            .addText(track.artist.ifBlank { "Play Spotify/YouTube Music • Synced lyrics on phone" })
                            .setOnClickListener { screenManager.push(NowPlayingCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.SPORTS -> {
                    val top = sports.firstOrNull()
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(top?.let { "${it.homeTeam} ${it.homeScore} - ${it.awayScore} ${it.awayTeam}" } ?: "Sports Scores")
                            .addText(top?.let { "${it.league} • ${it.status}" } ?: "Tap for live scores")
                            .setOnClickListener { screenManager.push(SportsCarScreen(carContext)) }
                            .build()
                    )
                }
                WidgetType.CALENDAR -> {
                    val nextEvent = calendarEvents.firstOrNull()
                    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(nextEvent?.title ?: "Today's Calendar")
                            .addText(nextEvent?.let { "${timeFmt.format(Date(it.startTimeMillis))} ${it.location}".trim() } ?: "No upcoming events today")
                            .setOnClickListener { screenManager.push(CalendarCarScreen(carContext)) }
                            .build()
                    )
                }
            }
        }
        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun buildMobilityContent(): ListTemplate {
        val f = fuel
        val p = parking
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("EV Charging Stations Map")
                    .addText("View nearby chargers on map & start navigation")
                    .setOnClickListener { screenManager.push(EvChargersMapScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Parking Location Saver")
                    .addText(p?.let { "Saved: Level ${it.level} • Bay ${it.bay}" } ?: "Save current GPS spot or navigate to car")
                    .setOnClickListener { screenManager.push(ParkingCarScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(
                        f?.let {
                            "RON95: RM${"%.2f".format(Locale.US, it.ron95)} | RON97: RM${"%.2f".format(Locale.US, it.ron97)}"
                        } ?: "Malaysia Fuel Prices"
                    )
                    .addText(
                        f?.let {
                            "Diesel (Peninsular): RM${"%.2f".format(Locale.US, it.dieselPeninsular)}"
                        } ?: "Official data.gov.my weekly prices"
                    )
                    .setOnClickListener { screenManager.push(FuelCarScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Refresh Live Data")
                    .addText("Update fuel, weather, prayer & scores")
                    .setOnClickListener { loadData() }
                    .build()
            )
            .build()
        return ListTemplate.Builder().setSingleList(list).build()
    }

    private fun buildPrayerWeatherContent(): ListTemplate {
        val builder = ItemList.Builder()
        val p = prayerDay
        if (p != null) {
            val next = PrayerCalculatorUtil.findNextPrayer(p)
            builder.addItem(
                Row.Builder()
                    .setTitle("Next: ${next.name} at ${next.timeText} (${next.remainingText})")
                    .addText("Zone ${p.zone} • Tap for full prayer schedule")
                    .setOnClickListener { screenManager.push(PrayerCarScreen(carContext)) }
                    .build()
            )
            builder.addItem(
                Row.Builder()
                    .setTitle("Subuh ${p.fajr} • Zohor ${p.dhuhr} • Asar ${p.asr}")
                    .addText("Maghrib ${p.maghrib} • Isyak ${p.isha}")
                    .build()
            )
        }
        val w = weather
        if (w != null) {
            builder.addItem(
                Row.Builder()
                    .setTitle("Weather: ${w.temperatureC.toInt()}°C (${w.conditionLabel})")
                    .addText("Feels ${w.apparentTempC.toInt()}°C • Rain ${w.rainProbabilityPercent}% • Humidity ${w.humidityPercent}%")
                    .setOnClickListener { screenManager.push(WeatherCarScreen(carContext)) }
                    .build()
            )
        }
        return ListTemplate.Builder().setSingleList(builder.build()).build()
    }

    private fun buildDriverHubContent(): ListTemplate {
        val track = MediaPlaybackTracker.nowPlaying.value
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Now Playing: ${track.title.ifBlank { "No Active Media" }}")
                    .addText(track.artist.ifBlank { "Open phone app for synchronized lyrics" })
                    .setOnClickListener { screenManager.push(NowPlayingCarScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Today's Calendar (${calendarEvents.size} events)")
                    .addText(calendarEvents.firstOrNull()?.title ?: "Tap to inspect schedule")
                    .setOnClickListener { screenManager.push(CalendarCarScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Live Sports Scores (${sports.size} matches)")
                    .addText(sports.firstOrNull()?.let { "${it.homeTeam} ${it.homeScore}-${it.awayScore} ${it.awayTeam}" } ?: "Tap for scores")
                    .setOnClickListener { screenManager.push(SportsCarScreen(carContext)) }
                    .build()
            )
            .build()
        return ListTemplate.Builder().setSingleList(list).build()
    }
}
