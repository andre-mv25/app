package com.utc.guerrarobots.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.rules.CodigoRegistro
import com.utc.guerrarobots.domain.service.ConsultaService

/**
 * Pantalla del competidor: muestra directamente la información del equipo registrado,
 * incluyendo su Folio, código QR, datos del equipo, robot y la competición.
 */
@Composable
fun PantallaCompetidor(
    servicio: ConsultaService,
    folioInicial: String? = null,
    categorias: List<Categoria> = emptyList(),
    onVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val folio = remember(folioInicial) {
        folioInicial?.ifBlank { null }
            ?: contexto.getSharedPreferences("guerra_robots_prefs", android.content.Context.MODE_PRIVATE)
                .getString("ultimo_folio", null)
    }

    var equipo by remember { mutableStateOf<Equipo?>(null) }
    var carga by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(folio) {
        if (folio.isNullOrBlank()) {
            carga = false
            return@LaunchedEffect
        }
        carga = true
        error = null
        try {
            equipo = servicio.buscarPorFolio(folio.trim())
            if (equipo == null) {
                error = "No se encontró información para el folio $folio."
            }
        } catch (_: Exception) {
            error = "No se pudo cargar la información. Revisa tu conexión."
        }
        carga = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Mi Competencia",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        when {
            carga -> {
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator()
                Text("Cargando registro...", style = MaterialTheme.typography.bodyMedium)
            }
            equipo != null -> {
                val eq = equipo!!
                val nombreCat = categorias.firstOrNull { it.clave == eq.robot.categoria }?.nombre
                    ?: eq.robot.categoria.valor

                // Folio destacado
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "FOLIO DE REGISTRO",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                        Text(
                            text = eq.folio,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Código QR del Registro
                val qr = remember(eq, nombreCat) {
                    QrBitmap.generar(CodigoRegistro.texto(eq, nombreCat))
                }
                var guardado by remember { mutableStateOf<Boolean?>(null) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrImagen(qr, tamano = 220.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Muestra este código QR en el evento para verificar tu registro.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { guardado = QrBitmap.guardarEnGaleria(contexto, qr, "QR-${eq.folio}") },
                        ) {
                            Text("Guardar QR en Galería")
                        }
                        when (guardado) {
                            true -> Text(
                                "QR guardado en Imágenes/GuerraRobots",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            false -> Text(
                                "No se pudo guardar la imagen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            null -> {}
                        }
                    }
                }

                // Datos del Equipo
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Datos del Equipo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        FilaDato("Equipo", eq.nombre)
                        FilaDato("Plantel", eq.plantel)
                        FilaDato("Capitán", eq.capitan)
                        if (eq.jefeCarrera.isNotBlank()) {
                            FilaDato("Jefe / Director de Carrera", eq.jefeCarrera)
                        }
                        if (eq.integrantes.isNotEmpty()) {
                            FilaDato("Integrantes", eq.integrantes.joinToString(", "))
                        }
                    }
                }

                // Datos del Robot
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Datos del Robot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        FilaDato("Nombre del Robot", eq.robot.nombre)
                        FilaDato("Categoría", nombreCat)
                        FilaDato("Peso", "${eq.robot.pesoG} g")
                        FilaDato("Tamaño", "${eq.robot.tamanoCm} cm")
                    }
                }

                // Datos de la Competición / Estado
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estado de la Competición", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text(
                            text = etiquetaEstado(eq.estado),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorEstado(eq.estado),
                        )
                        Text(
                            text = mensajeEstado(eq.estado),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            else -> {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = error ?: "Aún no has registrado ningún equipo en este dispositivo.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Text("Volver al inicio")
        }
    }
}

@Composable
private fun FilaDato(label: String, valor: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun etiquetaEstado(estado: EstadoEquipo): String = when (estado) {
    EstadoEquipo.PENDIENTE -> "Pendiente de aceptación"
    EstadoEquipo.REVISION -> "En revisión técnica"
    EstadoEquipo.ACEPTADO -> "Aceptado para competir"
    EstadoEquipo.RECHAZADO -> "Registro rechazado"
}

private fun mensajeEstado(estado: EstadoEquipo): String = when (estado) {
    EstadoEquipo.PENDIENTE -> "Tu registro ha sido enviado y está en espera de revisión por el comité."
    EstadoEquipo.REVISION -> "El robot requiere revisión técnica presencial de peso o tamaño en el evento."
    EstadoEquipo.ACEPTADO -> "Tu equipo está formalmente aceptado y listo para participar."
    EstadoEquipo.RECHAZADO -> "Tu registro no cumple con los requisitos del reglamento."
}

@Composable
private fun colorEstado(estado: EstadoEquipo) = when (estado) {
    EstadoEquipo.ACEPTADO -> MaterialTheme.colorScheme.primary
    EstadoEquipo.REVISION -> MaterialTheme.colorScheme.secondary
    EstadoEquipo.RECHAZADO -> MaterialTheme.colorScheme.error
    EstadoEquipo.PENDIENTE -> MaterialTheme.colorScheme.onSurfaceVariant
}
