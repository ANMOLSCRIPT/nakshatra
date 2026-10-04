package com.nakshatra.heritage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nakshatra.heritage.R
import com.nakshatra.heritage.data.ThemeMode
import com.nakshatra.heritage.voice.VoiceState

// The website's palette (frontend/css/styles.css :root).
object Brand {
    val Ink = Color(0xFF140D12)
    val Ink2 = Color(0xFF1C1219)
    val Ink3 = Color(0xFF281A22)
    val Night = Color(0xFF0D0B18)
    val NightGlow = Color(0xFF1D1A3A)
    val Ivory = Color(0xFFF7EFDE)
    val Paper = Color(0xFFF3E8D2)
    val Sand = Color(0xFFE6D6B6)
    val Gold = Color(0xFFE9B44C)
    val GoldBright = Color(0xFFF3C867)
    val GoldDeep = Color(0xFFB9822A)
    val Bronze = Color(0xFF8A5A17)
    val Saffron = Color(0xFFE2782A)
    val Vermilion = Color(0xFFC8402B)
    val Peacock = Color(0xFF2A9D9A)
    val OnInk = Color(0xFFF4E9D3)
    val OnInkMuted = Color(0xFFB8A78E)
    val OnPaper = Color(0xFF2A1912)
    val OnPaperMuted = Color(0xFF6B574A)
    val Live = Color(0xFF58C88A)
}

private val DarkColors = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Color(0xFF2A1706),
    primaryContainer = Color(0xFF3B2A14),
    onPrimaryContainer = Color(0xFFF6DFA8),
    secondary = Brand.Saffron,
    onSecondary = Color(0xFF2A1306),
    secondaryContainer = Color(0xFF45230F),
    onSecondaryContainer = Color(0xFFFFD9BD),
    tertiary = Brand.Peacock,
    onTertiary = Color(0xFF04201F),
    tertiaryContainer = Color(0xFF123B3A),
    onTertiaryContainer = Color(0xFFB4ECEA),
    error = Color(0xFFEF7A64),
    onError = Color(0xFF3A0B04),
    errorContainer = Color(0xFF4A1A12),
    onErrorContainer = Color(0xFFFFD4CB),
    background = Brand.Ink,
    onBackground = Brand.OnInk,
    surface = Brand.Ink,
    onSurface = Brand.OnInk,
    surfaceVariant = Brand.Ink3,
    onSurfaceVariant = Brand.OnInkMuted,
    surfaceContainerLowest = Color(0xFF100A0E),
    surfaceContainerLow = Brand.Ink2,
    surfaceContainer = Color(0xFF21151D),
    surfaceContainerHigh = Brand.Ink3,
    surfaceContainerHighest = Color(0xFF33222C),
    outline = Color(0xFF6B5843),
    outlineVariant = Color(0xFF3A2A24),
    inverseSurface = Brand.Ivory,
    inverseOnSurface = Brand.OnPaper,
    inversePrimary = Brand.Bronze,
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = Brand.Bronze,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D9A0),
    onPrimaryContainer = Color(0xFF3A2405),
    secondary = Color(0xFFA9500F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7D3B4),
    onSecondaryContainer = Color(0xFF3F1C03),
    tertiary = Color(0xFF1B6F6C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFE6E3),
    onTertiaryContainer = Color(0xFF07302E),
    error = Color(0xFF9D2F1D),
    onError = Color.White,
    errorContainer = Color(0xFFF6D2C9),
    onErrorContainer = Color(0xFF45100A),
    background = Brand.Ivory,
    onBackground = Brand.OnPaper,
    surface = Brand.Ivory,
    onSurface = Brand.OnPaper,
    surfaceVariant = Brand.Sand,
    onSurfaceVariant = Brand.OnPaperMuted,
    surfaceContainerLowest = Color(0xFFFFFBF2),
    surfaceContainerLow = Color(0xFFFBF4E5),
    surfaceContainer = Brand.Paper,
    surfaceContainerHigh = Color(0xFFEDDFC4),
    surfaceContainerHighest = Brand.Sand,
    outline = Color(0xFF8E7A62),
    outlineVariant = Color(0xFFD9C7A5),
    inverseSurface = Brand.Ink3,
    inverseOnSurface = Brand.OnInk,
    inversePrimary = Brand.Gold,
    scrim = Color.Black,
)

