package com.tlog.ui.theme

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
import com.tlog.data.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0D6EFD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565F71),
    background = Color(0xFFFBFBFB),
    surface = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6BA8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004887),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFBEC6DC),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E)
)

private fun oledBlackify(scheme: androidx.compose.material3.ColorScheme) = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF161616)
)

@Composable
fun TLogTheme(
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    oledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var scheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    if (dark && oledBlack) scheme = oledBlackify(scheme)
    MaterialTheme(colorScheme = scheme, content = content)
}
