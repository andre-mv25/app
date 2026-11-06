package com.utc.guerrarobots.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.rules.Tiempo
import com.utc.guerrarobots.domain.service.ConsultaService
import com.utc.guerrarobots.domain.service.ConsultaService.EstadoLlaves
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pantalla del participante: busca su equipo y ve su robot, su rival y su cancha o pista. */
@Composable
fun ConsultaScreen(servicio: ConsultaService, onVolver: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Equipo>?>(null) }
    var consulta by remember { mutableStateOf<ConsultaService.Consulta?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun buscar() {
        scope.launch {
            cargando = true
            mensaje = null
            consulta = null
            try {
                val lista = servicio.buscar(texto)
                resultados = lista
                if (lista.isEmpty()) mensaje = "No encontramos ningún equipo. Escribe al menos 2 letras del equipo, del robot o tu folio."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = "No se pudo buscar. Revisa tu conexión e intenta de nuevo."
            }
            cargando = false
        }
    }

    fun abrir(equipoId: Long) {
        scope.launch {
            cargando = true
            mensaje = null
            try {
                when (val r = servicio.consultar(equipoId)) {
                    is Resultado.Ok -> consulta = r.valor
                    is Resultado.Falla -> mensaje = r.primero.mensaje
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mensaje = "No se pudo consultar. Revisa tu conexión e intenta de nuevo."
            }
            cargando = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Consultar mi equipo", style = MaterialTheme.typography.headlineSmall)

        val actual = consulta
        if (actual == null) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Equipo, robot o folio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { buscar() },
                enabled = !cargando && texto.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (cargando) "Buscando..." else "Buscar") }

            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            resultados.orEmpty().forEach { e ->
                Card(Modifier.fillMaxWidth().clickable { abrir(e.id) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                        Text("Robot: ${e.robot.nombre}")
                        if (e.folio.isNotBlank()) Text("Folio: ${e.folio}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            DetalleConsulta(actual)
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { abrir(actual.equipo.id) },
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (cargando) "Actualizando..." else "Actualizar") }
            OutlinedButton(
                onClick = { consulta = null; mensaje = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buscar otro equipo") }
        }

        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun DetalleConsulta(c: ConsultaService.Consulta) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.equipo.nombre, style = MaterialTheme.typography.titleLarge)
            Text("Robot: ${c.equipo.robot.nombre}")
            Text("Categoría: ${c.categoria.nombre}")
            if (c.equipo.folio.isNotBlank()) Text("Folio: ${c.equipo.folio}")
        }
    }

    when {
        c.equipo.estado != EstadoEquipo.ACEPTADO -> Text(
            "Tu registro todavía está en revisión. Cuando el comité lo acepte, aquí verás tu rival y tu pista.",
        )
        c.llaves != null -> DetalleLlaves(c.llaves)
        c.tiempos != null -> DetalleTiempos(c.tiempos)
    }
}

@Composable
private fun DetalleLlaves(s: ConsultaService.SituacionLlaves) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (s.estado) {
                EstadoLlaves.SIN_LLAVES -> Text("Las llaves todavía no se generan. Vuelve a consultar más tarde.")
                EstadoLlaves.ESPERANDO_RIVAL -> Text("Ya estás en las llaves. Tu rival se define cuando termine otro cruce.")
                EstadoLlaves.ELIMINADO -> Text("Quedaste fuera en ${s.ronda ?: "las llaves"}. ¡Gracias por participar!")
                EstadoLlaves.SUBCAMPEON -> Text("¡Subcampeón del torneo!", style = MaterialTheme.typography.titleMedium)
                EstadoLlaves.CAMPEON -> Text("¡Campeón del torneo!", style = MaterialTheme.typography.titleMedium)
                EstadoLlaves.JUEGA_PRONTO, EstadoLlaves.EN_CURSO -> {
                    Text(
                        if (s.estado == EstadoLlaves.EN_CURSO) "Tu partida está en curso" else "Tu próxima partida",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    s.ronda?.let { Text("Ronda: $it") }
                    s.rival?.let { Text("Rival: ${it.equipo} (robot ${it.robot})") }
                    Text(s.cancha?.let { "Cancha: $it" } ?: "Cancha: por asignar")
                    s.turno?.let { Text("Turno: $it") }
                }
            }
        }
    }
}

@Composable
private fun DetalleTiempos(s: ConsultaService.SituacionTiempos) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (s.turno != null && s.pista != null) {
                Text("Tu salida", style = MaterialTheme.typography.titleMedium)
                Text("Turno: ${s.turno}")
                Text("Pista: ${s.pista}")
            } else {
                Text("Todavía no se sortea el orden de salida. Vuelve a consultar más tarde.")
            }
            if (s.intentos.isNotEmpty()) {
                Text("Tus intentos", style = MaterialTheme.typography.titleMedium)
                s.intentos.forEach { Text(describirIntento(it)) }
            }
            Text("Intentos restantes: ${s.restantes}")
            s.lugar?.let { Text("Lugar actual: $it") }
            s.mejorMs?.let { Text("Mejor tiempo: ${Tiempo.formatear(it)}") }
            if (s.categoriaCerrada) Text("La categoría ya cerró.")
        }
    }
}

private fun describirIntento(i: Intento): String {
    val detalle = when (i.resultado) {
        ResultadoIntento.VALIDO -> i.tiempoEfectivoMs?.let { Tiempo.formatear(it) } ?: "sin tiempo"
        ResultadoIntento.NO_TERMINO -> "No terminó"
        ResultadoIntento.DESCALIFICADO -> "Descalificado"
    }
    val pista = i.pista?.let { " (pista $it)" } ?: ""
    return "Intento ${i.numero}: $detalle$pista"
}
