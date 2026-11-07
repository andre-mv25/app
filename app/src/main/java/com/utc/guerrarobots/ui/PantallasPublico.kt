package com.utc.guerrarobots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.rules.Tiempo
import com.utc.guerrarobots.domain.service.LlavesService
import com.utc.guerrarobots.domain.service.RegistroService
import com.utc.guerrarobots.domain.service.TiemposService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// Sección "Público general": solo lectura, sin inicio de sesión. Muestra únicamente nombre del equipo,
// robot y plantel de los equipos ACEPTADOS (nada de integrantes ni capitán).

private const val ACTUALIZAR_CADA_MS = 20_000L

private fun descripcionPublica(clave: ClaveCategoria): String = when (clave) {
    ClaveCategoria.SIGUE_LINEAS -> "Robots autónomos que siguen una línea negra. Gana el menor tiempo."
    ClaveCategoria.FUTBOL -> "Robots controlados a distancia que juegan fútbol. Eliminación directa."
    ClaveCategoria.LABERINTO -> "Robots autónomos que buscan la salida de un laberinto. Gana el menor tiempo."
    ClaveCategoria.COMBATE -> "Robots de combate controlados a distancia, uno contra uno. Eliminación directa."
    ClaveCategoria.VELOCIDAD -> "Robots autónomos de velocidad. Gana el menor tiempo."
    else -> "Competencia del torneo."
}

private fun etiquetaEstadoPublica(estado: EstadoPartida): String = when (estado) {
    EstadoPartida.PENDIENTE -> "Esperando rival"
    EstadoPartida.PROGRAMADA -> "Por jugar"
    EstadoPartida.EN_CURSO -> "En juego"
    EstadoPartida.FINALIZADA -> "Terminada"
}

