package com.neteinstein.smsredirect.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = BrandPrimary,
        onPrimary = BrandOnPrimary,
        primaryContainer = BrandOnPrimaryContainer,
        onPrimaryContainer = BrandPrimaryContainer,
        secondary = BrandSecondaryContainer,
        onSecondary = BrandOnSecondaryContainer,
        secondaryContainer = BrandOnSecondaryContainer,
        onSecondaryContainer = BrandSecondaryContainer,
        tertiary = BrandTertiaryContainer,
        onTertiary = BrandOnTertiaryContainer,
        tertiaryContainer = BrandOnTertiaryContainer,
        onTertiaryContainer = BrandTertiaryContainer,
        error = BrandErrorContainer,
        onError = BrandOnErrorContainer,
        errorContainer = BrandOnErrorContainer,
        onErrorContainer = BrandErrorContainer,
        background = BrandBackgroundDark,
        onBackground = BrandOnBackgroundDark,
        surface = BrandSurfaceDark,
        onSurface = BrandOnSurfaceDark,
        surfaceVariant = BrandSurfaceVariantDark,
        onSurfaceVariant = BrandOnSurfaceVariantDark,
        outline = BrandOutlineDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = BrandPrimary,
        onPrimary = BrandOnPrimary,
        primaryContainer = BrandPrimaryContainer,
        onPrimaryContainer = BrandOnPrimaryContainer,
        secondary = BrandSecondary,
        onSecondary = BrandOnSecondary,
        secondaryContainer = BrandSecondaryContainer,
        onSecondaryContainer = BrandOnSecondaryContainer,
        tertiary = BrandTertiary,
        onTertiary = BrandOnTertiary,
        tertiaryContainer = BrandTertiaryContainer,
        onTertiaryContainer = BrandOnTertiaryContainer,
        error = BrandError,
        onError = BrandOnError,
        errorContainer = BrandErrorContainer,
        onErrorContainer = BrandOnErrorContainer,
        background = BrandBackgroundLight,
        onBackground = BrandOnBackgroundLight,
        surface = BrandSurfaceLight,
        onSurface = BrandOnSurfaceLight,
        surfaceVariant = BrandSurfaceVariantLight,
        onSurfaceVariant = BrandOnSurfaceVariantLight,
        outline = BrandOutlineLight,
    )

@Composable
fun SmsRedirectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColorScheme
            }

            else -> {
                LightColorScheme
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmsRedirectTypography,
        shapes = SmsRedirectShapes,
        content = content,
    )
}
