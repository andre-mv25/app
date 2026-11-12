package com.utc.guerrarobots.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.utc.guerrarobots.domain.model.*
import com.utc.guerrarobots.domain.repository.*
import com.utc.guerrarobots.domain.rules.Texto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class TransaccionFirestore : Transaccion {
    override suspend fun <T> ejecutar(bloque: suspend () -> T): T = bloque()
}

/** Ids numéricos únicos entre todos los teléfonos, con un documento contador por colección. */
class Contadores(private val db: FirebaseFirestore) {
    private class Rango(var siguiente: Long = 1, var limite: Long = 0)

    private val rangos = mutableMapOf<String, Rango>()
    private val mutex = Mutex()

    suspend fun siguiente(nombre: String, bloque: Int = 1): Long = mutex.withLock {
        val r = rangos.getOrPut(nombre) { Rango() }
        if (r.siguiente > r.limite) {
            val ref = db.collection("gr_contadores").document(nombre)
            val nuevoLimite = db.runTransaction { t ->
                val actual = t.get(ref).getLong("valor") ?: 0L
                t.set(ref, mapOf("valor" to actual + bloque))
                actual + bloque
            }.await()
            r.siguiente = nuevoLimite - bloque + 1
            r.limite = nuevoLimite
        }
        r.siguiente++
    }
}

private fun DocumentSnapshot.mapa(): Map<String, Any?>? = data

class CatalogoFirestore(db: FirebaseFirestore, private val contadores: Contadores) : CatalogoRepository {
    private val planteles = db.collection("gr_planteles")
    private val categorias = db.collection("gr_categorias")
    private val palabras = db.collection("gr_palabras")

    override suspend fun planteles(): List<Plantel> =
        planteles.get().await().documents.mapNotNull { d ->
            d.mapa()?.let { Plantel((it["id"] as Number).toLong(), it["nombre"] as String) }
        }.sortedBy { it.nombre }

    override suspend fun agregarPlantel(nombre: String): Plantel {
        val id = contadores.siguiente("planteles")
        planteles.document(id.toString()).set(mapOf("id" to id, "nombre" to nombre)).await()
        return Plantel(id, nombre)
    }

    override suspend fun categorias(): List<Categoria> =
        categorias.get().await().documents.mapNotNull { it.mapa() }.map { Mapeo.aCategoria(it) }.sortedBy { it.orden }

    override suspend fun categoria(clave: ClaveCategoria): Categoria =
        categorias.document(clave.valor).get().await().mapa()?.let { Mapeo.aCategoria(it) } ?: CategoriasBase.de(clave)

    override suspend fun guardarCategoria(categoria: Categoria) {
        categorias.document(categoria.clave.valor).set(Mapeo.aMapa(categoria)).await()
    }

    override suspend fun palabrasBloqueadas(): Set<String> = palabras.get().await().documents.map { it.id }.toSet()

    override suspend fun agregarPalabraBloqueada(normalizada: String) {
        palabras.document(normalizada).set(mapOf("palabra" to normalizada)).await()
    }
}

class EquipoFirestore(db: FirebaseFirestore, private val contadores: Contadores) : EquipoRepository {
    private val col = db.collection("gr_equipos")

    override suspend fun insertar(equipo: Equipo): Long {
        val id = contadores.siguiente("equipos")
        col.document(id.toString()).set(Mapeo.aMapa(equipo.copy(id = id))).await()
        return id
    }

    override suspend fun obtener(id: Long): Equipo? =
        col.document(id.toString()).get().await().mapa()?.let { Mapeo.aEquipo(it) }

    override suspend fun listar(estado: EstadoEquipo?, categoria: ClaveCategoria?): List<Equipo> {
        var q: com.google.firebase.firestore.Query = col
        if (estado != null) q = q.whereEqualTo("estado", estado.name)
        if (categoria != null) q = q.whereEqualTo("robotCategoria", categoria.name)
        return q.get().await().documents.mapNotNull { it.mapa()?.let(Mapeo::aEquipo) }.sortedBy { it.id }
    }

