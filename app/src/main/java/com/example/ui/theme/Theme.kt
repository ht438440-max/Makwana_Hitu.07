package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = TextPrimaryDark,
    primaryContainer = ElevatedNavy,
    onPrimaryContainer = CyanGlow,
    secondary = VividPurple,
    onSecondary = TextPrimaryDark,
    secondaryContainer = ColorTokens.PurpleContainerDark,
    onSecondaryContainer = NeonViolet,
    tertiary = CyanGlow,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimaryDark,
    surface = DeepMidnight,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderBlue,
    outlineVariant = ColorTokens.SubtleBorderDark,
    error = LiveCrimson,
    onError = TextPrimaryDark
)

private val StudioLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = LightSurface,
    primaryContainer = LightElevated,
    onPrimaryContainer = LightTextPrimary,
    secondary = VividPurple,
    onSecondary = LightSurface,
    secondaryContainer = ColorTokens.PurpleContainerLight,
    onSecondaryContainer = VividPurple,
    tertiary = CyanGlow,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = ColorTokens.SubtleBorderLight,
    error = LiveCrimson,
    onError = LightSurface
)

private object ColorTokens {
    val PurpleContainerDark = androidx.compose.ui.graphics.Color(0xFF28194D)
    val SubtleBorderDark = androidx.compose.ui.graphics.Color(0xFF1A254B)
    val PurpleContainerLight = androidx.compose.ui.graphics.Color(0xFFEDE9FE)
    val SubtleBorderLight = androidx.compose.ui.graphics.Color(0xFFCBD5E1)
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MakwanaHituTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
