package com.autodeck.companion.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.autodeck.companion.data.repository.AutoDeckRepository
import com.autodeck.companion.model.AppThemeOption
import com.autodeck.companion.model.CalendarEvent
import com.autodeck.companion.model.DarkModePreference
import com.autodeck.companion.model.DashboardWidgetConfig
import com.autodeck.companion.model.EvChargerStation
import com.autodeck.companion.model.FuelPriceInfo
import com.autodeck.companion.model.LyricsResult
import com.autodeck.companion.model.NextPrayerInfo
import com.autodeck.companion.model.NowPlayingTrack
import com.autodeck.companion.model.ParkingSpot
import com.autodeck.companion.model.PrayerTimesDay
import com.autodeck.companion.model.SportsMatch
import com.autodeck.companion.model.UserPreferences
import com.autodeck.companion.model.WeatherSummary
import com.autodeck.companion.model.WidgetType
import com.autodeck.companion.service.MediaPlaybackTracker
import com.autodeck.companion.util.LrcParser
import com.autodeck.companion.util.PrayerCalculatorUtil
import com.autodeck.companion.util.QiblaCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val isRefreshing: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val widgets: List<DashboardWidgetConfig> = emptyList(),
    val prayerDay: PrayerTimesDay? = null,
    val nextPrayer: NextPrayerInfo? = null,
    val qiblaBearingDeg: Double = 292.5,
    val weather: WeatherSummary? = null,
    val fuelPrice: FuelPriceInfo? = null,
    val evChargers: List<EvChargerStation> = emptyList(),
    val sportsMatches: List<SportsMatch> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val latestParking: ParkingSpot? = null,
    val parkingHistory: List<ParkingSpot> = emptyList(),
    val nowPlaying: NowPlayingTrack = NowPlayingTrack(),
    val lyricsResult: LyricsResult? = null,
    val activeLyricIndex: Int = -1,
    val statusBanner: String? = null
)