    override suspend fun existeNombreEquipo(nombre: String): Boolean =
        !col.whereEqualTo("nombreNorm", Texto.normalizar(nombre)).limit(1).get().await().isEmpty

    override suspend fun existeNombreRobot(nombre: String): Boolean =
        !col.whereEqualTo("robotNombreNorm", Texto.normalizar(nombre)).limit(1).get().await().isEmpty

    override suspend fun actualizarEstado(id: Long, estado: EstadoEquipo) {
        col.document(id.toString()).update("estado", estado.name).await()
    }

    override suspend fun actualizarFolio(id: Long, folio: String) {
        col.document(id.toString()).update("folio", folio).await()
    }

    override suspend fun actualizarLogo(id: Long, logoUri: String?) {
        col.document(id.toString()).update("logoUri", logoUri).await()
    }

    override suspend fun registrarAsistencia(id: Long, presente: Boolean, horaMs: Long?, adminNombre: String?) {
        col.document(id.toString()).update(
            mapOf(
                "asistencia" to presente,
                "horaLlegada" to if (presente) (horaMs ?: System.currentTimeMillis()) else null,
                "adminRegistro" to if (presente) adminNombre else null,
            ),
        ).await()
    }

    override suspend fun eliminar(id: Long) {
        col.document(id.toString()).delete().await()
    }
}

class PartidaFirestore(private val db: FirebaseFirestore, private val contadores: Contadores) : PartidaRepository {
    private val col: CollectionReference = db.collection("gr_partidas")

    override suspend fun insertar(partida: Partida): Long {
        val id = contadores.siguiente("partidas", bloque = 20)
        col.document(id.toString()).set(Mapeo.aMapa(partida.copy(id = id))).await()
        return id
    }

    override suspend fun obtener(id: Long): Partida? =
        col.document(id.toString()).get().await().mapa()?.let { Mapeo.aPartida(it) }

    override suspend fun listar(categoria: ClaveCategoria, estado: EstadoPartida?): List<Partida> =
        col.whereEqualTo("categoria", categoria.name).get().await().documents
            .mapNotNull { it.mapa()?.let(Mapeo::aPartida) }
            .filter { estado == null || it.estado == estado }
            .sortedWith(compareBy({ it.ronda }, { it.posicion }))

    override suspend fun actualizar(partida: Partida) {
        col.document(partida.id.toString()).set(Mapeo.aMapa(partida)).await()
    }

    override suspend fun eliminarPorCategoria(categoria: ClaveCategoria) {
        val docs = col.whereEqualTo("categoria", categoria.name).get().await().documents
        docs.chunked(400).forEach { grupo ->
            val lote = db.batch()
            grupo.forEach { lote.delete(it.reference) }
            lote.commit().await()
        }
    }
}

class SorteoFirestore(db: FirebaseFirestore, private val contadores: Contadores) : SorteoRepository {
    private val col = db.collection("gr_sorteos")

    override suspend fun insertar(sorteo: Sorteo): Long {
        val id = contadores.siguiente("sorteos")
        col.document(id.toString()).set(Mapeo.aMapa(sorteo.copy(id = id))).await()
        return id
    }

    override suspend fun ultimo(categoria: ClaveCategoria): Sorteo? =
        col.whereEqualTo("categoria", categoria.name).get().await().documents
            .mapNotNull { it.mapa()?.let(Mapeo::aSorteo) }.maxByOrNull { it.id }
}

class IntentoFirestore(db: FirebaseFirestore, private val contadores: Contadores) : IntentoRepository {
    private val col = db.collection("gr_intentos")

    override suspend fun insertar(intento: Intento): Long {
        val id = contadores.siguiente("intentos")
        col.document(id.toString()).set(Mapeo.aMapa(intento.copy(id = id))).await()
        return id
    }

    override suspend fun listar(categoria: ClaveCategoria): List<Intento> =
        col.whereEqualTo("categoria", categoria.name).get().await().documents
            .mapNotNull { it.mapa()?.let(Mapeo::aIntento) }.sortedBy { it.id }

    override suspend fun listarPorEquipo(categoria: ClaveCategoria, equipoId: Long): List<Intento> =
        listar(categoria).filter { it.equipoId == equipoId }.sortedBy { it.numero }

