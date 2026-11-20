package com.meshcore.repeater.ui.theme

import androidx.compose.ui.graphics.Color

// === Bauhaus Dark Palette ===
val BauhausDarkCarbon = Color(0xFF121316)          // Carbon Background
val BauhausDarkCard = Color(0xFF1C1E22)            // Card / Surface
val BauhausDarkSurfaceVariant = Color(0xFF26292E)  // Elevated / Interactive Surface
val BauhausDarkOutline = Color(0xFF383C44)         // 1.dp Contour Lines
val BauhausDarkSignalRed = Color(0xFFE53935)       // Primary Bauhaus Signal Red
val BauhausDarkOchre = Color(0xFFFFB300)           // Bauhaus Ochre / Yellow (Warnings & Schonmodus)
val BauhausDarkCobalt = Color(0xFF1E88E5)          // Bauhaus Cobalt Blue (Radio & Telemetry)
val BauhausDarkTextPrimary = Color(0xFFF0F1F2)     // Off-white readable text
val BauhausDarkTextSecondary = Color(0xFF9EABA4)   // Technical muted caption text
val BauhausDarkGreen = Color(0xFF00E676)           // Status / OK Indicator

// === Bauhaus Light Palette ===
val BauhausLightOffWhite = Color(0xFFF7F6F2)       // Warm Off-White (Sunlight readability)
val BauhausLightCard = Color(0xFFFFFFFF)           // Pure Crisp White Card
val BauhausLightSurfaceVariant = Color(0xFFEBE9E1) // Warm geometric surface
val BauhausLightOutline = Color(0xFFD0CECA)        // 1.dp Contour Lines
val BauhausLightDeepBlack = Color(0xFF111111)      // Deep Black Primary Accent
val BauhausLightSignalRed = Color(0xFFD32F2F)      // Primary Bauhaus Signal Red
val BauhausLightOchre = Color(0xFFE65100)          // High-contrast Amber / Ochre
val BauhausLightCobalt = Color(0xFF1565C0)         // Deep Cobalt Blue
val BauhausLightTextPrimary = Color(0xFF111111)    // Deep Black Text
val BauhausLightTextSecondary = Color(0xFF5A5D61)  // Dark Grey Text
val BauhausLightGreen = Color(0xFF2E7D32)          // Green OK Indicator

// Legacy compatibility aliases
val EmeraldGreen = BauhausDarkGreen
val EmeraldGreenDark = Color(0xFF00B248)
val CyanAccent = BauhausDarkCobalt
val DeepDarkBg = BauhausDarkCarbon
val SurfaceDark = BauhausDarkCard
val SurfaceVariantDark = BauhausDarkSurfaceVariant
val AlertRed = BauhausDarkSignalRed
val WarningAmber = BauhausDarkOchre
val TextPrimary = BauhausDarkTextPrimary
val TextSecondary = BauhausDarkTextSecondary
val DividerColor = BauhausDarkOutline
