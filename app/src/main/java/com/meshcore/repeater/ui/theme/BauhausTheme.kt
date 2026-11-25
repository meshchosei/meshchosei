package com.meshcore.repeater.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshcore.repeater.model.ThemeMode

enum class BauhausPalette(val primary: Color, val secondary: Color, val tertiary: Color) {
    BAUHAUS(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFBC02D)),
    TACTICAL(Color(0xFFFF9800), Color(0xFF43A047), Color(0xFFFFD54F)),
    NORDIC(Color(0xFF00ACC1), Color(0xFF3949AB), Color(0xFF26A69A)),
    STEALTH(Color(0xFFAB47BC), Color(0xFF00BCD4), Color(0xFFFF7043))
}

val LocalBauhausPalette = androidx.compose.runtime.compositionLocalOf { androidx.compose.runtime.mutableStateOf(BauhausPalette.BAUHAUS) }

@Immutable
data class BauhausCustomColors(
    val signalRed: Color,
    val ochre: Color,
    val cobalt: Color,
    val successGreen: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val surfaceVariant: Color,
    val isDark: Boolean
)

val LocalBauhausColors = staticCompositionLocalOf {
    BauhausCustomColors(
        signalRed = BauhausDarkSignalRed,
        ochre = BauhausDarkOchre,
        cobalt = BauhausDarkCobalt,
        successGreen = BauhausDarkGreen,
        cardBackground = BauhausDarkCard,
        cardBorder = BauhausDarkOutline,
        surfaceVariant = BauhausDarkSurfaceVariant,
        isDark = true
    )
}

// Geometrische Bauhaus-Form: BauhausCornerShape = 2.dp – einzige Form für Cards, Buttons, Chips, Felder,
// Badges und Dialoge (STYLE_GUIDE.md §1.2). Seit v1.09 liegen auch alle Material-Shapes auf 2.dp, damit
// Komponenten ohne explizites shape (z. B. Menüs, Snackbars) nicht wieder runder werden.
val BauhausCornerShape = RoundedCornerShape(2.dp)

val BauhausShapes = Shapes(
    extraSmall = BauhausCornerShape,
    small = BauhausCornerShape,
    medium = BauhausCornerShape,
    large = BauhausCornerShape,
    extraLarge = BauhausCornerShape
)

private val BauhausDarkColorScheme = darkColorScheme(
    primary = BauhausDarkSignalRed,
    onPrimary = Color.White,
    primaryContainer = BauhausDarkSurfaceVariant,
    onPrimaryContainer = BauhausDarkSignalRed,
    secondary = BauhausDarkCobalt,
    onSecondary = Color.White,
    tertiary = BauhausDarkOchre,
    onTertiary = BauhausDarkCarbon,
    background = BauhausDarkCarbon,
    onBackground = BauhausDarkTextPrimary,
    surface = BauhausDarkCard,
    onSurface = BauhausDarkTextPrimary,
    surfaceVariant = BauhausDarkSurfaceVariant,
    onSurfaceVariant = BauhausDarkTextSecondary,
    outline = BauhausDarkOutline,
    outlineVariant = BauhausDarkOutline.copy(alpha = 0.5f),
    error = BauhausDarkSignalRed,
    onError = Color.White
)

private val BauhausLightColorScheme = lightColorScheme(
    primary = BauhausLightSignalRed,
    onPrimary = Color.White,
    primaryContainer = BauhausLightSurfaceVariant,
    onPrimaryContainer = BauhausLightSignalRed,
    secondary = BauhausLightCobalt,
    onSecondary = Color.White,
    tertiary = BauhausLightOchre,
    onTertiary = Color.White,
    background = BauhausLightOffWhite,
    onBackground = BauhausLightTextPrimary,
    surface = BauhausLightCard,
    onSurface = BauhausLightTextPrimary,
    surfaceVariant = BauhausLightSurfaceVariant,
    onSurfaceVariant = BauhausLightTextSecondary,
    outline = BauhausLightOutline,
    outlineVariant = BauhausLightOutline.copy(alpha = 0.5f),
    error = BauhausLightSignalRed,
    onError = Color.White
)

@Composable
fun BauhausTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val activePalette = LocalBauhausPalette.current.value
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = (if (isDark) BauhausDarkColorScheme else BauhausLightColorScheme).copy(primary = activePalette.primary, secondary = activePalette.secondary, tertiary = activePalette.tertiary)

    val customColors = if (isDark) {
        BauhausCustomColors(
            signalRed = activePalette.primary,
            ochre = activePalette.tertiary,
            cobalt = activePalette.secondary,
            successGreen = BauhausDarkGreen,
            cardBackground = BauhausDarkCard,
            cardBorder = BauhausDarkOutline,
            surfaceVariant = BauhausDarkSurfaceVariant,
            isDark = true
        )
    } else {
        BauhausCustomColors(
            signalRed = activePalette.primary,
            ochre = activePalette.tertiary,
            cobalt = activePalette.secondary,
            successGreen = BauhausLightGreen,
            cardBackground = BauhausLightCard,
            cardBorder = BauhausLightOutline,
            surfaceVariant = BauhausLightSurfaceVariant,
            isDark = false
        )
    }

    CompositionLocalProvider(
        LocalBauhausColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = BauhausShapes,
            typography = Typography,
            content = content
        )
    }
}

// Bauhaus Helper methods
@Composable
fun bauhausCardBorder(color: Color = MaterialTheme.colorScheme.outline): BorderStroke =
    BorderStroke(1.dp, color)

@Composable
fun bauhausCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
