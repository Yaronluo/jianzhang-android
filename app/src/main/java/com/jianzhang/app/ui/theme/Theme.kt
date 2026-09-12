package com.jianzhang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JianzhangLightColors = lightColorScheme(
    primary = Color(0xFF2D6A5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2EBDD),
    onPrimaryContainer = Color(0xFF082019),
    secondary = Color(0xFF5C6370),
    secondaryContainer = Color(0xFFE0E4EC),
    background = Color(0xFFF8FAF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEE9),
    error = Color(0xFFBA1A1A)
)

private val JianzhangDarkColors = darkColorScheme(
    primary = Color(0xFFA5D6C3),
    onPrimary = Color(0xFF0C392E),
    primaryContainer = Color(0xFF205042),
    onPrimaryContainer = Color(0xFFC2EBDD),
    secondary = Color(0xFFC2C7D0),
    background = Color(0xFF101412),
    surface = Color(0xFF171B18),
    surfaceVariant = Color(0xFF3F4944)
)

@Composable
fun JianzhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) JianzhangDarkColors else JianzhangLightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
