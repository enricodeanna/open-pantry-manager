package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.studiodeanna.openpantrymanager.store.ThemeChoice

// Open Check Lists' layout in warmer, wooden-shelf colours, so the two apps tell apart at a glance.
// Tertiary is the "use soon" warning colour.
private val Light = lightColorScheme(
    primary = Color(0xFF855318),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBD),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    secondaryContainer = Color(0xFFFEDCBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFFB3471A),
    tertiaryContainer = Color(0xFFFFDBCE),
    onTertiaryContainer = Color(0xFF380D00),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    surfaceContainerLow = Color(0xFFFBF2EB),
    surfaceContainer = Color(0xFFF5ECE5),
    surfaceContainerHigh = Color(0xFFEFE6DF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB875),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBD),
    secondary = Color(0xFFE1C1A4),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDCBE),
    tertiary = Color(0xFFFFB59A),
    tertiaryContainer = Color(0xFF7D2D0A),
    onTertiaryContainer = Color(0xFFFFDBCE),
    background = Color(0xFF19120C),
    surface = Color(0xFF19120C),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF312822),
)

/** Whether this choice means dark colors right now. */
@Composable
fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.System -> isSystemInDarkTheme()
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

@Composable
fun PantryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
