package com.simplereso.app.ui

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

private val Indigo = Color(0xFF4340C4)
private val IndigoLight = Color(0xFF8F8CFF)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E4FA),
    onPrimaryContainer = Color(0xFF17146B),
    background = Color(0xFFF1F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E9F1),
    onSurfaceVariant = Color(0xFF5B6274),
    error = Color(0xFFA8231C),
)

private val DarkScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1A1840),
    primaryContainer = Color(0xFF242542),
    onPrimaryContainer = Color(0xFFD9D8FF),
    background = Color(0xFF0D0F15),
    surface = Color(0xFF161922),
    surfaceVariant = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFF949BAE),
    error = Color(0xFFF18B84),
)

@Composable
fun SimpleResoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
