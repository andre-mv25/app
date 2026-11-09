package com.utc.guerrarobots.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * La app siempre es oscura (nunca fondo blanco), sin importar el tema del teléfono.
 * Las tarjetas son semitransparentes para que se vea el fondo; los diálogos y menús, opacos.
 */
private val EsquemaOscuro = darkColorScheme(
    primary = NeonCian,
    onPrimary = Color(0xFF00212B),
    primaryContainer = Color(0xFF00465A),
    onPrimaryContainer = Color(0xFFBDEEFF),
    secondary = NeonNaranja,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = Color(0xFF4A2E00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = NeonMagenta,
    onTertiary = Color(0xFF3A0050),
    tertiaryContainer = Color(0xFF4A0E66),
    onTertiaryContainer = Color(0xFFF3D3FF),
    background = Color(0xFF070B18),
    onBackground = TextoClaro,
    surface = Color(0xFF0E1530),
    onSurface = TextoClaro,
    surfaceVariant = Color(0xFF1B2547),
    onSurfaceVariant = TextoApagado,
    surfaceContainer = Color(0xFF111A38),
    surfaceContainerHigh = Color(0xFF16204A),
    surfaceContainerHighest = Color(0xD91B2656),
    outline = Color(0xFF5A6BA0),
    outlineVariant = Color(0xFF2A3560),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
)

@Composable
fun GuerraRobotsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaOscuro,
        typography = Typography,
        content = content,
    )
}
