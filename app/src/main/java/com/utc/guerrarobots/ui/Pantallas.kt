package com.utc.guerrarobots.ui

import androidx.compose.foundation.background
import android.graphics.BitmapFactory
import android.os.Build
import com.utc.guerrarobots.domain.rules.CodigoRegistro
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Terminos
import com.utc.guerrarobots.domain.model.TerminosBase
import com.utc.guerrarobots.domain.service.RegistroService
import com.utc.guerrarobots.domain.rules.Peso
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.utc.guerrarobots.R

@Composable
fun InicioScreen(onComenzar: () -> Unit, onPublico: () -> Unit, onAcceso: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_torneo_transparente),
            contentDescription = "5° Torneo Guerra de Robots, Universidad Tres Culturas",
            modifier = Modifier.size(240.dp),
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onComenzar, modifier = Modifier.fillMaxWidth()) { Text("Registrar mi equipo") }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onPublico, modifier = Modifier.fillMaxWidth()) { Text("Público general") }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAcceso, modifier = Modifier.fillMaxWidth()) { Text("Acceso jurado / admin") }
    }
}

@Composable
fun TerminosScreen(
    categoria: Categoria,
    terminos: Terminos?,
    enviando: Boolean,
    error: String?,
    onAceptar: (Int) -> Unit,
    onVolver: () -> Unit,
) {
    var acepto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Último paso", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Reglas, sanciones y aviso de privacidad", style = MaterialTheme.typography.headlineSmall)
        Text(categoria.nombre, style = MaterialTheme.typography.titleMedium)
        Text(
            "Para finalizar tu registro debes leer y aceptar lo siguiente.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (terminos == null) {
                Text("Cargando...")
            } else {
                Text("Reglas de la categoría", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                terminos.reglas.forEachIndexed { i, r -> Text("${i + 1}. $r") }
                if (terminos.sanciones.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Sanciones por incumplimiento", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    terminos.sanciones.forEach { Text("• $it") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Aviso de privacidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TerminosBase.avisoPrivacidad.forEach { Text("• $it") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acepto, onCheckedChange = { acepto = it }, enabled = terminos != null && !enviando)
            Text(
                "He leído y acepto las reglas, las sanciones y el aviso de privacidad",
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = { terminos?.let { onAceptar(it.version) } },
            enabled = acepto && terminos != null && !enviando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (enviando) "Enviando registro..." else "Aceptar y finalizar registro") }
        TextButton(onClick = onVolver, enabled = !enviando, modifier = Modifier.fillMaxWidth()) { Text("Volver al formulario") }
    }
}

/** La imagen de cada categoría, según su clave. Una categoría nueva sin imagen se muestra solo con texto. */
private fun imagenCategoria(clave: ClaveCategoria): Int? = when (clave) {
    ClaveCategoria.SIGUE_LINEAS -> R.drawable.sigue_lineas
    ClaveCategoria.FUTBOL -> R.drawable.robots_futbol
    ClaveCategoria.LABERINTO -> R.drawable.laberintos
    ClaveCategoria.COMBATE -> R.drawable.combate_robots
    ClaveCategoria.VELOCIDAD -> R.drawable.robots_velocidad
    else -> null
}

