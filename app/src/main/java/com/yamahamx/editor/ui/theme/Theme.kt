package com.yamahamx.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = YamahaCyan,
    onPrimary = Color.Black,
    primaryContainer = YamahaCyanDim,
    onPrimaryContainer = Color.White,
    secondary = SynthAmber,
    onSecondary = Color.Black,
    tertiary = SynthGreen,
    background = SynthDarkBackground,
    onBackground = SynthLightText,
    surface = SynthCardBackground,
    onSurface = SynthLightText,
    surfaceVariant = SynthPanelSurface,
    onSurfaceVariant = SynthMutedGray,
    outline = SynthCardBorder,
    error = SynthRed
)

@Composable
fun YamahaMXEditorTheme(
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = 1.0f
        )
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
