package xr.steambridge.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import xr.steambridge.R

/** Steam's own login palette. */
object Steam {
    val Bg0 = Color(0xFF1B2838)     // body navy (top of the ground)
    val Bg1 = Color(0xFF171A21)     // darkest navy (chrome / bottom)
    val Panel = Color(0xFF1B2838)
    val PanelHi = Color(0xFF2A3F5A) // raised surface / inputs
    val Card = Color(0xFF1C5679)    // Steam "steel" card fill (art fallback tile)
    val Line = Color(0xFF316282)    // Steam's hairline blue
    val LineDim = Color(0xFF2A3B4E)
    val Blue = Color(0xFF1A9FFF)    // bright link blue
    val BlueLt = Color(0xFF66C0F4)  // light blue text / accents
    val BtnA = Color(0xFF06BFFF)    // sign-in gradient start
    val BtnB = Color(0xFF2D73BB)    // sign-in gradient end
    val Green = Color(0xFF90BA3C)   // "online" / relay live
    val Text = Color(0xFFC7D5E0)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8F98A0)
    val Faint = Color(0xFF5B6C7D)
    val Danger = Color(0xFFCE4A4A)

    val Ground = Brush.linearGradient(listOf(Color(0xFF1B2838), Color(0xFF171A21)))
    val SignIn = Brush.horizontalGradient(listOf(BtnA, BtnB))
}

private val SteamScheme = darkColorScheme(
    primary = Steam.Blue,
    onPrimary = Steam.White,
    secondary = Steam.Green,
    background = Steam.Bg0,
    onBackground = Steam.Text,
    surface = Steam.Panel,
    onSurface = Steam.Text,
    surfaceVariant = Steam.PanelHi,
    onSurfaceVariant = Steam.Muted,
    error = Steam.Danger,
    outline = Steam.Line,
)

// Arimo — Apache-2.0, metric-compatible with Arial (the family Steam's Motiva Sans is drawn from), so
// text reads like Steam's without shipping Valve's proprietary font.
private val Arimo = FontFamily(
    Font(R.font.arimo, FontWeight.Normal),
    Font(R.font.arimo, FontWeight.Medium),
    Font(R.font.arimo_bold, FontWeight.SemiBold),
    Font(R.font.arimo_bold, FontWeight.Bold),
)

@Composable
fun SteamBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SteamScheme, typography = Typography()) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Arimo),
            content = content,
        )
    }
}
