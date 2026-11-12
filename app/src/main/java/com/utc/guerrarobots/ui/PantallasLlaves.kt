package com.utc.guerrarobots.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.Lado
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.service.LlavesService
import kotlinx.coroutines.launch

@Composable
private fun BotonOpcion(texto: String, activa: Boolean, onClick: () -> Unit) {
    if (activa) {
        Button(onClick = onClick) { Text(texto) }
    } else {
        OutlinedButton(onClick = onClick) { Text(texto) }
    }
}

private fun etiquetaEstado(estado: EstadoPartida): String = when (estado) {
    EstadoPartida.PENDIENTE -> "Esperando rival"
    EstadoPartida.PROGRAMADA -> "Lista para jugar"
    EstadoPartida.EN_CURSO -> "En juego"
    EstadoPartida.FINALIZADA -> "Terminada"
}

/**
 * Llaves de eliminación directa de una categoría (fútbol robot y combate): sorteo, canchas,
 * captura de resultados y campeón. El admin sortea y programa; el jurado inicia y captura resultados.
 */
@Composable
fun LlavesScreen(
    servicio: LlavesService,
    info: Categoria,
    esAdmin: Boolean,
    esSuperAdmin: Boolean = false,
    onVolver: () -> Unit,
) {
    val categoria = info.clave
    val esFutbol = categoria == ClaveCategoria.FUTBOL
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(0) }
    var rondas by remember { mutableStateOf<List<LlavesService.RondaVista>>(emptyList()) }
    var podio by remember { mutableStateOf<LlavesService.Podio?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var dialogo by remember { mutableStateOf<LlavesService.PartidaVista?>(null) }
    var errorDialogo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        try {
            rondas = servicio.cuadro(categoria)
            podio = servicio.campeon(categoria)
        } catch (e: Exception) {
            mensaje = "No se pudo cargar: ${e.message}"
        }
        cargando = false
    }

    fun ejecutar(accion: suspend () -> Resultado<Any?>) {
        scope.launch {
            when (val r = accion()) {
                is Resultado.Ok -> { mensaje = null; version += 1 }
                is Resultado.Falla -> mensaje = r.primero.mensaje
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(info.nombre, style = MaterialTheme.typography.headlineSmall)
            if (esSuperAdmin) {
                Text("Super Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (cargando) Text("Cargando...")

        podio?.let { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Campeón: ${p.campeon.nombre}", style = MaterialTheme.typography.titleMedium)
                    Text("Robot: ${p.campeon.robot.nombre}")
                    p.subcampeon?.let { Text("Subcampeón: ${it.nombre}") }
                }
            }
        }

        if (esAdmin || esSuperAdmin) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ejecutar { servicio.generar(categoria) } }) {
                    Text(if (rondas.isEmpty()) "Sortear llaves (Super Admin)" else "Volver a sortear")
                }
                if (rondas.isNotEmpty()) {
                    OutlinedButton(onClick = { ejecutar { servicio.programar(categoria) } }) {
                        Text("Asignar canchas (${info.canchas})")
                    }
                }
            }
        }

        if (!cargando && rondas.isEmpty()) {
            Text(
                if (esAdmin) {
                    "Todavía no hay llaves. Acepta a los equipos y pulsa \"Sortear llaves\"."
                } else {
                    "Todavía no hay llaves: el admin debe sortearlas."
                },
            )
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rondas.forEach { ronda ->
                item(key = "ronda-${ronda.ronda}") {
                    Text(ronda.nombre, style = MaterialTheme.typography.titleMedium)
                }
                ronda.partidas.forEach { pv ->
                    item(key = "partida-${pv.partida.id}") {
                        TarjetaPartida(
                            pv = pv,
                            esFutbol = esFutbol,
                            onIniciar = { ejecutar { servicio.iniciar(pv.partida.id) } },
                            onResultado = { errorDialogo = null; dialogo = pv },
                        )
                    }
                }
            }
        }

        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }

    dialogo?.let { pv ->
        ResultadoDialog(
            pv = pv,
            esFutbol = esFutbol,
            error = errorDialogo,
            onCancelar = { dialogo = null; errorDialogo = null },
            onGuardar = { golesA, golesB, ladoGanador ->
                scope.launch {
                    val r: Resultado<Any?> = if (esFutbol) {
                        servicio.resultadoFutbol(pv.partida.id, golesA, golesB, ladoGanador)
                    } else {
                        val ganadorId = if (ladoGanador == Lado.A) pv.partida.equipoAId else pv.partida.equipoBId
                        if (ganadorId == null) {
                            falla(Codigo.VALIDACION, "Elige quién ganó")
                        } else {
                            servicio.resultadoCombate(pv.partida.id, ganadorId)
                        }
                    }
                    when (r) {
                        is Resultado.Ok -> { dialogo = null; errorDialogo = null; mensaje = null; version += 1 }
                        is Resultado.Falla -> errorDialogo = r.primero.mensaje
                    }
                }
            },
        )
    }
}

