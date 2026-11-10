package com.utc.guerrarobots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.service.ConsultaService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pantalla del competidor: solo lectura, muestra folio, categoría y estado de su equipo. */
@Composable
fun PantallaCompetidor(
    servicio: ConsultaService,
    folioInicial: String? = null,
    onVolver: () -> Unit,
) {
    var folio by remember { mutableStateOf(folioInicial ?: "") }
    var equipo by remember { mutableStateOf<Equipo?>(null) }
    var carga by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun buscar() {
        val f = folio.trim()
        if (f.isEmpty()) {
            mensaje = "Escribe un folio para buscar"
            return
        }
        scope.launch {
            carga = true
            mensaje = null
            try {
                equipo = servicio.buscarPorFolio(f)
                if (equipo == null) {
                    mensaje = "No se encontró ningún equipo con el folio $f"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = "No se pudo buscar. Revisa tu conexión."
            }
            carga = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mi Competencia", style = MaterialTheme.typography.headlineSmall)
        Text("Consulta el estado de tu registro", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = folio,
            onValueChange = { folio = it },
            label = { Text("Folio de registro") },
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { buscar() },
            enabled = !carga && folio.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (carga) "Buscando..." else "Buscar") }

        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (carga) {
            Text("Buscando...", style = MaterialTheme.typography.bodySmall)
        }

        equipo?.let { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folio: ${e.folio}", style = MaterialTheme.typography.titleMedium)
                    Text("Equipo: ${e.nombre}", style = MaterialTheme.typography.bodyLarge)
                    Text("Robot: ${e.robot.nombre}", style = MaterialTheme.typography.bodyMedium)
                    Text("Categoría: ${e.robot.categoria.valor}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Estado: ${etiquetaEstado(e.estado)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorEstado(e.estado),
                    )
                    Text(
                        "Plantel: ${e.plantel}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Capitán: ${e.capitan}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private fun etiquetaEstado(estado: EstadoEquipo): String = when (estado) {
    EstadoEquipo.PENDIENTE -> "Pendiente de aceptación"
    EstadoEquipo.REVISION -> "En revisión técnica"
    EstadoEquipo.ACEPTADO -> "Aceptado"
    EstadoEquipo.RECHAZADO -> "Rechazado"
}

private fun colorEstado(estado: EstadoEquipo) = when (estado) {
    EstadoEquipo.ACEPTADO -> MaterialTheme.colorScheme.primary
    EstadoEquipo.REVISION -> MaterialTheme.colorScheme.secondary
    EstadoEquipo.RECHAZADO -> MaterialTheme.colorScheme.error
    EstadoEquipo.PENDIENTE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun rememberScrollState() = androidx.compose.foundation.rememberScrollState()
