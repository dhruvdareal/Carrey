package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = RentalBluePrimary,
    onPrimary = RentalBlueOnPrimary,
    primaryContainer = RentalBluePrimaryContainer,
    onPrimaryContainer = RentalBlueOnPrimaryContainer,
    secondary = RentalSkySecondary,
    onSecondary = RentalSkyOnSecondary,
    secondaryContainer = RentalSkySecondaryContainer,
    onSecondaryContainer = RentalSkyOnSecondaryContainer,
    tertiary = RentalTealTertiary,
    onTertiary = RentalTealOnTertiary,
    tertiaryContainer = RentalTealTertiaryContainer,
    onTertiaryContainer = RentalTealOnTertiaryContainer,
    error = RentalError,
    onError = RentalOnError,
    errorContainer = RentalErrorContainer,
    onErrorContainer = RentalOnErrorContainer,
    background = RentalBackground,
    onBackground = RentalOnBackground,
    surface = RentalSurface,
    onSurface = RentalOnSurface,
    surfaceVariant = RentalSurfaceVariant,
    onSurfaceVariant = RentalOnSurfaceVariant,
    outline = RentalOutline,
    outlineVariant = RentalOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = RentalBlueDarkPrimary,
    onPrimary = RentalBlueDarkOnPrimary,
    primaryContainer = RentalBlueDarkPrimaryContainer,
    onPrimaryContainer = RentalBlueDarkOnPrimaryContainer,
    secondary = RentalSkyDarkSecondary,
    onSecondary = RentalSkyDarkOnSecondary,
    secondaryContainer = RentalSkyDarkSecondaryContainer,
    onSecondaryContainer = RentalSkyDarkOnSecondaryContainer,
    tertiary = RentalTealDarkTertiary,
    onTertiary = RentalTealDarkOnTertiary,
    tertiaryContainer = RentalTealDarkTertiaryContainer,
    onTertiaryContainer = RentalTealDarkOnTertiaryContainer,
    background = RentalDarkBackground,
    onBackground = RentalDarkOnBackground,
    surface = RentalDarkSurface,
    onSurface = RentalDarkOnSurface,
    surfaceVariant = RentalDarkSurfaceVariant,
    onSurfaceVariant = RentalDarkOnSurfaceVariant,
    outline = RentalDarkOutline,
    outlineVariant = RentalDarkOutlineVariant
)

val CarRentalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CarRentalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CarRentalShapes,
        content = content
    )
}
