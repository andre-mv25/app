package com.utc.guerrarobots.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.rules.Tiempo
import com.utc.guerrarobots.domain.service.TiemposService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
private fun Pestana(texto: String, activa: Boolean, onClick: () -> Unit) {
    if (activa) {
        Button(onClick = onClick) { Text(texto) }
    } else {
        OutlinedButton(onClick = onClick) { Text(texto) }
    }
}

/** En sigue líneas la pista 1 es la A y la 2 es la B. */
private fun nombrePista(categoria: ClaveCategoria, pista: Int?): String = when {
    pista == null -> "—"
    categoria == ClaveCategoria.SIGUE_LINEAS -> if (pista == 1) "A" else "B"
    else -> pista.toString()
}

private fun describir(i: Intento, maxIntentos: Int): String {
    val que = if (i.numero > maxIntentos) "Extra" else "Intento ${i.numero}"
    val resultado = when (i.resultado) {
        ResultadoIntento.VALIDO -> {
            val bruto = Tiempo.formatear(i.tiempoMs ?: 0)
            if (i.penalizaciones > 0) {
                "${Tiempo.formatear(i.tiempoEfectivoMs ?: 0)} efectivo ($bruto + ${i.penalizaciones} pen.)"
            } else {
                bruto
            }
        }
        ResultadoIntento.NO_TERMINO -> "No terminó"
        ResultadoIntento.DESCALIFICADO -> "Descalificado (${i.motivo ?: "sin motivo"})"
    }
    return "$que: $resultado"
}

private enum class Pagina(val titulo: String) { ORDEN("Orden"), CAPTURA("Captura"), TABLA("Tabla"), CIERRE("Cierre") }

