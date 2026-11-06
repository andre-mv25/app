package com.utc.guerrarobots.di

import com.google.firebase.firestore.FirebaseFirestore
import com.utc.guerrarobots.data.firebase.*
import com.utc.guerrarobots.domain.model.CategoriasBase
import com.utc.guerrarobots.domain.model.ConfigTorneo
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.rules.Texto
import com.utc.guerrarobots.domain.service.*
import com.utc.guerrarobots.ui.ProveedorInicioSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val equipos = EquipoFirestore(db, contadores)
    private val partidas = PartidaFirestore(db, contadores)
    private val sorteos = SorteoFirestore(db, contadores)
    private val intentos = IntentoFirestore(db, contadores)
    private val pines = PinFirestore(db, scope)
    private val bitacora = AuditoriaFirestore(db, contadores)
    private val versionesTerminos = TerminosFirestore(db, contadores)
    private val autorizados = AutorizacionFirestore(db)

    val acceso = AccesoService(pines)
    val sesion = SesionService(autorizados, acceso)
    /** En la bitácora se anota el correo de quien inició sesión con Google; con PIN, su rol. */
    val auditoria = AuditoriaService(bitacora, reloj) { acceso.usuarioActual?.correo ?: acceso.rolActual.name }
    val terminos = TerminosService(versionesTerminos, reloj, auditoria)
    val registro = RegistroService(
        equipos, catalogo, partidas, tx, config, auditoria, terminos,
        intentos = intentos,
        esAdmin = { acceso.puede(Rol.ADMIN) },
    )
    val llaves = LlavesService(equipos, partidas, sorteos, catalogo, tx, reloj, auditoria)
    val tiempos = TiemposService(equipos, intentos, sorteos, catalogo, tx, reloj, config, auditoria)
    val consulta = ConsultaService(equipos, catalogo, llaves, tiempos)

    /** Se llama una vez al abrir la app, antes de mostrar pantallas. */
    suspend fun iniciar() {
        pines.cargar()
        val guardadas = catalogo.categorias().map { it.clave }.toSet()
        CategoriasBase.porDefecto.filter { it.clave !in guardadas }.forEach { catalogo.guardarCategoria(it) }
        if (catalogo.planteles().isEmpty()) {
            listOf(
                "Aragón", "Atizapán", "Chalco", "Coacalco", "Cuautitlán", "Ecatepec",
                "Huehuetoca", "Ixtapaluca", "Iztapalapa", "Los Reyes", "Neza",
                "Observatorio", "Puebla", "Tlalpan", "Tlalnepantla", "Toluca",
                "Toreo", "Zona Rosa",
            ).forEach { catalogo.agregarPlantel(it) }
        }
        if (catalogo.palabrasBloqueadas().isEmpty()) {
            listOf("puto", "puta", "pendejo", "mierda", "verga", "chingar", "cabron", "culero")
                .forEach { catalogo.agregarPalabraBloqueada(Texto.normalizar(it)) }
        }
        terminos.sembrar()
    }
}
