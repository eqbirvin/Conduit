package com.conduit.app.ui.theme

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

@Composable
fun ConduitTheme(themePreference: Int, jacobMonochrome: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themePreference) {
        1 -> false // Light
        2 -> true  // Dark
        3 -> true  // Jacob Mode (AMOLED)
        else -> isSystemDark // System (0)
    }
    
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themePreference != 3
    val colorScheme = when {
        themePreference == 3 -> {
            if (jacobMonochrome) {
                darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF1C1C1E),
                    onPrimaryContainer = Color.White,
                    secondary = Color.LightGray,
                    onSecondary = Color.Black,
                    secondaryContainer = Color(0xFF1C1C1E),
                    onSecondaryContainer = Color.White,
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color.Black,
                    onSurface = Color.White,
                    surfaceVariant = Color.Black,
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    outline = Color.White.copy(alpha = 0.5f),
                    outlineVariant = Color.White.copy(alpha = 0.2f)
                )
            } else {
                val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(context)
                } else {
                    darkColorScheme()
                }
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color.Black
                )
            }
        }
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
