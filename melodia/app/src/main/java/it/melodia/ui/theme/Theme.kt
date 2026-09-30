package it.melodia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Green = Color(0xFF1DB954)
val Background = Color(0xFF121212)
val Surface = Color(0xFF1E1E1E)
val SurfaceHigh = Color(0xFF2A2A2A)
val TextSecondary = Color(0xFFB3B3B3)

private val colors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    secondary = Green,
    onSecondary = Color.Black,
    background = Background,
    onBackground = Color.White,
    surface = Background,
    onSurface = Color.White,
    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerLow = Surface,
    secondaryContainer = SurfaceHigh,
    onSecondaryContainer = Color.White,
    outline = Color(0xFF535353),
)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val SectionTitle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)

@Composable
fun MelodiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