@Composable
private fun TarjetaPartida(
    pv: LlavesService.PartidaVista,
    esFutbol: Boolean,
    onIniciar: () -> Unit,
    onResultado: () -> Unit,
) {
    val p = pv.partida
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (p.esBye) {
                val quien = pv.equipoA ?: pv.equipoB
                Text("${quien?.nombre ?: "—"} pasa directo (sin rival)", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "${pv.equipoA?.nombre ?: "Por definir"}  vs  ${pv.equipoB?.nombre ?: "Por definir"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                val donde = if (p.cancha != null) "Cancha ${p.cancha} · turno ${p.turno}" else "Sin cancha asignada"
                Text("$donde · ${etiquetaEstado(p.estado)}", style = MaterialTheme.typography.bodySmall)
                if (p.estado == EstadoPartida.FINALIZADA) {
                    if (esFutbol && p.golesA != null && p.golesB != null) {
                        Text("Marcador: ${p.golesA} - ${p.golesB}")
                    }
                    Text("Ganó: ${pv.ganador?.nombre ?: "—"}", style = MaterialTheme.typography.labelLarge)
                }
                if (p.estado == EstadoPartida.PROGRAMADA) {
                    Button(onClick = onIniciar) { Text("Iniciar partida") }
                }
                if (p.estado == EstadoPartida.EN_CURSO) {
                    Button(onClick = onResultado) { Text("Capturar resultado") }
                }
            }
        }
    }
}

@Composable
private fun ResultadoDialog(
    pv: LlavesService.PartidaVista,
    esFutbol: Boolean,
    error: String?,
    onCancelar: () -> Unit,
    onGuardar: (golesA: Int, golesB: Int, ganador: Lado?) -> Unit,
) {
    var golesA by remember { mutableStateOf("") }
    var golesB by remember { mutableStateOf("") }
    var lado by remember { mutableStateOf<Lado?>(null) }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    val nombreA = pv.equipoA?.nombre ?: "Equipo A"
    val nombreB = pv.equipoB?.nombre ?: "Equipo B"
    val a = golesA.trim().toIntOrNull()
    val b = golesB.trim().toIntOrNull()
    val empate = esFutbol && a != null && b != null && a == b

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Resultado") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$nombreA  vs  $nombreB")
                if (esFutbol) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = golesA, onValueChange = { golesA = it }, label = { Text("Goles A") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = golesB, onValueChange = { golesB = it }, label = { Text("Goles B") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                    if (empate) {
                        Text("Hay empate: ¿quién ganó en tiempo extra o penales?", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Text("¿Quién quedó en pie?", style = MaterialTheme.typography.labelLarge)
                }
                if (!esFutbol || empate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonOpcion(nombreA, lado == Lado.A) { lado = Lado.A }
                        BotonOpcion(nombreB, lado == Lado.B) { lado = Lado.B }
                    }
                }
                (errorLocal ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (esFutbol && (a == null || b == null || a < 0 || b < 0)) {
                    errorLocal = "Escribe los goles de los dos equipos (números enteros)"
                } else if ((!esFutbol || empate) && lado == null) {
                    errorLocal = if (esFutbol) "Elige quién ganó el desempate" else "Elige quién ganó"
                } else {
                    errorLocal = null
                    onGuardar(a ?: 0, b ?: 0, if (!esFutbol || empate) lado else null)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
