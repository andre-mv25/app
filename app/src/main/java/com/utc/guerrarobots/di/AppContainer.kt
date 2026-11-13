package com.utc.guerrarobots.di

import com.google.firebase.firestore.FirebaseFirestore
import com.utc.guerrarobots.data.firebase.*
import com.utc.guerrarobots.domain.model.CategoriasBase
import com.utc.guerrarobots.domain.model.PlantelesBase
import com.utc.guerrarobots.domain.model.ConfigTorneo
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.rules.Texto
import com.utc.guerrarobots.domain.service.*
import com.utc.guerrarobots.ui.ProveedorInicioSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.withTimeoutOrNull

class AppContainer(
    db: FirebaseFirestore,
    val inicioSesion: ProveedorInicioSesion,
    config: ConfigTorneo = ConfigTorneo(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tx = TransaccionFirestore()
    private val reloj = Reloj { System.currentTimeMillis() }
    private val contadores = Contadores(db)

    val catalogo = CatalogoFirestore(db, contadores)
    val equipos = EquipoFirestore(db, contadores)
    val partidas = PartidaFirestore(db, contadores)
    val sorteos = SorteoFirestore(db, contadores)
    val intentos = IntentoFirestore(db, contadores)
    private val pines = PinFirestore(db, scope)
    val bitacora = AuditoriaFirestore(db, contadores)
    private val versionesTerminos = TerminosFirestore(db, contadores)
    private val autorizados = AutorizacionFirestore(db)
    private val fotos = FotoFirestore(db)

    val acceso = AccesoService(pines)
    val sesion = SesionService(autorizados, acceso)
    /** En la bitácora se anota el nombre del admin/juez si entró con PIN, su correo si entró con Google, o su rol. */
    val auditoria = AuditoriaService(bitacora, reloj) {
        acceso.nombreActual ?: acceso.usuarioActual?.correo ?: acceso.rolActual.name
    }
    val terminos = TerminosService(versionesTerminos, reloj, auditoria)
    val registro = RegistroService(
        equipos, catalogo, partidas, tx, config, auditoria, terminos,
        intentos = intentos,
        esAdmin = { acceso.puede(Rol.ADMIN) },
        fotos = fotos,
    )
    val llaves = LlavesService(equipos, partidas, sorteos, catalogo, tx, reloj, auditoria)
    val tiempos = TiemposService(equipos, intentos, sorteos, catalogo, tx, reloj, config, auditoria)
    val reportes = ReporteService(equipos, catalogo, intentos, partidas, bitacora, tiempos, llaves, reloj) {
        acceso.usuarioActual?.correo ?: acceso.rolActual.name
    }
    val consulta = ConsultaService(equipos, catalogo, llaves, tiempos)
    val actualizacion = ActualizacionService(ActualizacionFirestore(db), auditoria)

    /** Se llama una vez al abrir la app, antes de mostrar pantallas. */
    suspend fun iniciar() {
        withTimeoutOrNull(6_000L) {
            runCatching {
                val guardadas = catalogo.categorias().map { it.clave }.toSet()
                CategoriasBase.porDefecto.filter { it.clave !in guardadas }.forEach {
                    runCatching { catalogo.guardarCategoria(it) }
                }
            }
            runCatching {
                val plantelesGuardados = catalogo.planteles().map { Texto.normalizar(it.nombre) }.toSet()
                PlantelesBase.todos
                    .filter { Texto.normalizar(it) !in plantelesGuardados }
                    .forEach { runCatching { catalogo.agregarPlantel(it) } }
            }
            runCatching {
                if (catalogo.palabrasBloqueadas().isEmpty()) {
                    listOf("puto", "puta", "pendejo", "mierda", "verga", "chingar", "cabron", "culero")
                        .forEach { runCatching { catalogo.agregarPalabraBloqueada(Texto.normalizar(it)) } }
                }
            }
            runCatching { terminos.sembrar() }
        }
    }
}
