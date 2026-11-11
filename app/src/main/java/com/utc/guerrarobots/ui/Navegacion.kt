package com.utc.guerrarobots.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.utc.guerrarobots.di.AppContainer
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.model.Resultado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Publico : Pantalla
    data class PublicoCategoria(val categoria: Categoria) : Pantalla
    data class Terminos(val categoria: Categoria) : Pantalla
    data object Categorias : Pantalla
    data class Registro(val categoria: Categoria) : Pantalla
    data class Listo(val equipo: Equipo) : Pantalla
    data class Competidor(val folio: String = "") : Pantalla
    data object Acceso : Pantalla
    data object Panel : Pantalla
    data object Equipos : Pantalla
    data object PinJurado : Pantalla
    data object Auditoria : Pantalla
    data class Tiempos(val categoria: Categoria) : Pantalla
    data class Llaves(val categoria: Categoria) : Pantalla
}

/**
 * Muestra la pantalla "EL TORNEO REGRESA A CASA" mientras la app se conecta a Firestore (y al menos
 * unos segundos), y después las pantallas. Si no hay conexión, ofrece reintentar.
 */
@Composable
fun GuerraRobotsNavegacion(contenedor: AppContainer, modifier: Modifier = Modifier) {
    var listo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }
    var splashTerminado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3_000)
        splashTerminado = true
    }
    LaunchedEffect(intento) {
        error = null
        try {
            contenedor.iniciar()
            listo = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val lugar = e.stackTrace.take(6).joinToString("\n") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            error = (e.message ?: "sin conexión") + "\n" + lugar
        }
    }
    val mensajeError = error
    when {
        mensajeError != null -> PantallaSinConexion(mensajeError, onReintentar = { intento += 1 }, modifier = modifier)
        listo && splashTerminado -> NavegacionListo(contenedor, modifier)
        else -> PantallaCarga(modifier)
    }
}

