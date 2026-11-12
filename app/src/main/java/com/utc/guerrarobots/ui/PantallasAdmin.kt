package com.utc.guerrarobots.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Divider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Context
import com.utc.guerrarobots.data.sheets.GoogleSheetsSync
import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.rules.Peso
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.service.AccesoService
import com.utc.guerrarobots.domain.service.AuditoriaService
import com.utc.guerrarobots.domain.service.RegistroService
import com.utc.guerrarobots.domain.service.SesionService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun Opcion(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    if (seleccionada) {
        Button(onClick = onClick) { Text(texto) }
    } else {
        OutlinedButton(onClick = onClick) { Text(texto) }
    }
}

private fun etiqueta(estado: EstadoEquipo) = when (estado) {
    EstadoEquipo.PENDIENTE -> "Pendiente"
    EstadoEquipo.REVISION -> "En revisión"
    EstadoEquipo.ACEPTADO -> "Aceptado"
    EstadoEquipo.RECHAZADO -> "Rechazado"
}

/**
 * Entrada de jurado y admin: con Google (lo normal) o con el PIN de acceso individual.
 * Cada admin y cada jurado tiene su propio PIN asociado a su correo/identificador.
 * Si se perdió el PIN, el admin lo restablece entrando con Google.
 */
@Composable
fun AccesoScreen(
    acceso: AccesoService,
    sesion: SesionService,
    inicioSesion: ProveedorInicioSesion,
    onEntro: (Rol) -> Unit,
    onRestablecer: () -> Unit,
    onVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var rol by remember { mutableStateOf(Rol.ADMIN) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var errorGoogle by remember { mutableStateOf<String?>(null) }
    var abriendoGoogle by remember { mutableStateOf(false) }

    /** Abre Google. Con [restablecer], solo deja pasar al admin y lo lleva a definir un PIN nuevo. */
    fun google(restablecer: Boolean) {
        scope.launch {
            abriendoGoogle = true
            errorGoogle = null
            try {
                val usuario = inicioSesion.iniciar(contexto)
                when (val r = sesion.entrar(usuario)) {
                    is Resultado.Ok -> when {
                        restablecer && r.valor != Rol.ADMIN -> {
                            errorGoogle = "Solo el admin puede restablecer el PIN"
                            acceso.salir()
                            inicioSesion.cerrar(contexto)
                        }
                        restablecer -> onRestablecer()
                        else -> onEntro(r.valor)
                    }
                    is Resultado.Falla -> {
                        errorGoogle = r.primero.mensaje
                        inicioSesion.cerrar(contexto)
                    }
                }
            } catch (e: SesionCancelada) {
                // Cerró la ventana de Google: no es un error.
            } catch (e: Exception) {
                errorGoogle = e.message ?: "No se pudo iniciar sesión"
            }
            abriendoGoogle = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Acceso jurado / admin", style = MaterialTheme.typography.headlineSmall)

        Button(
            enabled = !abriendoGoogle,
            modifier = Modifier.fillMaxWidth(),
            onClick = { google(restablecer = false) },
        ) { Text(if (abriendoGoogle) "Abriendo Google..." else "Entrar con Google") }
        errorGoogle?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Divider(modifier = Modifier.fillMaxWidth())

        Text("O con tu PIN individual", style = MaterialTheme.typography.labelLarge)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Opcion("Admin", rol == Rol.ADMIN) { rol = Rol.ADMIN; error = null }
            Opcion("Jurado", rol == Rol.JURADO) { rol = Rol.JURADO; error = null }
        }

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN (4 a 8 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error ?: "") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (pin.isBlank()) {
                    error = "Ingresa tu PIN"
                } else if (acceso.entrar(correo = "", rol = rol, pin = pin)) {
                    onEntro(rol)
                } else {
                    error = "PIN incorrecto o no autorizado"
                }
            },
        ) { Text("Entrar con PIN") }

        if (pin.isNotEmpty()) {
            Text("¿Perdiste tu PIN? Pídele al admin que te cree uno nuevo, o entra con tu cuenta de Google.", style = MaterialTheme.typography.bodySmall)
            TextButton(
                enabled = !abriendoGoogle,
                onClick = { google(restablecer = true) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restablecer PIN con Google") }
        }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
fun PanelScreen(
    rol: Rol,
    categorias: List<Categoria>,
    adminNombre: String? = null,
    onEquipos: () -> Unit,
    onPinJurado: () -> Unit,
    onAuditoria: () -> Unit,
    onCategoria: (Categoria) -> Unit,
    onSalir: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val titulo = when {
            rol == Rol.ADMIN -> if (!adminNombre.isNullOrBlank()) "Panel del admin ($adminNombre)" else "Panel del admin"
            rol == Rol.JURADO -> if (!adminNombre.isNullOrBlank()) "Panel del jurado ($adminNombre)" else "Panel del jurado"
            else -> "Panel"
        }
        Text(titulo, style = MaterialTheme.typography.headlineSmall)
        if (rol == Rol.ADMIN) {
            Button(onClick = onEquipos, modifier = Modifier.fillMaxWidth()) { Text("Equipos registrados") }
            OutlinedButton(onClick = onPinJurado, modifier = Modifier.fillMaxWidth()) { Text("PIN de acceso") }
            OutlinedButton(onClick = onAuditoria, modifier = Modifier.fillMaxWidth()) { Text("Bitácora de cambios") }
        }
        categorias.forEach { c ->
            Button(onClick = { onCategoria(c) }, modifier = Modifier.fillMaxWidth()) { Text(c.nombre) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSalir, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
    }
}

/** Cambiar el PIN del admin o del jurado (deben ser distintos). Pide repetirlo para evitar errores de dedo. */
@Composable
fun PinJuradoScreen(acceso: AccesoService, auditoria: AuditoriaService, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rol by remember { mutableStateOf(Rol.ADMIN) }
    var pin by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PIN de acceso", style = MaterialTheme.typography.headlineSmall)
        Text("El admin y el jurado tienen cada uno su PIN, y deben ser distintos. Guardar uno nuevo reemplaza el anterior.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("PIN del admin", rol == Rol.ADMIN) { rol = Rol.ADMIN; mensaje = null }
            Opcion("PIN del jurado", rol == Rol.JURADO) { rol = Rol.JURADO; mensaje = null }
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Nuevo PIN (de 4 a 8 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = repetir,
            onValueChange = { repetir = it },
            label = { Text("Repite el PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        mensaje?.let { Text(it, color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (pin != repetir) {
                    mensaje = "Los dos PIN no coinciden"; esError = true
                } else {
                    when (val r = acceso.configurarPin(correo = "", rol = rol, pin)) {
                        is Resultado.Ok -> {
                            val quien = if (rol == Rol.ADMIN) "del admin" else "del jurado"
                            mensaje = "PIN $quien guardado"; esError = false; pin = ""; repetir = ""
                            scope.launch { auditoria.registrar(Acciones.PIN_CAMBIADO, "config", null, "PIN de acceso $quien de $rol") }
                        }
                        is Resultado.Falla -> { mensaje = r.primero.mensaje; esError = true }
                    }
                }
            },
        ) { Text("Guardar PIN") }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

/** La foto del robot en la tarjeta del equipo; al tocarla se agranda o se encoge. */
@Composable
private fun FotoEquipo(registro: RegistroService, equipoId: Long) {
    var ampliada by remember { mutableStateOf(false) }
    val estado by produceState<Pair<Boolean, ImageBitmap?>>(false to null, equipoId) {
        val imagen = try {
            withContext(Dispatchers.IO) {
                registro.foto(equipoId)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        value = true to imagen
    }
    val (listo, imagen) = estado
    if (imagen != null) {
        Image(
            bitmap = imagen,
            contentDescription = "Foto del robot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (ampliada) 320.dp else 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { ampliada = !ampliada },
        )
    } else {
        Text(if (listo) "Sin foto del robot" else "Cargando foto...", style = MaterialTheme.typography.bodySmall)
    }
}

/** Lista de equipos con filtros y botones para aceptar, rechazar, marcar asistencia (Checklist) y exportar a Excel. */
@Composable
fun EquiposScreen(
    registro: RegistroService,
    esAdmin: Boolean,
    adminNombre: String? = null,
    onVolver: () -> Unit,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var categoria by remember { mutableStateOf<ClaveCategoria?>(null) }
    var estado by remember { mutableStateOf<EstadoEquipo?>(null) }
    var busquedaFolio by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var lista by remember { mutableStateOf<List<Equipo>>(emptyList()) }
    var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var porEliminar by remember { mutableStateOf<Equipo?>(null) }
    var verQr by remember { mutableStateOf<Equipo?>(null) }
    var mostrarEscanerQr by remember { mutableStateOf(false) }
    var mostrarConfigSheets by remember { mutableStateOf(false) }
    val nombres = categorias.associate { it.clave to it.nombre }

    LaunchedEffect(Unit) { categorias = registro.categorias() }
    LaunchedEffect(categoria, estado, version) { lista = registro.listar(estado, categoria) }

    val listaFiltrada = remember(lista, busquedaFolio) {
        if (busquedaFolio.isBlank()) lista
        else {
            val q = busquedaFolio.trim().lowercase()
            lista.filter {
                it.folio.lowercase().contains(q) ||
                    it.nombre.lowercase().contains(q) ||
                    it.robot.nombre.lowercase().contains(q) ||
                    it.capitan.lowercase().contains(q)
            }
        }
    }

    fun cambiar(equipo: Equipo, nuevo: EstadoEquipo) {
        scope.launch {
            when (val r = registro.cambiarEstado(equipo.id, nuevo)) {
                is Resultado.Ok -> { mensaje = null; version += 1 }
                is Resultado.Falla -> mensaje = r.primero.mensaje
            }
        }
    }

    fun cambiarAsistencia(equipo: Equipo) {
        scope.launch {
            val nuevaAsistencia = !equipo.asistencia
            val quien = if (nuevaAsistencia) adminNombre else null
            when (val r = registro.marcarAsistencia(equipo.id, nuevaAsistencia, quien)) {
                is Resultado.Ok -> {
                    mensaje = null
                    version += 1
                    if (GoogleSheetsSync.estaConfigurado(contexto)) {
                        GoogleSheetsSync.sincronizarAsistencia(
                            context = contexto,
                            folio = equipo.folio,
                            asistencia = nuevaAsistencia,
                            horaMs = System.currentTimeMillis(),
                            adminNombre = quien,
                        )
                    }
                }
                is Resultado.Falla -> mensaje = r.primero.mensaje
            }
        }
    }

    if (mostrarEscanerQr && esAdmin) {
        EscanerQrAdminDialog(
            esAdmin = true,
            equipos = lista,
            onRegistrarAsistencia = { equipo ->
                if (!equipo.asistencia) {
                    cambiarAsistencia(equipo)
                }
            },
            onCerrar = { mostrarEscanerQr = false },
        )
    }

    porEliminar?.let { equipo ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("Eliminar registro") },
            text = {
                Text(
                    "Se eliminará ${equipo.folio} · ${equipo.nombre} (robot ${equipo.robot.nombre}). " +
                        "No se puede deshacer. Si el equipo ya compitió, mejor recházalo.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    porEliminar = null
                    scope.launch {
                        when (val r = registro.eliminarEquipo(equipo.id)) {
                            is Resultado.Ok -> { mensaje = null; version += 1 }
                            is Resultado.Falla -> mensaje = r.primero.mensaje
                        }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar") } },
        )
    }

    if (mostrarConfigSheets) {
        ConfigurarGoogleSheetsDialog(
            contexto = contexto,
            equipos = lista,
            categorias = categorias,
            onCerrar = { mostrarConfigSheets = false },
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Equipos registrados (${listaFiltrada.size})", style = MaterialTheme.typography.headlineSmall)
                val presentes = listaFiltrada.count { it.asistencia }
                Text("Asistencia en sitio: $presentes / ${listaFiltrada.size} llegaron", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { ExcelChecklist.guardarYCompartirExcel(contexto, lista, categorias) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Exportar Excel")
            }
            if (esAdmin) {
                Button(
                    onClick = { mostrarEscanerQr = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Escanear QR")
                }
            }
            OutlinedButton(
                onClick = { mostrarConfigSheets = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Google Sheets")
            }
        }

        OutlinedTextField(
            value = busquedaFolio,
            onValueChange = { busquedaFolio = it },
            label = { Text("Escanear o buscar por Folio, Equipo o Robot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("Todas", categoria == null) { categoria = null }
            categorias.forEach { c -> Opcion(c.nombre, categoria == c.clave) { categoria = c.clave } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("Todos", estado == null) { estado = null }
            EstadoEquipo.entries.forEach { e -> Opcion(etiqueta(e), estado == e) { estado = e } }
        }
        mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (listaFiltrada.isEmpty()) {
            Text("No hay equipos con ese filtro.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listaFiltrada, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${e.folio} · ${e.nombre}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (e.asistencia) {
                                Text("LLEGÓ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("PENDIENTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Text("Capitán: ${e.capitan} · ${e.plantel}")
                        if (e.integrantes.isNotEmpty()) {
                            Text("Participantes (${1 + e.integrantes.size}): ${(listOf(e.capitan) + e.integrantes).joinToString(", ")}")
                        }
                        Text("Robot: ${e.robot.nombre} · ${nombres[e.robot.categoria] ?: e.robot.categoria.valor}")
                        Text("${Peso.formatearKg(e.robot.pesoG)} · ${e.robot.tamanoCm} cm")
                        FotoEquipo(registro, e.id)
                        Text("Estado: ${etiqueta(e.estado)}", style = MaterialTheme.typography.labelLarge)
                        e.horaLlegada?.let {
                            val quien = e.adminRegistro?.let { adm -> " · por $adm" } ?: ""
                            Text("Llegada registrada: ${formatoFecha.format(Instant.ofEpochMilli(it))}$quien", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { cambiarAsistencia(e) }) {
                                Text(if (e.asistencia) "Marcar Ausente" else "Marcar Llegada")
                            }
                            if (e.estado != EstadoEquipo.ACEPTADO) Button(onClick = { cambiar(e, EstadoEquipo.ACEPTADO) }) { Text("Aceptar") }
                            if (e.estado != EstadoEquipo.REVISION) OutlinedButton(onClick = { cambiar(e, EstadoEquipo.REVISION) }) { Text("Revisión") }
                            if (e.estado != EstadoEquipo.RECHAZADO) OutlinedButton(onClick = { cambiar(e, EstadoEquipo.RECHAZADO) }) { Text("Rechazar") }
                            TextButton(onClick = { verQr = e }) { Text("Ver QR") }
                            if (esAdmin) {
                                TextButton(onClick = { porEliminar = e }) {
                                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private val formatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())

private fun etiquetaAccion(accion: String): String = when (accion) {
    Acciones.EQUIPO_REGISTRADO -> "Equipo registrado"
    Acciones.ESTADO_EQUIPO_CAMBIADO -> "Estado de equipo cambiado"
    Acciones.LIMITES_ACTUALIZADOS -> "Límites de categoría cambiados"
    Acciones.ORDEN_SORTEADO -> "Orden de salida sorteado"
    Acciones.INTENTO_REGISTRADO -> "Intento capturado"
    Acciones.CARRERA_EXTRA_REGISTRADA -> "Carrera extra capturada"
    Acciones.INTENTO_ANULADO -> "Intento anulado"
    Acciones.CATEGORIA_CERRADA -> "Categoría cerrada"
    Acciones.CATEGORIA_REABIERTA -> "Categoría reabierta"
    Acciones.TERMINOS_PUBLICADOS -> "Términos y condiciones publicados"
    Acciones.LLAVES_GENERADAS -> "Llaves sorteadas"
    Acciones.LLAVES_PROGRAMADAS -> "Canchas asignadas"
    Acciones.PARTIDA_INICIADA -> "Partida iniciada"
    Acciones.PARTIDA_FINALIZADA -> "Resultado de partida"
    Acciones.EQUIPO_ELIMINADO -> "Registro de equipo eliminado"
    Acciones.PIN_CAMBIADO -> "PIN de acceso cambiado"
    else -> accion
}

private fun etiquetaActor(actor: String): String = when (actor) {
    "ADMIN" -> "Admin"
    "JURADO" -> "Jurado"
    "PARTICIPANTE" -> "Participante"
    else -> actor
}

/** Bitácora: quién hizo qué y cuándo, lo más reciente primero. Solo la ve el admin. */
@Composable
fun AuditoriaScreen(servicio: AuditoriaService, onVolver: () -> Unit) {
    var eventos by remember { mutableStateOf<List<EventoAuditoria>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(version) {
        cargando = true
        try {
            eventos = servicio.listar()
            error = null
        } catch (e: Exception) {
            error = "No se pudo cargar: ${e.message}"
        }
        cargando = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bitácora de cambios", style = MaterialTheme.typography.headlineSmall)
        Text("Los 200 movimientos más recientes.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (cargando) Text("Cargando...")
        if (!cargando && eventos.isEmpty() && error == null) Text("Todavía no hay movimientos.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eventos, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(etiquetaAccion(e.accion), style = MaterialTheme.typography.titleSmall)
                        e.detalle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            "${etiquetaActor(e.actor)} · ${formatoFecha.format(Instant.ofEpochMilli(e.creadoEn))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = { version += 1 }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") }
        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

/** Diálogo para configurar el Webhook de Google Sheets y sincronizar en vivo. */
@Composable
fun ConfigurarGoogleSheetsDialog(
    contexto: Context,
    equipos: List<Equipo>,
    categorias: List<Categoria>,
    onCerrar: () -> Unit,
) {
    var webhookUrl by remember { mutableStateOf(GoogleSheetsSync.obtenerWebhookUrl(contexto)) }
    var sheetUrl by remember { mutableStateOf(GoogleSheetsSync.obtenerSheetUrl(contexto)) }
    var sincronizando by remember { mutableStateOf(false) }
    var mensajeSincronizacion by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Google Sheets Compartido en Vivo") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Conecta una hoja de cálculo para que las asistencias registradas por los administradores se sincronicen en vivo en la nube.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    label = { Text("URL del Webhook (Apps Script)") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sheetUrl,
                    onValueChange = { sheetUrl = it },
                    label = { Text("Enlace a la Hoja de Cálculo (Opcional)") },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                mensajeSincronizacion?.let {
                    Text(
                        it,
                        color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !sincronizando && webhookUrl.isNotBlank(),
                        onClick = {
                            GoogleSheetsSync.guardarWebhookUrl(contexto, webhookUrl)
                            GoogleSheetsSync.guardarSheetUrl(contexto, sheetUrl)
                            sincronizando = true
                            mensajeSincronizacion = "Sincronizando..."
                            scope.launch {
                                val (ok, msg) = GoogleSheetsSync.sincronizarTodo(contexto, equipos, categorias)
                                sincronizando = false
                                esError = !ok
                                mensajeSincronizacion = msg
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (sincronizando) "Enviando..." else "Sincronizar Todo")
                    }

                    if (sheetUrl.isNotBlank() || webhookUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { GoogleSheetsSync.abrirGoogleSheet(contexto) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Abrir Hoja")
                        }
                    }
                }

                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(GoogleSheetsSync.CODIGO_APPS_SCRIPT))
                        mensajeSincronizacion = "Código copiado. Pégalo en Extensiones > Apps Script de tu Google Sheet."
                        esError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copiar Código para Google Apps Script")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                GoogleSheetsSync.guardarWebhookUrl(contexto, webhookUrl)
                GoogleSheetsSync.guardarSheetUrl(contexto, sheetUrl)
                onCerrar()
            }) { Text("Guardar y Cerrar") }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Cancelar") }
        },
    )
}
