package com.autodeck.companion.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autodeck.companion.model.AppThemeOption
import com.autodeck.companion.model.DarkModePreference
import com.autodeck.companion.ui.viewmodel.MainUiState
import com.autodeck.companion.ui.viewmodel.MainViewModel
import com.autodeck.companion.util.JakimZones
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.preferences.carProfileName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android Auto POI Companion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.refreshAll() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            state.statusBanner?.let { banner ->
                Text(banner, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SummaryCard(
                title = "Next Prayer (${state.preferences.jakimZone})",
                body = state.nextPrayer?.let { "${it.name} at ${it.timeText} • in ${it.remainingText}" } ?: "Loading JAKIM schedule..."
            )
        }
        item {
            SummaryCard(
                title = "Weather & Forecast",
                body = state.weather?.let {
                    "${it.temperatureC.toInt()}°C (${it.conditionLabel}) • Rain ${it.rainProbabilityPercent}% • UV ${it.uvIndexMax.toInt()}"
                } ?: "Fetching Open-Meteo weather..."
            )
        }
        item {
            SummaryCard(
                title = "Malaysia Fuel Prices",
                body = state.fuelPrice?.let {
                    "RON95 RM${"%.2f".format(Locale.US, it.ron95)} | RON97 RM${"%.2f".format(Locale.US, it.ron97)} | Diesel RM${"%.2f".format(Locale.US, it.dieselPeninsular)}"
                } ?: "Loading official data.gov.my fuel prices..."
            )
        }
        item {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val eventText = state.calendarEvents.firstOrNull()?.let {
                "${fmt.format(Date(it.startTimeMillis))} — ${it.title}"
            } ?: "No upcoming events today"
            val scoreText = state.sportsMatches.firstOrNull()?.let {
                "${it.homeTeam} ${it.homeScore}-${it.awayScore} ${it.awayTeam} (${it.status})"
            } ?: "No live matches"
            SummaryCard(title = "Calendar & Sports", body = "$eventText\n$scoreText")
        }

        item {
            Text(
                text = "Android Auto Widget Order",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Toggle and reorder widgets synced to your car's Home tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(state.widgets, key = { it.type.name }) { widget ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(widget.type.title, fontWeight = FontWeight.Medium)
                        Text(widget.type.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.moveWidget(widget.type, moveUp = true) }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up")
                    }
                    IconButton(onClick = { viewModel.moveWidget(widget.type, moveUp = false) }) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down")
                    }
                    Switch(
                        checked = widget.enabled,
                        onCheckedChange = { viewModel.toggleWidget(widget.type, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var queryTitle by remember { mutableStateOf("") }
    var queryArtist by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Now Playing & Synced Lyrics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Grant Notification Access to auto-sync Spotify & YouTube Music. Full scrolling lyrics stay on the phone to comply with Android Auto safety rules.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Enable Notification Listener Access")
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(state.nowPlaying.title.ifBlank { "No Active Track Detected" }, fontWeight = FontWeight.Bold)
                    Text(state.nowPlaying.artist.ifBlank { "Search a song manually below or play music" })
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = queryTitle,
                    onValueChange = { queryTitle = it },
                    label = { Text("Track") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = queryArtist,
                    onValueChange = { queryArtist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { viewModel.searchLyricsManually(queryTitle, queryArtist) },
                enabled = queryTitle.isNotBlank()
            ) {
                Text("Fetch Synced Lyrics (LRCLIB)")
            }
        }

        val synced = state.lyricsResult?.syncedLines.orEmpty()
        if (synced.isNotEmpty()) {
            itemsIndexed(synced) { idx, line ->
                val isActive = idx == state.activeLyricIndex
                Text(
                    text = line.text,
                    style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else if (!state.lyricsResult?.plainLyrics.isNullOrBlank()) {
            item { Text(state.lyricsResult!!.plainLyrics, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Islamic Prayer Times", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Qibla Bearing: ${"%.1f".format(Locale.US, state.qiblaBearingDeg)}° from True North", color = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val next = state.nextPrayer
                    Text("Next Prayer", style = MaterialTheme.typography.labelMedium)
                    Text(
                        next?.let { "${it.name} • ${it.timeText}" } ?: "Loading...",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (next != null) Text("Countdown: ${next.remainingText}")
                }
            }
        }

        state.prayerDay?.let { p ->
            val rows = listOf(
                "Subuh" to p.fajr,
                "Syuruk" to p.syuruk,
                "Zohor" to p.dhuhr,
                "Asar" to p.asr,
                "Maghrib" to p.maghrib,
                "Isyak" to p.isha
            )
            items(rows) { (name, time) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, fontWeight = FontWeight.Medium)
                        Text(time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Text("Select Malaysia JAKIM Zone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(JakimZones.popular) { zone ->
            FilterChip(
                selected = state.preferences.jakimZone == zone.code,
                onClick = { viewModel.setJakimZone(zone.code) },
                label = { Text("${zone.code} — ${zone.label}") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MobilityScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var level by remember { mutableStateOf("") }
    var bay by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("EV, Fuel & Parking Saver", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save Parking Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Level") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = bay, onValueChange = { bay = it }, label = { Text("Bay") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (e.g. Near Pillar B4)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        viewModel.saveParking(level, bay, notes)
                        level = ""; bay = ""; notes = ""
                    }) {
                        Text("Save Current GPS Spot")
                    }
                }
            }
        }

        items(state.parkingHistory.take(3)) { spot ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Level ${spot.level} • Bay ${spot.bay}", fontWeight = FontWeight.Bold)
                        Text("${"%.5f".format(Locale.US, spot.latitude)}, ${"%.5f".format(Locale.US, spot.longitude)} ${spot.notes}".trim(), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.deleteParking(spot.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        item {
            Text("Malaysia Fuel Prices (data.gov.my)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val f = state.fuelPrice
            SummaryCard(
                title = f?.let { "Effective ${it.effectiveDate}" } ?: "Fuel Prices",
                body = f?.let {
                    buildString {
                        append("RON95: RM ${"%.2f".format(Locale.US, it.ron95)} / L\n")
                        it.ron95Budi?.let { b -> append("RON95 (BUDI 95): RM ${"%.2f".format(Locale.US, b)} / L\n") }
                        append("RON97: RM ${"%.2f".format(Locale.US, it.ron97)} / L\n")
                        append("Diesel (Peninsular): RM ${"%.2f".format(Locale.US, it.dieselPeninsular)} / L")
                        it.dieselEastMalaysia?.let { e -> append("\nDiesel (East MY): RM ${"%.2f".format(Locale.US, e)} / L") }
                    }
                } ?: "Unable to fetch fuel prices"
            )
        }

        item {
            Text("Nearby EV Chargers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(state.evChargers) { station ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(station.name, fontWeight = FontWeight.Bold)
                    Text("${station.operatorName} • ${"%.1f".format(Locale.US, station.distanceKm)} km", style = MaterialTheme.typography.bodySmall)
                    Text(station.connectorSummary, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var carName by remember(state.preferences.carProfileName) { mutableStateOf(state.preferences.carProfileName) }
    var ocmKey by remember(state.preferences.openChargeMapApiKey) { mutableStateOf(state.preferences.openChargeMapApiKey) }
    var footballKey by remember(state.preferences.footballDataApiKey) { mutableStateOf(state.preferences.footballDataApiKey) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Themes & Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = carName,
                onValueChange = { carName = it },
                label = { Text("Car Profile Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { viewModel.setCarProfile(carName) }) { Text("Save Profile Name") }
        }
        item {
            Text("Accent Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeOption.entries.take(2).forEach { opt ->
                    FilterChip(selected = state.preferences.themeOption == opt, onClick = { viewModel.setTheme(opt) }, label = { Text(opt.displayName) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeOption.entries.drop(2).forEach { opt ->
                    FilterChip(selected = state.preferences.themeOption == opt, onClick = { viewModel.setTheme(opt) }, label = { Text(opt.displayName) })
                }
            }
        }
        item {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkModePreference.entries.forEach { mode ->
                    FilterChip(selected = state.preferences.darkMode == mode, onClick = { viewModel.setDarkMode(mode) }, label = { Text(mode.displayName) })
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Azan Pre-Prayer Reminder", fontWeight = FontWeight.Medium)
                    Text("Notify 15 minutes before each prayer", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.preferences.azanReminderEnabled, onCheckedChange = { viewModel.setAzanReminder(it) })
            }
        }
        item {
            Text("External API Keys (Optional Overrides)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = ocmKey,
                onValueChange = { ocmKey = it },
                label = { Text("Open Charge Map API Key (Live EV Chargers)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = footballKey,
                onValueChange = { footballKey = it },
                label = { Text("Football-Data.org v4 Token (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.saveApiKeys(ocmKey, footballKey) }) {
                Text("Save API Keys & Refresh")
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