    override suspend fun eliminar(id: Long) {
        col.document(id.toString()).delete().await()
    }
}

class AuditoriaFirestore(db: FirebaseFirestore, private val contadores: Contadores) : AuditoriaRepository {
    private val col = db.collection("gr_auditoria")

    override suspend fun insertar(evento: EventoAuditoria): Long {
        val id = contadores.siguiente("auditoria")
        col.document(id.toString()).set(Mapeo.aMapa(evento.copy(id = id))).await()
        return id
    }

    override suspend fun listar(limite: Int): List<EventoAuditoria> =
        col.orderBy("id", Query.Direction.DESCENDING).limit(limite.toLong()).get().await().documents
            .mapNotNull { it.mapa()?.let(Mapeo::aEvento) }
}

class TerminosFirestore(db: FirebaseFirestore, private val contadores: Contadores) : TerminosRepository {
    private val versiones = db.collection("gr_terminos")
    private val aceptaciones = db.collection("gr_aceptaciones_terminos")

    override suspend fun vigente(): Terminos? =
        versiones.orderBy("version", Query.Direction.DESCENDING).limit(1).get().await().documents
            .firstNotNullOfOrNull { it.mapa()?.let(Mapeo::aTerminos) }

    override suspend fun publicar(terminos: Terminos) {
        versiones.document(terminos.version.toString()).set(Mapeo.aMapa(terminos)).await()
    }

    override suspend fun insertarAceptacion(aceptacion: AceptacionTerminos): Long {
        val id = contadores.siguiente("aceptaciones_terminos")
        aceptaciones.document(id.toString()).set(Mapeo.aMapa(aceptacion.copy(id = id))).await()
        return id
    }

    override suspend fun aceptacionesDe(equipoId: Long): List<AceptacionTerminos> =
        aceptaciones.whereEqualTo("equipoId", equipoId).get().await().documents
            .mapNotNull { it.mapa()?.let(Mapeo::aAceptacion) }.sortedBy { it.id }
}

/** Lista de correos autorizados: colección "autorizados", un documento por correo con el campo "rol". */
class AutorizacionFirestore(db: FirebaseFirestore) : AutorizacionRepository {
    private val col = db.collection("gr_autorizados")

    override suspend fun rolDe(correo: String): Rol? =
        (col.document(correo).get().await().data?.get("rol") as? String)
            ?.let { nombre -> Rol.entries.firstOrNull { it.name == nombre } }
}

/** PIN en Firestore por persona y rol: colección "gr_pines", un documento por correo con campos por rol. */
class PinFirestore(db: FirebaseFirestore, private val scope: CoroutineScope) : PinStore {
    private val col = db.collection("gr_pines")

    override suspend fun obtenerHash(correo: String, rol: Rol): String? {
        val doc = col.document(correo).get().await().data
        return doc?.get(rol.name) as? String
    }

    override suspend fun guardarHash(correo: String, rol: Rol, hash: String) {
        col.document(correo).set(mapOf(rol.name to hash), SetOptions.merge()).await()
    }

    override suspend fun borrarHash(correo: String, rol: Rol) {
        col.document(correo).update(mapOf(rol.name to com.google.firebase.firestore.FieldValue.delete())).await()
    }
}

/**
 * Fotos de los robots. Cada una es un JPEG ya reducido (unos 250 KB) guardado como texto Base64
 * en su propio documento, para no cargar las fotos al listar los equipos y sin usar Firebase Storage.
 */
class FotoFirestore(db: FirebaseFirestore) : FotoRepository {
    private val col = db.collection("gr_fotos")

    override suspend fun guardar(equipoId: Long, jpeg: ByteArray) {
        val texto = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        col.document(equipoId.toString()).set(mapOf("datos" to texto)).await()
    }

    override suspend fun obtener(equipoId: Long): ByteArray? =
        col.document(equipoId.toString()).get().await().getString("datos")
            ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }

    override suspend fun eliminar(equipoId: Long) {
        col.document(equipoId.toString()).delete().await()
    }
}
