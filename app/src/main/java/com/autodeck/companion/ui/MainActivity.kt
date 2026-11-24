package com.autodeck.companion.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autodeck.companion.AutoDeckApplication
import com.autodeck.companion.ui.screens.DashboardScreen
import com.autodeck.companion.ui.screens.LyricsScreen
import com.autodeck.companion.ui.screens.MobilityScreen
import com.autodeck.companion.ui.screens.PrayerScreen
import com.autodeck.companion.ui.screens.SettingsScreen
import com.autodeck.companion.ui.theme.AutoDeckTheme
import com.autodeck.companion.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as AutoDeckApplication).container.repository

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(repo))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var selectedTab by remember { mutableIntStateOf(0) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { viewModel.refreshAll() }

            LaunchedEffect(Unit) {
                val permissions = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    add(Manifest.permission.READ_CALENDAR)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                permissionLauncher.launch(permissions.toTypedArray())
            }

            AutoDeckTheme(
                themeOption = state.preferences.themeOption,
                darkModePreference = state.preferences.darkMode
            ) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Rounded.Dashboard, contentDescription = "Home") },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = "Lyrics") },
                                label = { Text("Lyrics") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Rounded.Mosque, contentDescription = "Prayer") },
                                label = { Text("Prayer") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = "Drive") },
                                label = { Text("Drive") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> DashboardScreen(state, viewModel, Modifier.padding(innerPadding))
                        1 -> LyricsScreen(state, viewModel, Modifier.padding(innerPadding))
                        2 -> PrayerScreen(state, viewModel, Modifier.padding(innerPadding))
                        3 -> MobilityScreen(state, viewModel, Modifier.padding(innerPadding))
                        4 -> SettingsScreen(state, viewModel, Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}
