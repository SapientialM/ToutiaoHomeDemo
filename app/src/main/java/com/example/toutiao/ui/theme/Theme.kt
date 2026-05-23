package com.example.toutiao.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = RedMain,
    onPrimary = White,
    primaryContainer = RedLight,
    secondary = RedDark,
    background = Background,
    surface = CardBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Divider,
)

private val DarkColorScheme = darkColorScheme(
    primary = RedLight,
    onPrimary = White,
    primaryContainer = RedDark,
    secondary = RedLight,
    background = DarkBackground,
    surface = DarkCard,
    onBackground = White,
    onSurface = White,
    outline = DarkDivider,
)

@Composable
fun ToutiaoFeedDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ToutiaoTypography,
        content = content,
    )
}