/** Colours the Material scheme has no slot for. */
@Immutable
data class NakshatraColors(
    val dark: Boolean,
    /** Hero background stops. */
    val heroTop: Color,
    val heroBottom: Color,
    val star: Color,
    /** Hairline used on cards -- the website's gold-tinted border. */
    val hairline: Color,
    val live: Color,
    val edgeChip: Color,
    val onEdgeChip: Color,
    val cloudChip: Color,
    val onCloudChip: Color,
) {
    /** The voice accent: gold, saffron or peacock depending on the pipeline state. */
    fun accent(state: VoiceState): Color = when (state) {
        VoiceState.IDLE -> if (dark) Color(0xFF8F8371) else Color(0xFF8E7A62)
        VoiceState.ARMED, VoiceState.ANSWERING -> if (dark) Brand.Gold else Brand.GoldDeep
        VoiceState.WAKE -> if (dark) Color(0xFFFFD98A) else Brand.Gold
        VoiceState.LISTENING -> Brand.Saffron
        VoiceState.PROCESSING -> Brand.Peacock
    }
}

private val DarkExtras = NakshatraColors(
    dark = true,
    heroTop = Color(0xFF170E14), heroBottom = Color(0xFF1D1118), star = Color(0xFFF6DFA8),
    hairline = Brand.Gold.copy(alpha = 0.18f), live = Brand.Live,
    edgeChip = Brand.Vermilion.copy(alpha = 0.22f), onEdgeChip = Color(0xFFF2A392),
    cloudChip = Brand.Peacock.copy(alpha = 0.22f), onCloudChip = Color(0xFF8ADAD7),
)
private val LightExtras = NakshatraColors(
    dark = false,
    heroTop = Color(0xFFFBF4E5), heroBottom = Brand.Paper, star = Brand.GoldDeep,
    hairline = Color(0xFF5C3818).copy(alpha = 0.18f), live = Color(0xFF2E8B57),
    edgeChip = Brand.Vermilion.copy(alpha = 0.12f), onEdgeChip = Color(0xFF9D2F1D),
    cloudChip = Brand.Peacock.copy(alpha = 0.14f), onCloudChip = Color(0xFF1B6F6C),
)

val LocalNakshatraColors = staticCompositionLocalOf { DarkExtras }

val Serif = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_semibold, FontWeight.Medium),
    Font(R.font.cormorant_bold, FontWeight.Bold),
    Font(R.font.cormorant_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.cormorant_medium_italic, FontWeight.SemiBold, FontStyle.Italic),
)
val Sans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
)

private fun serif(size: Int, line: Int) =
    TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = line.sp)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) =
    TextStyle(fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em)

private val AppTypography = Typography(
    displayLarge = serif(48, 50),
    displayMedium = serif(40, 43),
    displaySmall = serif(34, 37),
    headlineLarge = serif(32, 35),
    headlineMedium = serif(28, 31),
    headlineSmall = serif(25, 28),
    titleLarge = serif(22, 26),
    titleMedium = sans(16, 22, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(17, 27),
    bodyMedium = sans(15, 23),
    bodySmall = sans(13, 19),
    labelLarge = sans(15, 20, FontWeight.SemiBold),
    labelMedium = sans(13, 18, FontWeight.Medium),
    labelSmall = sans(11, 15, FontWeight.SemiBold, 0.14),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun NakshatraTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    CompositionLocalProvider(LocalNakshatraColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val nk: NakshatraColors
    @Composable get() = LocalNakshatraColors.current
