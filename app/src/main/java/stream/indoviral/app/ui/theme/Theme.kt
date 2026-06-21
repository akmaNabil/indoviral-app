package stream.indoviral.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand500 = Color(0xFFE50914)
val Brand600 = Color(0xFFDC2626)
val Brand700 = Color(0xFFB91C1C)

val DarkBackground = Color(0xFF141414)
val DarkSurface = Color(0xFF1F1F1F)
val DarkSurfaceVariant = Color(0xFF333333)
val DarkBorder = Color(0xFF404040)
val DarkOnSurface = Color.White
val DarkOnSurfaceVariant = Color(0xFF999999)

private val DarkColorScheme = darkColorScheme(
    primary = Brand500,
    onPrimary = Color.White,
    primaryContainer = Brand600,
    onPrimaryContainer = Color.White,
    secondary = Brand500,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder,
    error = Brand500,
    onError = Color.White
)

@Composable
fun IndoViralTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
