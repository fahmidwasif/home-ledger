package nz.afhome.ledger.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nz.afhome.ledger.data.Person

// A light Hogwarts touch: Gryffindor burgundy & gold on parchment. Data colours stay on the validated chart palette.
private val Light = lightColorScheme(
    primary = Color(0xFF7A1F2B), onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DADB), onPrimaryContainer = Color(0xFF3B0710),
    secondary = Color(0xFF8A6412), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E6BC), onSecondaryContainer = Color(0xFF2C1E00),
    tertiary = Color(0xFF2A78D6), tertiaryContainer = Color(0xFFD8E6FA),
    background = Color(0xFFF6F1E4), surface = Color(0xFFFCFAF4), surfaceVariant = Color(0xFFEDE6D3),
    surfaceContainer = Color(0xFFF1EBDC), surfaceContainerHigh = Color(0xFFEAE2CF), surfaceContainerLow = Color(0xFFF8F4EA),
    error = Color(0xFFD03B3B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE9B949), onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF5C1520), onPrimaryContainer = Color(0xFFF6DADB),
    secondary = Color(0xFFE9B949), onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF4A3708), onSecondaryContainer = Color(0xFFF7E6BC),
    tertiary = Color(0xFF3987E5), tertiaryContainer = Color(0xFF16335A),
    background = Color(0xFF121110), surface = Color(0xFF1B1A18), surfaceVariant = Color(0xFF2C2A26),
    surfaceContainer = Color(0xFF23211E), surfaceContainerHigh = Color(0xFF2C2A26), surfaceContainerLow = Color(0xFF1E1C1A),
    error = Color(0xFFE66767),
)

/** Chart colours: one hue for magnitude, fixed categorical slots for people (from the validated reference palette). */
@Immutable
data class ChartColors(val bar: Color, val barMuted: Color, val grid: Color, val person: Map<Person, Color>, val good: Color, val critical: Color, val warning: Color)

private val LightChart = ChartColors(
    bar = Color(0xFF2A78D6), barMuted = Color(0xFF9EC5F4), grid = Color(0xFFE4E2DC),
    person = mapOf(Person.ANIKA to Color(0xFF2A78D6), Person.FAHMID to Color(0xFFEB6834), Person.BOTH to Color(0xFF1BAF7A)),
    good = Color(0xFF0CA30C), critical = Color(0xFFD03B3B), warning = Color(0xFFFAB219),
)
private val DarkChart = ChartColors(
    bar = Color(0xFF3987E5), barMuted = Color(0xFF184F95), grid = Color(0xFF383835),
    person = mapOf(Person.ANIKA to Color(0xFF3987E5), Person.FAHMID to Color(0xFFD95926), Person.BOTH to Color(0xFF199E70)),
    good = Color(0xFF0CA30C), critical = Color(0xFFD03B3B), warning = Color(0xFFFAB219),
)

val LocalChart = staticCompositionLocalOf { LightChart }

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalChart provides if (dark) DarkChart else LightChart) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
