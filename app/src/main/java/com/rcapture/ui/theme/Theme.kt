package com.rcapture.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CF4FF),
    secondary = CyberBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF003DA5),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = CyberEmerald,
    onTertiary = Color(0xFF003823),
    tertiaryContainer = Color(0xFF005235),
    onTertiaryContainer = Color(0xFF67FEBE),
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceLevel1,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLevel2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceLevel1,
    surfaceContainer = SurfaceLevel1,
    surfaceContainerHigh = SurfaceLevel2,
    surfaceContainerHighest = Color(0xFF202A40),
    error = CyberCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = BorderSubtle,
    outlineVariant = Color(0xFF26334D)
)

private val FuturisticLightColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF4FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = CyberBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001945),
    tertiary = Color(0xFF006C47),
    onTertiary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF10151E),
    surface = Color.White,
    onSurface = Color(0xFF10151E),
    surfaceVariant = Color(0xFFE2E7F0),
    onSurfaceVariant = Color(0xFF424B5A),
    error = CyberCrimson,
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Futuristic dark theme by default
    dynamicColor: Boolean = false, // Keep branded cyber-workstation colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FuturisticDarkColorScheme
        else -> FuturisticLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
