package com.netscan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colores de marca y degradados premium de NetScan. */
object Brand {
    val cyan = Color(0xFF22D3EE)
    val blue = Color(0xFF38BDF8)
    val deep = Color(0xFF0EA5E9)
    val teal = Color(0xFF2DD4BF)
    val violet = Color(0xFF818CF8)

    val gradient = Brush.linearGradient(listOf(cyan, deep))
    val gradientSoft = Brush.linearGradient(listOf(cyan.copy(alpha = 0.22f), violet.copy(alpha = 0.16f)))
    val heroGlow = Brush.verticalGradient(listOf(cyan.copy(alpha = 0.16f), Color.Transparent))

    val good = Color(0xFF4ADE80)
    val warn = Color(0xFFFBBF24)
    val bad = Color(0xFFFB7185)
    val muted = Color(0xFF94A3B8)
}

private val DarkColors = darkColorScheme(
    primary = Brand.cyan,
    onPrimary = Color(0xFF042029),
    primaryContainer = Color(0xFF0C3D4A),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = Brand.teal,
    onSecondary = Color(0xFF04231D),
    secondaryContainer = Color(0xFF123B34),
    onSecondaryContainer = Color(0xFF99F6E4),
    background = Color(0xFF070F1A),
    onBackground = Color(0xFFE6EEF6),
    surface = Color(0xFF0E1C2B),
    onSurface = Color(0xFFE2EAF3),
    surfaceVariant = Color(0xFF17283A),
    onSurfaceVariant = Color(0xFF9FB3C6),
    outline = Color(0xFF284055),
    outlineVariant = Color(0xFF1B2E40),
    error = Brand.bad,
    onError = Color(0xFF2B0710)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEBF5),
    onPrimaryContainer = Color(0xFF002C36),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF4F8FB),
    onBackground = Color(0xFF0D1B2A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1B2A),
    surfaceVariant = Color(0xFFE6EEF4),
    onSurfaceVariant = Color(0xFF4A5B6B),
    outline = Color(0xFFC2D0DC),
    error = Color(0xFFD6455D),
    onError = Color(0xFFFFFFFF)
)

private val PremiumType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
)

@Composable
fun NetScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PremiumType,
        content = content
    )
}
