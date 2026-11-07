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
import kotlinx.coroutines.launch

sealed interface Pantalla {
    data object Inicio : Pantalla
    data object Publico : Pantalla
    data class PublicoCategoria(val categoria: Categoria) : Pantalla
    data class Terminos(val categoria: Categoria) : Pantalla
    data object Categorias : Pantalla
    data class Registro(val categoria: Categoria) : Pantalla
    data class Listo(val equipo: Equipo) : Pantalla
    data object Acceso : Pantalla
    data object Panel : Pantalla
    data object Equipos : Pantalla
    data object PinJurado : Pantalla
    data object Auditoria : Pantalla
    data class Tiempos(val categoria: Categoria) : Pantalla
    data class Llaves(val categoria: Categoria) : Pantalla
}

/** Espera a que la app se conecte a Firestore antes de mostrar las pantallas. */
@Composable
fun GuerraRobotsNavegacion(contenedor: AppContainer, modifier: Modifier = Modifier) {
    var listo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            contenedor.iniciar()
            listo = true
        } catch (e: Exception) {
            val lugar = e.stackTrace.take(6).joinToString("\n") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            error = (e.message ?: "sin conexión") + "\n" + lugar
        }
    }
    when {
        listo -> NavegacionListo(contenedor, modifier)
        error != null -> Text("No se pudo conectar: $error", modifier)
        else -> Text("Conectando...", modifier)
    }
}

@Composable
private fun NavegacionListo(contenedor: AppContainer, modifier: Modifier = Modifier) {
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
    var rol by remember { mutableStateOf(Rol.PARTICIPANTE) }
    var planteles by remember { mutableStateOf<List<String>>(emptyList()) }
    var categorias by remember { mutableStateOf<List<Categoria>>(emptyList()) }
    var terminos by remember { mutableStateOf<com.utc.guerrarobots.domain.model.Terminos?>(null) }
    var versionAceptada by remember { mutableStateOf<Int?>(null) }
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
        when (pantalla) {
            Pantalla.Inicio, Pantalla.Categorias, Pantalla.Acceso, Pantalla.Publico -> pantalla = Pantalla.Inicio
            is Pantalla.PublicoCategoria -> pantalla = Pantalla.Publico
            is Pantalla.Terminos -> pantalla = Pantalla.Categorias
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
            )
            Pantalla.Publico -> PublicoScreen(
                categorias = categorias,
                onElegir = { pantalla = Pantalla.PublicoCategoria(it) },
                onVolver = { pantalla = Pantalla.Inicio },
            )
            is Pantalla.Terminos -> TerminosScreen(
                categoria = p.categoria,
                terminos = terminos,
                onAceptar = { versionAceptada = it; pantalla = Pantalla.Registro(p.categoria) },
                onVolver = { pantalla = Pantalla.Categorias },
            )
            Pantalla.Categorias -> CategoriasScreen(
                categorias = categorias,
                onElegir = { versionAceptada = null; pantalla = Pantalla.Terminos(it) },
                onVolver = { pantalla = Pantalla.Inicio },
            )
            is Pantalla.Registro -> RegistroScreen(
                categoria = p.categoria,
                planteles = planteles,
                terminosVersion = versionAceptada,
                onEnviar = { contenedor.registro.registrar(it.copy(terminosVersion = versionAceptada)) },
                onListo = { pantalla = Pantalla.Listo(it) },
                onVolver = { pantalla = Pantalla.Terminos(p.categoria) },
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