/** Lista de las competencias para el público. */
@Composable
fun PublicoScreen(categorias: List<Categoria>, onElegir: (Categoria) -> Unit, onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Público general", style = MaterialTheme.typography.headlineSmall)
        Text("Conoce las competencias y sigue sus resultados.", style = MaterialTheme.typography.bodyMedium)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            categorias.forEach { c ->
                Card(Modifier.fillMaxWidth().clickable { onElegir(c) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(descripcionPublica(c.clave), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private class VistaPublica(
    val equipos: List<Equipo>,
    val posiciones: List<TiemposService.PosicionVista>,
    val cerrada: Boolean,
    val cuadro: List<LlavesService.RondaVista>,
    val campeon: LlavesService.Podio?,
)

private enum class SeccionPublica(val titulo: String) { RESULTADOS("Resultados"), EQUIPOS("Equipos") }

@Composable
private fun PestanaPublica(texto: String, activa: Boolean, onClick: () -> Unit) {
    if (activa) {
        Button(onClick = onClick) { Text(texto) }
    } else {
        OutlinedButton(onClick = onClick) { Text(texto) }
    }
}

/** Una competencia: sus resultados (tabla de tiempos o llaves) y los equipos que participan. Se actualiza sola. */
@Composable
fun PublicoCategoriaScreen(
    categoria: Categoria,
    registro: RegistroService,
    tiempos: TiemposService,
    llaves: LlavesService,
    onVolver: () -> Unit,
) {
    var vista by remember { mutableStateOf<VistaPublica?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var seccion by remember { mutableStateOf(SeccionPublica.RESULTADOS) }
    val porLlaves = categoria.metodo == Metodo.LLAVES

    LaunchedEffect(categoria.clave) {
        while (true) {
            try {
                val equipos = registro.listar(EstadoEquipo.ACEPTADO, categoria.clave).sortedBy { it.nombre.lowercase() }
                vista = if (porLlaves) {
                    VistaPublica(equipos, emptyList(), false, llaves.cuadro(categoria.clave), llaves.campeon(categoria.clave))
                } else {
                    VistaPublica(equipos, tiempos.posiciones(categoria.clave), tiempos.estaCerrada(categoria.clave), emptyList(), null)
                }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo actualizar. Revisa tu conexión."
            }
            delay(ACTUALIZAR_CADA_MS)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(categoria.nombre, style = MaterialTheme.typography.headlineSmall)
        Text(descripcionPublica(categoria.clave), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeccionPublica.values().forEach { s -> PestanaPublica(s.titulo, seccion == s) { seccion = s } }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        val v = vista
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                v == null -> Text("Cargando...")
                seccion == SeccionPublica.EQUIPOS -> ListaEquiposPublica(v.equipos)
                porLlaves -> ResultadosLlaves(v, esFutbol = categoria.clave == ClaveCategoria.FUTBOL)
                else -> ResultadosTiempos(v)
            }
        }
        Text("Se actualiza solo cada 20 segundos.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun ListaEquiposPublica(equipos: List<Equipo>) {
    if (equipos.isEmpty()) {
        Text("Todavía no hay equipos confirmados en esta categoría.")
        return
    }
    Text("${equipos.size} equipos participando", style = MaterialTheme.typography.labelLarge)
    equipos.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.nombre, style = MaterialTheme.typography.titleSmall)
                Text("Robot: ${e.robot.nombre} · ${e.plantel}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResultadosTiempos(v: VistaPublica) {
    if (v.posiciones.isEmpty()) {
        Text("Todavía no hay tiempos registrados.")
        return
    }
    Text(
        if (v.cerrada) "Resultados finales" else "Resultados en vivo",
        style = MaterialTheme.typography.labelLarge,
    )
    v.posiciones.forEach { p ->
        val medalla = if (p.fila.sinTiempo) "" else when (p.fila.lugar) {
            1 -> "🥇 "
            2 -> "🥈 "
            3 -> "🥉 "
            else -> ""
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$medalla${p.fila.lugar}. ${p.equipo.nombre}", style = MaterialTheme.typography.titleSmall)
                Text("Robot: ${p.equipo.robot.nombre} · ${p.equipo.plantel}", style = MaterialTheme.typography.bodySmall)
                val mejor = p.fila.mejorMs
                Text(
                    if (mejor != null) "Mejor tiempo: ${Tiempo.formatear(mejor)}" else "Sin tiempo válido",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p.fila.empate && !v.cerrada) {
                    Text("Empatado: se definirá en una carrera extra", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ResultadosLlaves(v: VistaPublica, esFutbol: Boolean) {
    v.campeon?.let { podio ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🏆 Campeón: ${podio.campeon.nombre}", style = MaterialTheme.typography.titleMedium)
                Text("Robot: ${podio.campeon.robot.nombre} · ${podio.campeon.plantel}", style = MaterialTheme.typography.bodySmall)
                podio.subcampeon?.let { Text("🥈 Subcampeón: ${it.nombre}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    if (v.cuadro.isEmpty()) {
        Text("Las llaves todavía no se han sorteado.")
        return
    }
    v.cuadro.forEach { ronda ->
        Text(ronda.nombre, style = MaterialTheme.typography.titleMedium)
        ronda.partidas.forEach { pv ->
            val p = pv.partida
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (p.esBye) {
                        Text("${(pv.equipoA ?: pv.equipoB)?.nombre ?: "—"} pasa directo (sin rival)")
                    } else {
                        Text(
                            "${pv.equipoA?.nombre ?: "Por definir"}  vs  ${pv.equipoB?.nombre ?: "Por definir"}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val donde = if (p.cancha != null) "Cancha ${p.cancha} · turno ${p.turno}" else "Sin cancha asignada"
                        Text("$donde · ${etiquetaEstadoPublica(p.estado)}", style = MaterialTheme.typography.bodySmall)
                        if (p.estado == EstadoPartida.FINALIZADA) {
                            if (esFutbol && p.golesA != null && p.golesB != null) Text("Marcador: ${p.golesA} - ${p.golesB}")
                            Text("Ganó: ${pv.ganador?.nombre ?: "—"}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