@Composable
private fun NavegacionListo(contenedor: AppContainer, modifier: Modifier = Modifier) {
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var rol by remember { mutableStateOf(Rol.PARTICIPANTE) }
    var planteles by remember { mutableStateOf<List<String>>(emptyList()) }
    var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
    var terminos by remember { mutableStateOf<com.utc.guerrarobots.domain.model.Terminos?>(null) }
    var borrador by remember { mutableStateOf(BorradorRegistro()) }
    var erroresRegistro by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var enviando by remember { mutableStateOf(false) }
    var errorEnvio by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pantalla) {
        val actual = pantalla
        if (actual is Pantalla.Terminos) {
            terminos = null
            terminos = contenedor.terminos.paraCategoria(actual.categoria.clave)
        }
        planteles = contenedor.catalogo.planteles().map { it.nombre }
        categorias = contenedor.catalogo.categorias()
    }

    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()

    fun cerrarSesion() {
        contenedor.acceso.salir()
        scope.launch { contenedor.inicioSesion.cerrar(contexto) }
        rol = Rol.PARTICIPANTE
        pantalla = Pantalla.Inicio
    }

    BackHandler(enabled = pantalla != Pantalla.Inicio) {
        when (val actual = pantalla) {
            Pantalla.Inicio, Pantalla.Categorias, Pantalla.Acceso, Pantalla.Publico -> pantalla = Pantalla.Inicio
            is Pantalla.Competidor -> pantalla = Pantalla.Inicio
            is Pantalla.PublicoCategoria -> pantalla = Pantalla.Publico
            is Pantalla.Terminos -> {
                if (!enviando) pantalla = Pantalla.Registro(actual.categoria)
            }
            is Pantalla.Registro -> pantalla = Pantalla.Categorias
            is Pantalla.Listo -> pantalla = Pantalla.Inicio
            Pantalla.Panel -> cerrarSesion()
            Pantalla.Equipos, Pantalla.PinJurado, Pantalla.Auditoria, is Pantalla.Tiempos, is Pantalla.Llaves -> pantalla = Pantalla.Panel
        }
    }

    Box(modifier) {
        when (val p = pantalla) {
            Pantalla.Inicio -> InicioScreen(
                onComenzar = { pantalla = Pantalla.Categorias },
                onPublico = { pantalla = Pantalla.Publico },
                onAcceso = { pantalla = Pantalla.Acceso },
                onMiCompetencia = {
                    val pref = contexto.getSharedPreferences("guerra_robots_prefs", android.content.Context.MODE_PRIVATE)
                    val folioGuardado = pref.getString("ultimo_folio", "") ?: ""
                    pantalla = Pantalla.Competidor(folioGuardado)
                },
            )
            Pantalla.Publico -> PublicoScreen(
                categorias = categorias,
                onElegir = { pantalla = Pantalla.PublicoCategoria(it) },
                onVolver = { pantalla = Pantalla.Inicio },
            )
            is Pantalla.Competidor -> PantallaCompetidor(
                servicio = contenedor.consulta,
                folioInicial = p.folio,
                categorias = categorias,
                onVolver = { pantalla = Pantalla.Inicio },
            )
            Pantalla.Categorias -> CategoriasScreen(
                categorias = categorias,
                onElegir = {
                    borrador = BorradorRegistro()
                    erroresRegistro = emptyMap()
                    pantalla = Pantalla.Registro(it)
                },
                onVolver = { pantalla = Pantalla.Inicio },
            )
            is Pantalla.Registro -> RegistroScreen(
                categoria = p.categoria,
                planteles = planteles,
                inicial = borrador,
                errores = erroresRegistro,
                onContinuar = {
                    borrador = it
                    erroresRegistro = emptyMap()
                    errorEnvio = null
                    pantalla = Pantalla.Terminos(p.categoria)
                },
                onVolver = { pantalla = Pantalla.Categorias },
            )
            // Último paso: aceptar reglas, sanciones y aviso de privacidad. Aquí se envía el registro.
            is Pantalla.Terminos -> TerminosScreen(
                categoria = p.categoria,
                terminos = terminos,
                enviando = enviando,
                error = errorEnvio,
                onAceptar = { version ->
                    val solicitud = borrador.aSolicitud(p.categoria.clave, version)
                    if (solicitud == null) {
                        erroresRegistro = mapOf("general" to "Revisa el peso y el tamaño del robot")
                        pantalla = Pantalla.Registro(p.categoria)
                    } else {
                        scope.launch {
                            enviando = true
                            errorEnvio = null
                            try {
                                when (val r = contenedor.registro.registrar(solicitud)) {
                                    is Resultado.Ok -> {
                                        contexto.getSharedPreferences("guerra_robots_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("ultimo_folio", r.valor.folio)
                                            .apply()
                                        borrador = BorradorRegistro()
                                        erroresRegistro = emptyMap()
                                        pantalla = Pantalla.Listo(r.valor)
                                    }
                                    is Resultado.Falla -> {
                                        val mapa = r.errores.associate { (it.campo ?: "general") to it.mensaje }
                                        if (mapa.keys.all { it == "terminos" }) {
                                            errorEnvio = mapa.values.first()
                                        } else {
                                            erroresRegistro = mapa
                                            pantalla = Pantalla.Registro(p.categoria)
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorEnvio = "No se pudo enviar el registro. Revisa tu conexión e intenta de nuevo."
                            }
                            enviando = false
                        }
                    }
                },
                onVolver = { pantalla = Pantalla.Registro(p.categoria) },
            )
            is Pantalla.Listo -> ListoScreen(
                p.equipo,
                nombreCategoria = categorias.firstOrNull { it.clave == p.equipo.robot.categoria }?.nombre ?: p.equipo.robot.categoria.valor,
                onInicio = { pantalla = Pantalla.Inicio },
            )
            Pantalla.Acceso -> AccesoScreen(
                acceso = contenedor.acceso,
                sesion = contenedor.sesion,
                inicioSesion = contenedor.inicioSesion,
                onEntro = { rol = it; pantalla = Pantalla.Panel },
                onRestablecer = { rol = Rol.ADMIN; pantalla = Pantalla.PinJurado },
                onVolver = { pantalla = Pantalla.Inicio },
            )
            Pantalla.Panel -> PanelScreen(
                rol = rol,
                categorias = categorias,
                onEquipos = { pantalla = Pantalla.Equipos },
                onPinJurado = { pantalla = Pantalla.PinJurado },
                onAuditoria = { pantalla = Pantalla.Auditoria },
                onCategoria = { pantalla = if (it.metodo == Metodo.LLAVES) Pantalla.Llaves(it) else Pantalla.Tiempos(it) },
                onSalir = { cerrarSesion() },
            )
            Pantalla.Equipos -> EquiposScreen(contenedor.registro, esAdmin = rol == Rol.ADMIN, onVolver = { pantalla = Pantalla.Panel })
            Pantalla.PinJurado -> PinJuradoScreen(contenedor.acceso, contenedor.auditoria, onVolver = { pantalla = Pantalla.Panel })
            Pantalla.Auditoria -> AuditoriaScreen(contenedor.auditoria, onVolver = { pantalla = Pantalla.Panel })
            is Pantalla.Llaves -> LlavesScreen(
                servicio = contenedor.llaves,
                info = p.categoria,
                esAdmin = rol == Rol.ADMIN,
                onVolver = { pantalla = Pantalla.Panel },
            )
            is Pantalla.Tiempos -> TiemposScreen(
                servicio = contenedor.tiempos,
                info = p.categoria,
                esAdmin = rol == Rol.ADMIN,
                onVolver = { pantalla = Pantalla.Panel },
            )
            is Pantalla.PublicoCategoria -> PublicoCategoriaScreen(
                categoria = p.categoria,
                registro = contenedor.registro,
                tiempos = contenedor.tiempos,
                llaves = contenedor.llaves,
                onVolver = { pantalla = Pantalla.Publico },
            )
        }
    }
}
