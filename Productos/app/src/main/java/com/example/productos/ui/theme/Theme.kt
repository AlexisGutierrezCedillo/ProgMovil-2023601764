// ui/theme/Theme.kt
package com.example.productos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    background = Color(0xFFFFFFFF),
    onBackground = Color.Black
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.Black,
    secondaryContainer = Color(0xFF121212),
    background = Color(0xFF000000),
    onBackground = Color.White
)

@Composable
fun ProductosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = LightColors.primary,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    // Creamos un ColorScheme a partir del Light/Dark y sobreescribimos primary
    val colors = if (darkTheme) {
        DarkColors.copy(primary = accent)
    } else {
        LightColors.copy(primary = accent)
    }

    // Ajuste sencillo de escala de fuente multiplicando en Typography
    val typography = Typography(
        displayLarge = Typography().displayLarge.copy(fontSize = 57.sp * fontScale),
        displayMedium = Typography().displayMedium.copy(fontSize = 45.sp * fontScale),
        displaySmall = Typography().displaySmall.copy(fontSize = 36.sp * fontScale),
        headlineLarge = Typography().headlineLarge.copy(fontSize = 32.sp * fontScale),
        headlineMedium = Typography().headlineMedium.copy(fontSize = 28.sp * fontScale),
        headlineSmall = Typography().headlineSmall.copy(fontSize = 24.sp * fontScale),
        titleLarge = Typography().titleLarge.copy(fontSize = 22.sp * fontScale),
        titleMedium = Typography().titleMedium.copy(fontSize = 16.sp * fontScale),
        titleSmall = Typography().titleSmall.copy(fontSize = 14.sp * fontScale),
        bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp * fontScale),
        bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp * fontScale),
        bodySmall = Typography().bodySmall.copy(fontSize = 12.sp * fontScale),
        labelLarge = Typography().labelLarge.copy(fontSize = 14.sp * fontScale),
        labelMedium = Typography().labelMedium.copy(fontSize = 12.sp * fontScale),
        labelSmall = Typography().labelSmall.copy(fontSize = 11.sp * fontScale)
    )

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(),
        content = content
    )
}