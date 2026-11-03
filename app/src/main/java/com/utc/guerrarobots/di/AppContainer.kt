package com.utc.guerrarobots.di

import com.google.firebase.firestore.FirebaseFirestore
import com.utc.guerrarobots.data.firebase.*
import com.utc.guerrarobots.domain.model.CategoriasBase
import com.utc.guerrarobots.domain.model.ConfigTorneo
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.rules.Texto
import com.utc.guerrarobots.domain.service.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(db: FirebaseFirestore, config: ConfigTorneo = ConfigTorneo()) {
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

    val acceso = AccesoService(pines)
    val auditoria = AuditoriaService(bitacora, reloj) { acceso.rolActual.name }
    val terminos = TerminosService(versionesTerminos, reloj, auditoria)
    val registro = RegistroService(equipos, catalogo, partidas, tx, config, auditoria, terminos)
    val llaves = LlavesService(equipos, partidas, sorteos, catalogo, tx, reloj, auditoria)
    val tiempos = TiemposService(equipos, intentos, sorteos, catalogo, tx, reloj, config, auditoria)

    /** Se llama una vez al abrir la app, antes de mostrar pantallas. */
    suspend fun iniciar() {
        pines.cargar()
        val guardadas = catalogo.categorias().map { it.clave }.toSet()
        CategoriasBase.porDefecto.filter { it.clave !in guardadas }.forEach { catalogo.guardarCategoria(it) }
        if (catalogo.planteles().isEmpty()) catalogo.agregarPlantel("Toluca")
        if (catalogo.palabrasBloqueadas().isEmpty()) {
            listOf("puto", "puta", "pendejo", "mierda", "verga", "chingar", "cabron", "culero")
                .forEach { catalogo.agregarPalabraBloqueada(Texto.normalizar(it)) }
        }
        terminos.sembrar()
    }
}
