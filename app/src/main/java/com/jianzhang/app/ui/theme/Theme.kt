package com.jianzhang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val JianzhangLightColors = lightColorScheme(
    primary = Color(0xFFE9A900),
    onPrimary = Color(0xFF2A1C00),
    primaryContainer = Color(0xFFFFE49A),
    onPrimaryContainer = Color(0xFF2A1C00),
    secondary = Color(0xFF765A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDBE),
    onSecondaryContainer = Color(0xFF291D00),
    tertiary = Color(0xFF8A4F2A),
    onTertiary = Color.White,
    background = Color(0xFFFFF9EF),
    onBackground = Color(0xFF221B10),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF221B10),
    surfaceVariant = Color(0xFFF4EBDD),
    onSurfaceVariant = Color(0xFF655C4D),
    outline = Color(0xFF8F826D),
    outlineVariant = Color(0xFFE1D5C0),
    error = Color(0xFFBA1A1A)
)

private val JianzhangDarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D),
    onPrimary = Color(0xFF3E2E00),
    primaryContainer = Color(0xFF5A4300),
    onPrimaryContainer = Color(0xFFFFE49A),
    secondary = Color(0xFFE8C55E),
    onSecondary = Color(0xFF3D3000),
    secondaryContainer = Color(0xFF554500),
    onSecondaryContainer = Color(0xFFFFE9A8),
    background = Color(0xFF17130D),
    onBackground = Color(0xFFF0E7D7),
    surface = Color(0xFF201B13),
    onSurface = Color(0xFFF0E7D7),
    surfaceVariant = Color(0xFF3A3328),
    onSurfaceVariant = Color(0xFFD2C6B4),
    outline = Color(0xFF9B8F7A),
    outlineVariant = Color(0xFF4E4639)
)

private val JianzhangTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
)

private val JianzhangShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun JianzhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) JianzhangDarkColors else JianzhangLightColors,
        typography = JianzhangTypography,
        shapes = JianzhangShapes,
        content = content
    )
}
