// Prefs.kt
package com.example.productos

import android.content.Context
import kotlinx.coroutines.flow.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class UserSettings(
    val darkMode: Boolean,
    val fontScale: Float,
    val accentColor: Int
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _darkMode    = MutableStateFlow(sp.getBoolean(KEY_DARK_MODE, false))
    private val _fontScale   = MutableStateFlow(sp.getFloat(KEY_FONT_SCALE, 1f))
    private val _accentColor = MutableStateFlow(sp.getInt(KEY_ACCENT_COLOR, Color(0xFF2196F3).toArgb()))

    val settingsFlow: Flow<UserSettings> = combine(
        _darkMode, _fontScale, _accentColor
    ) { dark, scale, color ->
        UserSettings(dark, scale, color)
    }.distinctUntilChanged()

    fun updateDarkMode(enabled: Boolean) {
        sp.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _darkMode.value = enabled
    }
    fun updateFontScale(scale: Float) {
        sp.edit().putFloat(KEY_FONT_SCALE, scale).apply()
        _fontScale.value = scale
    }
    fun updateAccentColor(color: Int) {
        sp.edit().putInt(KEY_ACCENT_COLOR, color).apply()
        _accentColor.value = color
    }

    companion object {
        private const val KEY_DARK_MODE    = "dark_mode"
        private const val KEY_FONT_SCALE   = "font_scale"
        private const val KEY_ACCENT_COLOR = "accent_color"
    }
}
