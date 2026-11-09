package com.utc.guerrarobots.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utc.guerrarobots.R
import com.utc.guerrarobots.ui.theme.NeonCian
import com.utc.guerrarobots.ui.theme.NeonMagenta
import com.utc.guerrarobots.ui.theme.NeonNaranja
import com.utc.guerrarobots.ui.theme.TextoApagado
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Letras con degradado de neón (cian → magenta → naranja). */
private fun estiloNeon(tamano: Int): TextStyle = TextStyle(
    brush = Brush.horizontalGradient(listOf(NeonCian, NeonMagenta, NeonNaranja)),
    fontSize = tamano.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = 4.sp,
    textAlign = TextAlign.Center,
)

/**
 * Pantalla de arranque: "EL TORNEO REGRESA A CASA". Se muestra mientras la app se conecta.
 * Las piezas entran una tras otra y el logo brilla con un pulso suave.
 */
@Composable
fun PantallaCarga(modifier: Modifier = Modifier) {
    val entradas = remember { List(5) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        entradas.forEachIndexed { i, animacion ->
            launch {
                delay(i * 300L)
                animacion.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            }
        }
    }
    val pulso by rememberInfiniteTransition(label = "pulso").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "brillo",
    )

    fun Modifier.aparecer(i: Int): Modifier = graphicsLayer {
        val v = entradas[i].value
        alpha = v
        translationY = (1f - v) * 48f
    }

    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val v = entradas[0].value
                    alpha = v
                    scaleX = 0.8f + 0.2f * v
                    scaleY = 0.8f + 0.2f * v
                }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(NeonCian.copy(alpha = 0.45f * pulso), Color.Transparent),
                            radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_torneo_transparente),
                contentDescription = "5° Torneo Guerra de Robots, Universidad Tres Culturas",
                modifier = Modifier.size(210.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        BasicText("EL TORNEO", style = estiloNeon(34), modifier = Modifier.fillMaxWidth().aparecer(1))
        BasicText("REGRESA", style = estiloNeon(50), modifier = Modifier.fillMaxWidth().aparecer(2))
        BasicText("A CASA", style = estiloNeon(34), modifier = Modifier.fillMaxWidth().aparecer(3))
        Spacer(Modifier.height(28.dp))
        Column(Modifier.aparecer(4), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.55f),
                color = NeonCian,
                trackColor = Color.White.copy(alpha = 0.10f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "V Torneo Interplanteles · Guerra de Robots",
                style = MaterialTheme.typography.bodyMedium,
                color = TextoApagado,
                textAlign = TextAlign.Center,
            )
            Text(
                "Conectando con el torneo...",
                style = MaterialTheme.typography.bodySmall,
                color = TextoApagado.copy(alpha = 0.7f),
            )
        }
    }
}

/** Cuando no se logra la conexión al abrir: mensaje claro y botón para reintentar. */
@Composable
fun PantallaSinConexion(detalle: String, onReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_torneo_transparente),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("No se pudo conectar", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Revisa tu conexión a internet e intenta de nuevo.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onReintentar, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        Spacer(Modifier.height(16.dp))
        Text("Detalle técnico:", style = MaterialTheme.typography.labelSmall, color = TextoApagado)
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = TextoApagado)
    }
}