class MainViewModel(private val repo: AutoDeckRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastFetchedLyricsKey: String = ""

    init {
        viewModelScope.launch { repo.ensureDefaultWidgetsSeeded() }
        observeStreams()
        startLiveTickers()
        refreshAll()
    }

    private fun observeStreams() {
        viewModelScope.launch {
            repo.preferencesStore.preferencesFlow.collectLatest { prefs ->
                val zoneChanged = _uiState.value.preferences.jakimZone != prefs.jakimZone
                _uiState.update { it.copy(preferences = prefs) }
                if (zoneChanged) refreshPrayerAndQibla(prefs.jakimZone)
            }
        }
        viewModelScope.launch {
            repo.widgetConfigsFlow.collectLatest { widgets ->
                _uiState.update { it.copy(widgets = widgets) }
            }
        }
        viewModelScope.launch {
            repo.latestParkingFlow.collectLatest { spot ->
                _uiState.update { it.copy(latestParking = spot) }
            }
        }
        viewModelScope.launch {
            repo.parkingHistoryFlow.collectLatest { history ->
                _uiState.update { it.copy(parkingHistory = history) }
            }
        }
        viewModelScope.launch {
            MediaPlaybackTracker.nowPlaying.collectLatest { track ->
                _uiState.update { it.copy(nowPlaying = track) }
                val key = "${track.title}|${track.artist}"
                if (track.title.isNotBlank() && key != lastFetchedLyricsKey) {
                    lastFetchedLyricsKey = key
                    val lyrics = repo.fetchLyrics(track.title, track.artist)
                    _uiState.update { it.copy(lyricsResult = lyrics) }
                }
            }
        }
    }

    private fun startLiveTickers() {
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val lines = state.lyricsResult?.syncedLines.orEmpty()
                val pos = MediaPlaybackTracker.currentEstimatedPositionMs()
                val idx = LrcParser.activeLineIndex(lines, pos)
                val nextPrayer = state.prayerDay?.let { PrayerCalculatorUtil.findNextPrayer(it) }
                _uiState.update { it.copy(activeLyricIndex = idx, nextPrayer = nextPrayer) }
                delay(500L)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusBanner = null) }
            val prefs = repo.getPreferencesSnapshot()
            val coords = repo.locationProvider.getCurrentCoordinates()
            val qibla = QiblaCalculator.bearingDegrees(coords.latitude, coords.longitude)

            val prayer = repo.fetchPrayerTimes(prefs.jakimZone)
            val weather = repo.fetchWeather()
            val fuel = repo.fetchMalaysiaFuelPrices()
            val chargers = repo.fetchNearbyEvChargers()
            val sports = repo.fetchSportsScores()
            val events = repo.fetchTodayCalendarEvents()

            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    prayerDay = prayer,
                    nextPrayer = prayer?.let { p -> PrayerCalculatorUtil.findNextPrayer(p) },
                    qiblaBearingDeg = qibla,
                    weather = weather,
                    fuelPrice = fuel,
                    evChargers = chargers,
                    sportsMatches = sports,
                    calendarEvents = events
                )
            }
        }
    }

    private fun refreshPrayerAndQibla(zone: String) {
        viewModelScope.launch {
            val prayer = repo.fetchPrayerTimes(zone)
            _uiState.update {
                it.copy(
                    prayerDay = prayer,
                    nextPrayer = prayer?.let { p -> PrayerCalculatorUtil.findNextPrayer(p) }
                )
            }
        }
    }

    fun toggleWidget(type: WidgetType, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.widgets.map {
                if (it.type == type) it.copy(enabled = enabled) else it
            }
            repo.saveWidgets(updated)
        }
    }

    fun moveWidget(type: WidgetType, moveUp: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.widgets.toMutableList()
            val idx = list.indexOfFirst { it.type == type }
            if (idx == -1) return@launch
            val target = if (moveUp) idx - 1 else idx + 1
            if (target !in list.indices) return@launch
            val item = list.removeAt(idx)
            list.add(target, item)
            repo.saveWidgets(list)
        }
    }

    fun saveParking(level: String, bay: String, notes: String) {
        viewModelScope.launch {
            val saved = repo.saveParkingSpot(level, bay, notes)
            _uiState.update { it.copy(statusBanner = "Parking spot saved (Level ${saved.level}, Bay ${saved.bay})") }
        }
    }

    fun deleteParking(id: Long) {
        viewModelScope.launch { repo.deleteParkingSpot(id) }
    }

    fun searchLyricsManually(track: String, artist: String) {
        viewModelScope.launch {
            MediaPlaybackTracker.setManualPreviewTrack(track, artist)
            val result = repo.fetchLyrics(track, artist)
            _uiState.update {
                it.copy(
                    lyricsResult = result,
                    statusBanner = if (result == null) "No lyrics found on LRCLIB" else null
                )
            }
        }
    }

    fun setJakimZone(zone: String) = viewModelScope.launch { repo.preferencesStore.updateJakimZone(zone) }
    fun setTheme(option: AppThemeOption) = viewModelScope.launch { repo.preferencesStore.updateTheme(option) }
    fun setDarkMode(mode: DarkModePreference) = viewModelScope.launch { repo.preferencesStore.updateDarkMode(mode) }
    fun setAzanReminder(enabled: Boolean) = viewModelScope.launch { repo.preferencesStore.updateAzanReminder(enabled) }
    fun setCarProfile(name: String) = viewModelScope.launch { repo.preferencesStore.updateCarProfile(name) }
    fun saveApiKeys(ocmKey: String, footballKey: String) = viewModelScope.launch {
        repo.preferencesStore.updateApiKeys(ocmKey, footballKey)
        refreshAll()
        _uiState.update { it.copy(statusBanner = "API keys saved & data refreshed") }
    }

    class Factory(private val repo: AutoDeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo) as T
    }
}
