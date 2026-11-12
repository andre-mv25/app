package com.utc.guerrarobots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.data.sheets.GoogleSheetsSync
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.rules.Tiempo
import com.utc.guerrarobots.domain.service.AccesoService
import com.utc.guerrarobots.domain.service.AuditoriaService
import com.utc.guerrarobots.domain.service.LlavesService
import com.utc.guerrarobots.domain.service.RegistroService
import com.utc.guerrarobots.domain.service.ReporteService
import com.utc.guerrarobots.domain.service.TiemposService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pantalla del Juez: solo lectura, muestra competencias, ganadores, jueces, fechas y horas.
 * Permite exportar a Excel y sincronizar con Google Sheets.
 * Cada juez tiene su propia hoja de Google Sheets independiente.
 */
@Composable
fun PantallaJuez(
    acceso: AccesoService,
    auditoria: AuditoriaService,
    llaves: LlavesService,
    tiempos: TiemposService,
    registro: RegistroService,
    reportes: ReporteService,
    onVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    var sincronizando by remember { mutableStateOf(false) }
    var exportando by remember { mutableStateOf(false) }

    // Obtener datos para mostrar
    var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
    var equipos by remember { mutableStateOf<List<Equipo>>(emptyList()) }
    var ganadores by remember { mutableStateOf<Map<ClaveCategoria, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        categorias = registro.categorias()
        equipos = registro.listar(EstadoEquipo.ACEPTADO)
        val mapa = mutableMapOf<ClaveCategoria, String>()
        for (categoria in categorias) {
            val ganador = obtenerGanadorCategoria(categoria, equipos, llaves, tiempos)
            if (ganador != null) {
                mapa[categoria.clave] = ganador
            }
        }
        ganadores = mapa.toMap()
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Encabezado con información del juez
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Mi Panel de Juez",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Juez: ${acceso.nombreActual ?: acceso.usuarioActual?.correo ?: "Desconocido"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Rol: ${acceso.rolActual.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Última actualización: ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(java.time.Instant.now())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Divider(modifier = Modifier.fillMaxWidth())

        // Botones de exportación y sincronización
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = !exportando,
                modifier = Modifier.weight(1f),
                onClick = {
                    exportando = true
                    mensaje = null
                    scope.launch {
                        val archivo = ExcelCompetencias.exportarYCompartir(
                            contexto = contexto,
                            categorias = categorias,
                            equipos = equipos,
                            partidas = llaves.partidasTodas(),
                            intentos = tiempos.intentosTodas(),
                            auditoria = auditoria.listar(),
                        )
                        exportando = false
                        if (archivo != null) {
                            mensaje = "Reporte de competencias exportado a Excel"
                            esError = false
                        } else {
                            mensaje = "Error al exportar a Excel"
                            esError = true
                        }
                    }
                },
            ) {
                if (exportando) Text("Exportando...") else Text("Exportar a Excel")
            }

            Button(
                enabled = !sincronizando,
                modifier = Modifier.weight(1f),
                onClick = {
                    sincronizando = true
                    mensaje = null
                    scope.launch {
                        try {
                            val todas = reportes.reporteTodas()
                            var ok = true
                            var msgs = mutableListOf<String>()
                            for (reporte in todas) {
                                val (exito, msg) = GoogleSheetsSync.sincronizarReporte(contexto, reporte)
                                ok = ok && exito
                                msgs.add(msg)
                            }
                            sincronizando = false
                            mensaje = msgs.joinToString(" ")
                            esError = !ok
                        } catch (e: Exception) {
                            sincronizando = false
                            mensaje = "Error al sincronizar reporte: ${e.message}"
                            esError = true
                        }
                    }
                },
            ) {
                if (sincronizando) Text("Sincronizando...") else Text("Google Sheets")
            }
        }

        mensaje?.let {
            Text(
                it,
                color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Competencias y Resultados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        // Mostrar resumen por categoría
        categorias.forEach { categoria ->
            val equipoGanador = ganadores[categoria.clave]
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${categoria.nombre}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${categoria.metodo.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Ganador: ${equipoGanador ?: "Pendiente"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Equipos: ${equipos.filter { it.robot.categoria == categoria.clave }.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onVolver,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver al Panel")
        }
    }
}

/**
 * Obtiene el equipo ganador de una categoría basado en las partidas finalizadas o intentos válidos.
 */
private suspend fun obtenerGanadorCategoria(
    categoria: Categoria,
    equipos: List<Equipo>,
    llaves: LlavesService,
    tiempos: TiemposService,
): String? {
    val equiposMap = equipos.associateBy { it.id }

    // Para categorías de llaves (Combate, Fútbol): buscar en la partida final
    val sorteo = llaves.campeon(categoria.clave)
    if (sorteo != null) {
        return sorteo.campeon.nombre
    }

    // Para categorías de tiempo: buscar el intento con mejor tiempo válido
    val intentosCategoria = tiempos.intentosTodas().filter { it.categoria == categoria.clave && it.resultado == ResultadoIntento.VALIDO }
    if (intentosCategoria.isNotEmpty()) {
        val mejorIntento = intentosCategoria.minByOrNull { it.tiempoEfectivoMs ?: Long.MAX_VALUE }
        if (mejorIntento != null) {
            return equipos.firstOrNull { it.id == mejorIntento.equipoId }?.nombre
        }
    }

    return null
}
