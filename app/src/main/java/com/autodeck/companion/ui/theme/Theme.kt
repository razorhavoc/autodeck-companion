package com.autodeck.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.autodeck.companion.model.AppThemeOption
import com.autodeck.companion.model.DarkModePreference

@Composable
fun AutoDeckTheme(
    themeOption: AppThemeOption,
    darkModePreference: DarkModePreference,
    content: @Composable () -> Unit
) {
    val isDark = when (darkModePreference) {
        DarkModePreference.DARK -> true
        DarkModePreference.LIGHT -> false
        DarkModePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val (primary, secondary) = when (themeOption) {
        AppThemeOption.MIDNIGHT_CARBON -> Color(0xFF94A3B8) to Color(0xFF38BDF8)
        AppThemeOption.EMERALD_CRESCENT -> Color(0xFF14B8A6) to Color(0xFFF59E0B)
        AppThemeOption.OCEAN_DRIVE -> Color(0xFF3B82F6) to Color(0xFF22D3EE)
        AppThemeOption.SUNSET_AMBER -> Color(0xFFF97316) to Color(0xFFFACC15)
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            background = Color(0xFF0B1120),
            surface = Color(0xFF111827),
            surfaceVariant = Color(0xFF1E293B)
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E8F0)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