@Composable
fun CategoriasScreen(categorias: List<Categoria>, onElegir: (Categoria) -> Unit, onVolver: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("¿En qué categoría vas a competir?", style = MaterialTheme.typography.headlineSmall)
        categorias.forEach { c ->
            Card(Modifier.fillMaxWidth().clickable { onElegir(c) }) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    imagenCategoria(c.clave)?.let { imagen ->
                        Image(
                            painter = painterResource(imagen),
                            contentDescription = c.nombre,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(104.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (c.metodo == Metodo.LLAVES) "Se clasifica por llaves de eliminación directa" else "Se clasifica por mejor tiempo",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
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

/** Lo que el participante lleva escrito en el formulario; se conserva al ir a los términos y volver. */
data class BorradorRegistro(
    val nombreEquipo: String = "",
    val capitan: String = "",
    /** Los demás participantes (el capitán cuenta aparte como participante 1). */
    val integrantes: List<String> = emptyList(),
    val jefe: String = "",
    val plantel: String = "",
    val nombreRobot: String = "",
    val pesoTxt: String = "",
    val tamanoTxt: String = "",
    /** La foto del robot ya reducida (JPEG). */
    val foto: ByteArray? = null,
) {
    /** La solicitud para el servicio; null si el peso o el tamaño no son números. */
    fun aSolicitud(categoria: ClaveCategoria, terminosVersion: Int?): RegistroService.SolicitudRegistro? {
        val peso = Peso.kgAGramos(pesoTxt) ?: return null
        val tamano = tamanoTxt.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return RegistroService.SolicitudRegistro(
            nombreEquipo = nombreEquipo,
            capitan = capitan,
            integrantes = integrantes.map { it.trim() },
            jefeCarrera = jefe,
            plantel = plantel,
            nombreRobot = nombreRobot,
            categoria = categoria,
            pesoG = peso,
            tamanoCm = tamano,
            terminosVersion = terminosVersion,
            foto = foto,
        )
    }
}

@Composable
fun RegistroScreen(
    categoria: Categoria,
    planteles: List<String>,
    inicial: BorradorRegistro,
    errores: Map<String, String>,
    onContinuar: (BorradorRegistro) -> Unit,
    onVolver: () -> Unit,
) {
    var nombreEquipo by remember { mutableStateOf(inicial.nombreEquipo) }
    var capitan by remember { mutableStateOf(inicial.capitan) }
    val integrantes = remember { mutableStateListOf<String>().apply { addAll(inicial.integrantes) } }
    var jefe by remember { mutableStateOf(inicial.jefe) }
    var plantel by remember { mutableStateOf(inicial.plantel) }
    var nombreRobot by remember { mutableStateOf(inicial.nombreRobot) }
    var pesoTxt by remember { mutableStateOf(inicial.pesoTxt) }
    var tamanoTxt by remember { mutableStateOf(inicial.tamanoTxt) }
    var menuPlantel by remember { mutableStateOf(false) }
    var erroresVisibles by remember { mutableStateOf(errores) }
    var foto by remember { mutableStateOf(inicial.foto) }
    var procesandoFoto by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val scopeFoto = rememberCoroutineScope()
    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scopeFoto.launch {
                procesandoFoto = true
                val bytes = FotoRobot.preparar(contexto, uri)
                if (bytes == null) {
                    erroresVisibles = erroresVisibles + ("foto" to "No se pudo leer esa foto. Elige otra")
                } else {
                    foto = bytes
                    erroresVisibles = erroresVisibles - "foto"
                }
                procesandoFoto = false
            }
        }
    }
    val vistaPreviaFoto = remember(foto) {
        foto?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val maxExtra = (categoria.maxParticipantes - 1).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Registro · ${categoria.nombre}", style = MaterialTheme.typography.headlineSmall)

        Campo(nombreEquipo, { nombreEquipo = it }, "Nombre del equipo", erroresVisibles["nombreEquipo"])

        Text(
            "Participantes: ${1 + integrantes.size} de ${categoria.maxParticipantes} (contando al capitán)",
            style = MaterialTheme.typography.labelLarge,
        )
        Campo(capitan, { capitan = it }, "Capitán (participante 1)", erroresVisibles["capitan"])
        integrantes.forEachIndexed { i, nombre ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Campo(nombre, { integrantes[i] = it }, "Participante ${i + 2}", null)
                }
                TextButton(onClick = { integrantes.removeAt(i) }) { Text("Quitar") }
            }
        }
        if (integrantes.size < maxExtra) {
            OutlinedButton(onClick = { integrantes.add("") }, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar participante")
            }
        }
        erroresVisibles["integrantes"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Campo(jefe, { jefe = it }, "Jefe de carrera", erroresVisibles["jefeCarrera"])

        OutlinedButton(onClick = { menuPlantel = !menuPlantel }, modifier = Modifier.fillMaxWidth()) {
            Text(plantel.ifEmpty { "Elige tu plantel" })
        }
        if (menuPlantel) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    if (planteles.isEmpty()) {
                        Text("Cargando planteles...", Modifier.padding(16.dp))
                    }
                    planteles.forEach { nombre ->
                        Text(
                            nombre,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (nombre == plantel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { plantel = nombre; menuPlantel = false }
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
        erroresVisibles["plantel"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Campo(nombreRobot, { nombreRobot = it }, "Nombre del robot (máx. 15)", erroresVisibles["nombreRobot"])
        Campo(pesoTxt, { pesoTxt = it }, "Peso (kg)", erroresVisibles["pesoG"], KeyboardType.Decimal)
        Campo(tamanoTxt, { tamanoTxt = it }, "Tamaño: lado más largo (cm)", erroresVisibles["tamanoCm"], KeyboardType.Decimal)
        Text(
            "Si el peso o el tamaño sale de los límites de la categoría, tu registro pasará a revisión técnica.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Foto del robot (obligatoria)", style = MaterialTheme.typography.labelLarge)
        if (vistaPreviaFoto != null) {
            Image(
                bitmap = vistaPreviaFoto,
                contentDescription = "Foto del robot",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        OutlinedButton(
            onClick = {
                selectorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !procesandoFoto,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    procesandoFoto -> "Preparando foto..."
                    foto == null -> "Elegir foto del robot"
                    else -> "Cambiar foto"
                },
            )
        }
        erroresVisibles["foto"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        erroresVisibles["categoria"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        erroresVisibles["terminos"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        erroresVisibles["general"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "Al continuar verás las reglas, las sanciones y el aviso de privacidad. Debes aceptarlos para finalizar tu registro.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val locales = buildMap<String, String> {
                    if (nombreEquipo.isBlank()) put("nombreEquipo", "Escribe el nombre del equipo")
                    if (capitan.isBlank()) put("capitan", "Escribe el nombre del capitán")
                    if (integrantes.any { it.isBlank() }) {
                        put("integrantes", "Escribe el nombre de cada participante o quita el campo vacío")
                    }
                    if (jefe.isBlank()) put("jefeCarrera", "Escribe el nombre del jefe de carrera")
                    if (plantel.isBlank()) put("plantel", "Elige tu plantel")
                    if (nombreRobot.isBlank()) put("nombreRobot", "Escribe el nombre del robot")
                    if (Peso.kgAGramos(pesoTxt) == null) put("pesoG", "Escribe el peso")
                    if (tamanoTxt.trim().replace(',', '.').toDoubleOrNull() == null) {
                        put("tamanoCm", "Escribe el tamaño en centímetros")
                    }
                    if (foto == null) put("foto", "Sube una foto de tu robot")
                }
                erroresVisibles = locales
                if (locales.isEmpty()) {
                    onContinuar(
                        BorradorRegistro(
                            nombreEquipo = nombreEquipo,
                            capitan = capitan,
                            integrantes = integrantes.toList(),
                            jefe = jefe,
                            plantel = plantel,
                            nombreRobot = nombreRobot,
                            pesoTxt = pesoTxt,
                            tamanoTxt = tamanoTxt,
                            foto = foto,
                        ),
                    )
                }
            },
            enabled = !procesandoFoto,
        ) { Text("Continuar") }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun ListoScreen(equipo: Equipo, nombreCategoria: String, onInicio: () -> Unit) {
    val contexto = LocalContext.current
    val qr = remember(equipo, nombreCategoria) { QrBitmap.generar(CodigoRegistro.texto(equipo, nombreCategoria)) }
    var guardado by remember { mutableStateOf<Boolean?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
        Spacer(Modifier.height(20.dp))
        Text("Tu código QR", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        QrImagen(qr)
        Spacer(Modifier.height(8.dp))
        Text(
            "Al escanearlo aparecen tu folio y los datos de tu registro. Guárdalo y muéstralo en el evento.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { guardado = QrBitmap.guardarEnGaleria(contexto, qr, "QR-${equipo.folio}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar QR en la galería") }
            when (guardado) {
                true -> Text("QR guardado en Imágenes/GuerraRobots", style = MaterialTheme.typography.bodySmall)
                false -> Text("No se pudo guardar. Toma una captura de pantalla.", color = MaterialTheme.colorScheme.error)
                null -> {}
            }
        } else {
            Text("Toma una captura de pantalla para guardarlo.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onInicio, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}
