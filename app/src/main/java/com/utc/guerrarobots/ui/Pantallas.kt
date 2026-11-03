package com.utc.guerrarobots.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Terminos
import com.utc.guerrarobots.domain.service.RegistroService
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.utc.guerrarobots.R

@Composable
fun InicioScreen(onComenzar: () -> Unit, onAcceso: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_torneo),
            contentDescription = "5° Torneo Guerra de Robots, Universidad Tres Culturas",
            modifier = Modifier.size(240.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onComenzar, modifier = Modifier.fillMaxWidth()) { Text("Registrar mi equipo") }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAcceso, modifier = Modifier.fillMaxWidth()) { Text("Acceso jurado / admin") }
    }
}

@Composable
fun TerminosScreen(terminos: Terminos?, onAceptar: (Int) -> Unit, onVolver: () -> Unit) {
    var acepto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Términos y condiciones", style = MaterialTheme.typography.headlineSmall)
        if (terminos != null) Text("Versión ${terminos.version}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (terminos == null) {
                Text("Cargando...")
            } else {
                terminos.reglas.forEachIndexed { i, r -> Text("${i + 1}. $r") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acepto, onCheckedChange = { acepto = it }, enabled = terminos != null)
            Text("Acepto los términos y condiciones")
        }
        Button(
            onClick = { terminos?.let { onAceptar(it.version) } },
            enabled = acepto && terminos != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continuar") }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun CategoriasScreen(categorias: List<Categoria>, onElegir: (Categoria) -> Unit, onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿En qué categoría vas a competir?", style = MaterialTheme.typography.headlineSmall)
        categorias.forEach { c ->
            Card(Modifier.fillMaxWidth().clickable { onElegir(c) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (c.metodo == Metodo.LLAVES) "Se clasifica por llaves de eliminación directa" else "Se clasifica por mejor tiempo",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun Campo(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String?,
    teclado: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RegistroScreen(
    categoria: Categoria,
    terminosVersion: Int?,
    planteles: List<String>,
    onEnviar: suspend (RegistroService.SolicitudRegistro) -> Resultado<Equipo>,
    onListo: (Equipo) -> Unit,
    onVolver: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nombreEquipo by remember { mutableStateOf("") }
    var capitan by remember { mutableStateOf("") }
    var jefe by remember { mutableStateOf("") }
    var plantel by remember { mutableStateOf("") }
    var nombreRobot by remember { mutableStateOf("") }
    var pesoTxt by remember { mutableStateOf("") }
    var tamanoTxt by remember { mutableStateOf("") }
    var menuPlantel by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var enviando by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Registro · ${categoria.nombre}", style = MaterialTheme.typography.headlineSmall)

        Campo(nombreEquipo, { nombreEquipo = it }, "Nombre del equipo", errores["nombreEquipo"])
        Campo(capitan, { capitan = it }, "Capitán", errores["capitan"])

        Campo(jefe, { jefe = it }, "Jefe de carrera", errores["jefeCarrera"])

        Box {
            OutlinedButton(onClick = { menuPlantel = true }, modifier = Modifier.fillMaxWidth()) {
                Text(plantel.ifEmpty { "Elige tu plantel" })
            }
            DropdownMenu(expanded = menuPlantel, onDismissRequest = { menuPlantel = false }) {
                planteles.forEach { nombre ->
                    DropdownMenuItem(text = { Text(nombre) }, onClick = { plantel = nombre; menuPlantel = false })
                }
            }
        }
        errores["plantel"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Campo(nombreRobot, { nombreRobot = it }, "Nombre del robot (máx. 15)", errores["nombreRobot"])
        Campo(pesoTxt, { pesoTxt = it }, "Peso (gramos)", errores["pesoG"], KeyboardType.Number)
        Campo(tamanoTxt, { tamanoTxt = it }, "Tamaño: lado más largo (cm)", errores["tamanoCm"], KeyboardType.Decimal)
        Text(
            "Si el peso o el tamaño sale de los límites de la categoría, tu registro pasará a revisión técnica.",
            style = MaterialTheme.typography.bodySmall,
        )

        errores["categoria"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        errores["terminos"]?.let { Text(it + ". Vuelve a los términos y acéptalos.", color = MaterialTheme.colorScheme.error) }
        errores["general"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = !enviando,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val peso = pesoTxt.trim().toIntOrNull()
                val tamano = tamanoTxt.trim().replace(',', '.').toDoubleOrNull()
                val locales = buildMap {
                    if (peso == null) put("pesoG", "Escribe el peso en gramos (número entero)")
                    if (tamano == null) put("tamanoCm", "Escribe el tamaño en centímetros")
                }
                if (peso == null || tamano == null) {
                    errores = locales
                } else {
                    scope.launch {
                        enviando = true
                        val resultado = onEnviar(
                            RegistroService.SolicitudRegistro(
                                nombreEquipo = nombreEquipo,
                                capitan = capitan,
                                integrantes = emptyList(),
                                jefeCarrera = jefe,
                                plantel = plantel,
                                nombreRobot = nombreRobot,
                                categoria = categoria.clave,
                                pesoG = peso,
                                tamanoCm = tamano,
                                terminosVersion = terminosVersion,
                            ),
                        )
                        enviando = false
                        when (resultado) {
                            is Resultado.Ok -> onListo(resultado.valor)
                            is Resultado.Falla -> errores = resultado.errores.associate { (it.campo ?: "general") to it.mensaje }
                        }
                    }
                }
            },
        ) { Text(if (enviando) "Enviando..." else "Registrar equipo") }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun ListoScreen(equipo: Equipo, nombreCategoria: String, onInicio: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¡Registro enviado!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Folio: ${equipo.folio}", style = MaterialTheme.typography.titleLarge)
        Text("Equipo: ${equipo.nombre}")
        Text("Robot: ${equipo.robot.nombre} · $nombreCategoria")
        Spacer(Modifier.height(8.dp))
        Text(
            when (equipo.estado) {
                EstadoEquipo.REVISION -> "Tu robot pasa a revisión técnica porque su peso o tamaño sale de los límites."
                else -> "Tu registro está en espera de aceptación."
            },
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onInicio, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}
