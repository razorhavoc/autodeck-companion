package com.autodeck.companion.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autodeck.companion.model.AppThemeOption
import com.autodeck.companion.model.DarkModePreference
import com.autodeck.companion.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "autodeck_prefs")

class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val JAKIM_ZONE = stringPreferencesKey("jakim_zone")
        val CAR_PROFILE = stringPreferencesKey("car_profile")
        val THEME_OPTION = stringPreferencesKey("theme_option")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val AZAN_REMINDER = booleanPreferencesKey("azan_reminder")
        val OCM_API_KEY = stringPreferencesKey("ocm_api_key")
        val FOOTBALL_API_KEY = stringPreferencesKey("football_api_key")
    }

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        UserPreferences(
            jakimZone = prefs[Keys.JAKIM_ZONE] ?: "SGR01",
            carProfileName = prefs[Keys.CAR_PROFILE] ?: "My Car",
            themeOption = runCatching {
                AppThemeOption.valueOf(prefs[Keys.THEME_OPTION] ?: AppThemeOption.EMERALD_CRESCENT.name)
            }.getOrDefault(AppThemeOption.EMERALD_CRESCENT),
            darkMode = runCatching {
                DarkModePreference.valueOf(prefs[Keys.DARK_MODE] ?: DarkModePreference.DARK.name)
            }.getOrDefault(DarkModePreference.DARK),
            azanReminderEnabled = prefs[Keys.AZAN_REMINDER] ?: true,
            openChargeMapApiKey = prefs[Keys.OCM_API_KEY] ?: "",
            footballDataApiKey = prefs[Keys.FOOTBALL_API_KEY] ?: ""
        )
    }

    suspend fun updateJakimZone(zone: String) {
        context.userPrefsDataStore.edit { it[Keys.JAKIM_ZONE] = zone }
    }

    suspend fun updateCarProfile(name: String) {
        context.userPrefsDataStore.edit { it[Keys.CAR_PROFILE] = name.trim().ifEmpty { "My Car" } }
    }

    suspend fun updateTheme(option: AppThemeOption) {
        context.userPrefsDataStore.edit { it[Keys.THEME_OPTION] = option.name }
    }

    suspend fun updateDarkMode(mode: DarkModePreference) {
        context.userPrefsDataStore.edit { it[Keys.DARK_MODE] = mode.name }
    }

    suspend fun updateAzanReminder(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.AZAN_REMINDER] = enabled }
    }

    suspend fun updateApiKeys(ocmKey: String, footballKey: String) {
        context.userPrefsDataStore.edit {
            it[Keys.OCM_API_KEY] = ocmKey.trim()
            it[Keys.FOOTBALL_API_KEY] = footballKey.trim()
        }
    }
}
