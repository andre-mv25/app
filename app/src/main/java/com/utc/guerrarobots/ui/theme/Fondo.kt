package com.utc.guerrarobots.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * El fondo de toda la app: degradado nocturno con luces de neón, una cuadrícula tenue y
 * "pistas de circuito" como las de un robot. Es estático: no gasta batería.
 */
@Composable
fun FondoRobots(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoArriba, FondoMedio, FondoAbajo)))
            .drawBehind { dibujarDecorado() },
    ) {
        // Todo el texto de la app sale en claro por defecto (nunca negro sobre el fondo oscuro).
        CompositionLocalProvider(LocalContentColor provides TextoClaro) { content() }
    }
}

private fun DrawScope.dibujarDecorado() {
    val ancho = size.width
    val alto = size.height

    // Luces de neón en las esquinas.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(NeonCian.copy(alpha = 0.24f), Color.Transparent),
            center = Offset(ancho * 0.05f, alto * 0.04f),
            radius = ancho * 0.95f,
        ),
        radius = ancho * 0.95f,
        center = Offset(ancho * 0.05f, alto * 0.04f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(NeonMagenta.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(ancho * 0.98f, alto * 0.96f),
            radius = ancho * 1.0f,
        ),
        radius = ancho * 1.0f,
        center = Offset(ancho * 0.98f, alto * 0.96f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(NeonNaranja.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(ancho * 1.0f, alto * 0.38f),
            radius = ancho * 0.6f,
        ),
        radius = ancho * 0.6f,
        center = Offset(ancho * 1.0f, alto * 0.38f),
    )

    // Cuadrícula tenue.
    val paso = 40.dp.toPx()
    val linea = Color.White.copy(alpha = 0.035f)
    var x = 0f
    while (x <= ancho) {
        drawLine(linea, Offset(x, 0f), Offset(x, alto), strokeWidth = 1f)
        x += paso
    }
    var y = 0f
    while (y <= alto) {
        drawLine(linea, Offset(0f, y), Offset(ancho, y), strokeWidth = 1f)
        y += paso
    }

    // Pistas de circuito: segmentos horizontales, a 45° y verticales que terminan en un nodo.
    val azar = Random(7)
    val traza = NeonCian.copy(alpha = 0.13f)
    val nodo = NeonCian.copy(alpha = 0.32f)
    repeat(16) {
        var px = (azar.nextInt((ancho / paso).toInt() + 1)) * paso
        var py = (azar.nextInt((alto / paso).toInt() + 1)) * paso
        repeat(3) { tramo ->
            val largo = (1 + azar.nextInt(3)) * paso
            val (dx, dy) = when ((tramo + azar.nextInt(3)) % 3) {
                0 -> largo to 0f
                1 -> largo to largo
                else -> 0f to largo
            }
            val sx = if (azar.nextBoolean()) 1f else -1f
            val nx = px + dx * sx
            val ny = py + dy
            drawLine(traza, Offset(px, py), Offset(nx, ny), strokeWidth = 2f)
            px = nx
            py = ny
        }
        drawCircle(nodo, radius = 4.dp.toPx() / 1.5f, center = Offset(px, py))
    }

    // Viñeta: oscurece un poco los bordes para dar profundidad.
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            center = Offset(ancho / 2f, alto / 2f),
            radius = maxOf(ancho, alto) * 0.8f,
        ),
    )
}
