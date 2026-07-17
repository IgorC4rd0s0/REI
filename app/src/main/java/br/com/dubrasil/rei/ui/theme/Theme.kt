package br.com.dubrasil.rei.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ReiThemeMode(val value: String, val label: String) {
    System("system", "Sistema"),
    Light("light", "Claro"),
    Dark("dark", "Escuro");

    companion object {
        fun fromValue(value: String?): ReiThemeMode = entries.firstOrNull { it.value == value } ?: System
    }
}

private val ReiLightColors = lightColorScheme(
    primary = Color(0xFF263A7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF5AAE45),
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF1F7),
    outline = Color(0xFFD7DBE5)
)

private val ReiDarkColors = darkColorScheme(
    primary = Color(0xFFAFC0FF),
    onPrimary = Color(0xFF0B1B4F),
    primaryContainer = Color(0xFF30478F),
    onPrimaryContainer = Color(0xFFF1F4FF),
    secondary = Color(0xFF91DA7D),
    onSecondary = Color(0xFF12380D),
    background = Color(0xFF080E1B),
    onBackground = Color(0xFFF7F9FF),
    surface = Color(0xFF151D2D),
    onSurface = Color(0xFFF7F9FF),
    surfaceVariant = Color(0xFF222D42),
    onSurfaceVariant = Color(0xFFD4DBEA),
    outline = Color(0xFF5B6884),
    outlineVariant = Color(0xFF414D66),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val ReiTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

private val ReiShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

@Composable
fun ReiTheme(themeMode: ReiThemeMode = ReiThemeMode.System, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ReiThemeMode.System -> isSystemInDarkTheme()
        ReiThemeMode.Light -> false
        ReiThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) ReiDarkColors else ReiLightColors,
        typography = ReiTypography,
        shapes = ReiShapes,
        content = content
    )
}