@Composable
fun TiemposScreen(
    servicio: TiemposService,
    info: Categoria,
    esAdmin: Boolean,
    onVolver: () -> Unit,
) {
    val categoria = info.clave
    val scope = rememberCoroutineScope()
    var pagina by remember { mutableStateOf(Pagina.ORDEN) }
    var version by remember { mutableStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    var orden by remember { mutableStateOf<List<TiemposService.TurnoSalida>>(emptyList()) }
    var avance by remember { mutableStateOf<List<TiemposService.AvanceEquipo>>(emptyList()) }
    var posiciones by remember { mutableStateOf<List<TiemposService.PosicionVista>>(emptyList()) }
    var podio by remember { mutableStateOf<List<TiemposService.PosicionVista>>(emptyList()) }
    var cerrada by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }

    var dialogo by remember { mutableStateOf<Pair<Equipo, Boolean>?>(null) }
    var errorDialogo by remember { mutableStateOf<String?>(null) }

    val maxIntentos = info.intentosMaximos
    val pistas = info.canchas

    LaunchedEffect(version) {
        try {
            orden = (servicio.ordenSalida(categoria) as? Resultado.Ok)?.valor.orEmpty()
            avance = servicio.avance(categoria)
            posiciones = servicio.posiciones(categoria)
            cerrada = servicio.estaCerrada(categoria)
            podio = (servicio.podio(categoria) as? Resultado.Ok)?.valor.orEmpty()
        } catch (e: Exception) {
            mensaje = "No se pudo cargar: ${e.message}"
        }
        cargando = false
    }

    fun refrescar() {
        version += 1
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(info.nombre, style = MaterialTheme.typography.headlineSmall)
        if (cerrada) Text("Categoría cerrada", color = MaterialTheme.colorScheme.primary)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pagina.entries.forEach { p -> Pestana(p.titulo, pagina == p) { pagina = p; mensaje = null } }
        }
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (cargando) Text("Cargando...")

        Column(Modifier.weight(1f)) {
            when (pagina) {
                Pagina.ORDEN -> {
                    if (esAdmin && !cerrada) {
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val r = servicio.generarOrden(categoria)) {
                                        is Resultado.Ok -> { mensaje = null; refrescar() }
                                        is Resultado.Falla -> mensaje = r.primero.mensaje
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (orden.isEmpty()) "Sortear orden de salida" else "Volver a sortear") }
                    }
                    if (orden.isEmpty()) {
                        Text("Todavía no hay orden de salida.", modifier = Modifier.padding(top = 8.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            orden.groupBy { it.turno }.forEach { (turno, lista) ->
                                item(key = "turno$turno") { Text("Turno $turno", style = MaterialTheme.typography.titleMedium) }
                                items(lista, key = { it.equipo.id }) { t ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Pista ${nombrePista(categoria, t.pista)} · ${t.equipo.nombre}")
                                            Text("Robot: ${t.equipo.robot.nombre}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Pagina.CAPTURA -> {
                    if (avance.isEmpty()) Text("No hay equipos aceptados en esta categoría.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(avance, key = { it.equipo.id }) { a ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val sitio = if (a.turno != null) " · Turno ${a.turno} pista ${nombrePista(categoria, a.pista)}" else ""
                                    Text("${a.equipo.nombre}$sitio", style = MaterialTheme.typography.titleMedium)
                                    Text("Robot: ${a.equipo.robot.nombre}", style = MaterialTheme.typography.bodySmall)
                                    if (a.intentos.isEmpty()) Text("Sin intentos todavía.")
                                    a.intentos.forEach { i ->
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(describir(i, maxIntentos), modifier = Modifier.weight(1f))
                                            if (!cerrada) {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        when (val r = servicio.anularIntento(categoria, i.id)) {
                                                            is Resultado.Ok -> { mensaje = null; refrescar() }
                                                            is Resultado.Falla -> mensaje = r.primero.mensaje
                                                        }
                                                    }
                                                }) { Text("Anular") }
                                            }
                                        }
                                    }
                                    if (a.carreraExtraPendiente) {
                                        Text("Empate: le toca carrera extra", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (!cerrada) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (a.restantes > 0) {
                                                Button(onClick = { errorDialogo = null; dialogo = a.equipo to false }) {
                                                    Text("Capturar intento")
                                                }
                                            }
                                            if (a.carreraExtraPendiente) {
                                                OutlinedButton(onClick = { errorDialogo = null; dialogo = a.equipo to true }) {
                                                    Text("Carrera extra")
                                                }
                                            }
                                        }
                                    } else {
                                        Text("Categoría cerrada", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                Pagina.TABLA -> {
                    if (posiciones.isEmpty()) Text("Todavía no hay tiempos capturados.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(posiciones, key = { it.equipo.id }) { p ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${p.fila.lugar}.º  ${p.equipo.nombre}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Mejor: ${p.fila.mejorMs?.let { Tiempo.formatear(it) } ?: "—"}  ·  " +
                                            "Segundo: ${p.fila.segundoMs?.let { Tiempo.formatear(it) } ?: "—"}",
                                    )
                                    if (p.fila.empate) Text("Empate: falta carrera extra", color = MaterialTheme.colorScheme.primary)
                                    if (p.fila.sinTiempo) Text("Sin tiempo válido", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Pagina.CIERRE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (cerrada) {
                            Text("Podio", style = MaterialTheme.typography.titleLarge)
                            if (podio.isEmpty()) Text("Nadie logró un tiempo válido.")
                            podio.forEach { p ->
                                Text("${p.fila.lugar}.º  ${p.equipo.nombre} (${p.equipo.robot.nombre}) · ${p.fila.mejorMs?.let { Tiempo.formatear(it) } ?: "—"}")
                            }
                        } else {
                            Text("Al cerrar la categoría ya no se capturan más intentos y se define el podio.")
                            Text("No se puede cerrar si hay un empate por los tres primeros lugares.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (esAdmin) {
                            if (!cerrada) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            when (val r = servicio.cerrar(categoria)) {
                                                is Resultado.Ok -> { mensaje = null; refrescar() }
                                                is Resultado.Falla -> mensaje = r.primero.mensaje
                                            }
                                        }
                                    },
                                ) { Text("Cerrar categoría") }
                            } else {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            when (val r = servicio.reabrir(categoria)) {
                                                is Resultado.Ok -> { mensaje = null; refrescar() }
                                                is Resultado.Falla -> mensaje = r.primero.mensaje
                                            }
                                        }
                                    },
                                ) { Text("Reabrir categoría") }
                            }
                        } else {
                            Text("Solo el admin puede cerrar o reabrir la categoría.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }

    dialogo?.let { (equipo, esExtra) ->
        IntentoDialog(
            categoria = categoria,
            equipo = equipo,
            esExtra = esExtra,
            pistas = pistas,
            penalizacionMs = servicio.penalizacionMs,
            error = errorDialogo,
            onCancelar = { dialogo = null; errorDialogo = null },
            onGuardar = { resultado, min, seg, mil, pista, motivo, penalizaciones ->
                scope.launch {
                    val r = if (esExtra) {
                        servicio.registrarCarreraExtra(categoria, equipo.id, resultado, min, seg, mil, pista, motivo, penalizaciones)
                    } else {
                        servicio.registrarIntento(categoria, equipo.id, resultado, min, seg, mil, pista, motivo, penalizaciones)
                    }
                    when (r) {
                        is Resultado.Ok -> { dialogo = null; errorDialogo = null; mensaje = null; refrescar() }
                        is Resultado.Falla -> errorDialogo = r.primero.mensaje
                    }
                }
            },
        )
    }
}

@Composable
private fun IntentoDialog(
    categoria: ClaveCategoria,
    equipo: Equipo,
    esExtra: Boolean,
    pistas: Int,
    penalizacionMs: Long,
    error: String?,
    onCancelar: () -> Unit,
    onGuardar: (ResultadoIntento, Int, Int, Int, Int?, String, Int) -> Unit,
) {
    var resultado by remember { mutableStateOf(ResultadoIntento.VALIDO) }
    var minutos by remember { mutableStateOf("") }
    var segundos by remember { mutableStateOf("") }
    var milesimas by remember { mutableStateOf("") }
    var pista by remember { mutableStateOf<Int?>(null) }
    var motivo by remember { mutableStateOf("") }
    var penalizaciones by remember { mutableStateOf(0) }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    var corriendo by remember { mutableStateOf(false) }
    var inicioNs by remember { mutableStateOf(0L) }
    var transcurridoMs by remember { mutableStateOf(0L) }

    // Mientras corre el cronómetro la pantalla no se apaga.
    val vista = LocalView.current
    DisposableEffect(corriendo) {
        vista.keepScreenOn = corriendo
        onDispose { vista.keepScreenOn = false }
    }

    LaunchedEffect(corriendo) {
        if (corriendo) {
            while (true) {
                transcurridoMs = (System.nanoTime() - inicioNs) / 1_000_000
                delay(16)
            }
        }
    }

    fun ponerTiempo(ms: Long) {
        minutos = (ms / 60_000).toString()
        segundos = ((ms / 1_000) % 60).toString()
        milesimas = (ms % 1_000).toString()
    }

    fun numero(texto: String): Int? = if (texto.isBlank()) 0 else texto.trim().toIntOrNull()

    val m = numero(minutos)
    val seg = numero(segundos)
    val mil = numero(milesimas)
    val bruto: Long? = if (m != null && seg != null && mil != null && Tiempo.esValido(m, seg, mil)) {
        Tiempo.aMilisegundos(m, seg, mil)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esExtra) "Carrera extra" else "Capturar intento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${equipo.nombre} · ${equipo.robot.nombre}")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pestana("Válido", resultado == ResultadoIntento.VALIDO) { resultado = ResultadoIntento.VALIDO }
                    Pestana("No terminó", resultado == ResultadoIntento.NO_TERMINO) { resultado = ResultadoIntento.NO_TERMINO }
                    Pestana("Descalificado", resultado == ResultadoIntento.DESCALIFICADO) { resultado = ResultadoIntento.DESCALIFICADO }
                }
                when (resultado) {
                    ResultadoIntento.VALIDO -> {
                        Text(Tiempo.formatear(transcurridoMs), style = MaterialTheme.typography.headlineLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!corriendo) {
                                Button(onClick = {
                                    inicioNs = System.nanoTime() - transcurridoMs * 1_000_000
                                    corriendo = true
                                    errorLocal = null
                                }) { Text(if (transcurridoMs == 0L) "Iniciar" else "Seguir") }
                            } else {
                                Button(onClick = {
                                    val final = (System.nanoTime() - inicioNs) / 1_000_000
                                    transcurridoMs = final
                                    corriendo = false
                                    ponerTiempo(final)
                                }) { Text("Detener") }
                            }
                            OutlinedButton(onClick = {
                                corriendo = false
                                transcurridoMs = 0
                                minutos = ""
                                segundos = ""
                                milesimas = ""
                            }) { Text("Reiniciar") }
                        }
                        Text("Al detenerlo se llena el tiempo; también puedes escribirlo o corregirlo.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minutos, onValueChange = { minutos = it }, label = { Text("Min") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = segundos, onValueChange = { segundos = it }, label = { Text("Seg") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = milesimas, onValueChange = { milesimas = it }, label = { Text("Mil") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.weight(1f),
                            )
                        }
                        Text("Penalizaciones (+${Tiempo.formatear(penalizacionMs)} cada una)", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { if (penalizaciones > 0) penalizaciones -= 1 }) { Text("−") }
                            Text("$penalizaciones", style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick = { penalizaciones += 1 }) { Text("+") }
                        }
                        if (bruto != null && bruto > 0) {
                            Text(
                                "Tiempo efectivo: ${Tiempo.formatear(bruto + penalizaciones * penalizacionMs)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    ResultadoIntento.DESCALIFICADO -> OutlinedTextField(
                        value = motivo, onValueChange = { motivo = it }, label = { Text("Motivo") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ResultadoIntento.NO_TERMINO -> Text("El intento queda registrado sin tiempo.")
                }
                if (pistas > 1) {
                    Text("Pista", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in 1..pistas) Pestana(nombrePista(categoria, n), pista == n) { pista = n }
                    }
                }
                (errorLocal ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (corriendo) {
                    errorLocal = "Detén el cronómetro antes de guardar"
                } else if (m == null || seg == null || mil == null) {
                    errorLocal = "Escribe solo números en el tiempo"
                } else {
                    errorLocal = null
                    onGuardar(resultado, m, seg, mil, pista, motivo, if (resultado == ResultadoIntento.VALIDO) penalizaciones else 0)
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
