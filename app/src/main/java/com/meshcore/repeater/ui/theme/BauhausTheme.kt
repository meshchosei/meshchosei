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

// Geometric Bauhaus shapes: strict 4.dp radii
val BauhausCornerShape = RoundedCornerShape(4.dp)

val BauhausShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp)
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
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) BauhausDarkColorScheme else BauhausLightColorScheme

    val customColors = if (isDark) {
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
    } else {
        BauhausCustomColors(
            signalRed = BauhausLightSignalRed,
            ochre = BauhausLightOchre,
            cobalt = BauhausLightCobalt,
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
