package com.example.knowledgegraph.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NexusPrimaryDark,
    onPrimary = NexusOnPrimaryDark,
    primaryContainer = NexusPrimaryContainerDark,
    onPrimaryContainer = NexusOnPrimaryContainerDark,
    secondary = NexusSecondaryDark,
    onSecondary = NexusOnSecondaryDark,
    secondaryContainer = NexusSecondaryContainerDark,
    onSecondaryContainer = NexusOnSecondaryContainerDark,
    tertiary = NexusTertiaryDark,
    onTertiary = NexusOnTertiaryDark,
    tertiaryContainer = NexusTertiaryContainerDark,
    onTertiaryContainer = NexusOnTertiaryContainerDark,
    background = NexusBackgroundDark,
    onBackground = NexusOnBackgroundDark,
    surface = NexusSurfaceDark,
    onSurface = NexusOnSurfaceDark,
    surfaceVariant = NexusSurfaceVariantDark,
    onSurfaceVariant = NexusOnSurfaceVariantDark,
    outline = NexusOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = NexusPrimaryLight,
    onPrimary = NexusOnPrimaryLight,
    primaryContainer = NexusPrimaryContainerLight,
    onPrimaryContainer = NexusOnPrimaryContainerLight,
    secondary = NexusSecondaryLight,
    onSecondary = NexusOnSecondaryLight,
    secondaryContainer = NexusSecondaryContainerLight,
    onSecondaryContainer = NexusOnSecondaryContainerLight,
    tertiary = NexusTertiaryLight,
    onTertiary = NexusOnTertiaryLight,
    tertiaryContainer = NexusTertiaryContainerLight,
    onTertiaryContainer = NexusOnTertiaryContainerLight,
    background = NexusBackgroundLight,
    onBackground = NexusOnBackgroundLight,
    surface = NexusSurfaceLight,
    onSurface = NexusOnSurfaceLight,
    surfaceVariant = NexusSurfaceVariantLight,
    onSurfaceVariant = NexusOnSurfaceVariantLight,
    outline = NexusOutlineLight
)

@Composable
fun KnowledgeGraphTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexusTypography,
        content = content
    )
}
