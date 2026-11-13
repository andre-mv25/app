package com.utc.guerrarobots.domain.repository

import com.utc.guerrarobots.domain.model.AceptacionTerminos
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.Plantel
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.model.Sorteo
import com.utc.guerrarobots.domain.model.Terminos

/** Ejecuta varias operaciones como una sola: o se guardan todas o ninguna. */
interface Transaccion {
    suspend fun <T> ejecutar(bloque: suspend () -> T): T
}

/** Hora actual en milisegundos. Se inyecta para poder probar sin depender del reloj real. */
fun interface Reloj {
    fun ahora(): Long
}

interface CatalogoRepository {
    suspend fun planteles(): List<Plantel>
    suspend fun agregarPlantel(nombre: String): Plantel
    /** Todas las categorías, en su orden. */
    suspend fun categorias(): List<Categoria>
    /** Una categoría; si todavía no está guardada, la base con esa clave. */
    suspend fun categoria(clave: ClaveCategoria): Categoria
    suspend fun guardarCategoria(categoria: Categoria)

    /** Palabras ya normalizadas (minúsculas, sin acentos). */
    suspend fun palabrasBloqueadas(): Set<String>
    suspend fun agregarPalabraBloqueada(normalizada: String)
}

interface EquipoRepository {
    /** Guarda el equipo con sus integrantes y devuelve su id. */
    suspend fun insertar(equipo: Equipo): Long
    suspend fun obtener(id: Long): Equipo?
    suspend fun listar(estado: EstadoEquipo? = null, categoria: ClaveCategoria? = null): List<Equipo>

    /** Las comparaciones de nombre ignoran mayúsculas, acentos y espacios repetidos. */
    suspend fun existeNombreEquipo(nombre: String): Boolean
    suspend fun existeNombreRobot(nombre: String): Boolean
    suspend fun actualizarEstado(id: Long, estado: EstadoEquipo)
    suspend fun actualizarFolio(id: Long, folio: String)
    suspend fun actualizarLogo(id: Long, logoUri: String?)
    suspend fun registrarAsistencia(id: Long, presente: Boolean, horaMs: Long? = System.currentTimeMillis(), adminNombre: String? = null)
    suspend fun eliminar(id: Long)
}

/** Guarda la foto de cada robot (una imagen JPEG ya reducida), aparte de los datos del equipo. */
interface FotoRepository {
    suspend fun guardar(equipoId: Long, jpeg: ByteArray)
    suspend fun obtener(equipoId: Long): ByteArray?
    suspend fun eliminar(equipoId: Long)
}

interface PartidaRepository {
    suspend fun insertar(partida: Partida): Long
    suspend fun obtener(id: Long): Partida?
    suspend fun listar(categoria: ClaveCategoria, estado: EstadoPartida? = null): List<Partida>
    suspend fun actualizar(partida: Partida)
    suspend fun eliminarPorCategoria(categoria: ClaveCategoria)
}

interface SorteoRepository {
    suspend fun insertar(sorteo: Sorteo): Long
    suspend fun ultimo(categoria: ClaveCategoria): Sorteo?
}

interface IntentoRepository {
    suspend fun insertar(intento: Intento): Long
    suspend fun listar(categoria: ClaveCategoria): List<Intento>
    suspend fun listarPorEquipo(categoria: ClaveCategoria, equipoId: Long): List<Intento>
    suspend fun eliminar(id: Long)
}

/** Guarda el hash del PIN de cada persona por rol (correo + rol). */
interface PinStore {
    fun obtenerHash(correo: String, rol: Rol): String?
    fun guardarHash(correo: String, rol: Rol, hash: String)
    fun borrarHash(correo: String, rol: Rol)
}

/** Quién tiene la sesión ahora: el nombre que se anota en la bitácora. */
fun interface Actor {
    fun actual(): String
}

interface AuditoriaRepository {
    suspend fun insertar(evento: EventoAuditoria): Long

    /** Los más recientes primero. */
    suspend fun listar(limite: Int = 200): List<EventoAuditoria>
}

interface TerminosRepository {
    /** La versión más reciente, o null si todavía no se publica ninguna. */
    suspend fun vigente(): Terminos?

    suspend fun publicar(terminos: Terminos)

    suspend fun insertarAceptacion(aceptacion: AceptacionTerminos): Long

    suspend fun aceptacionesDe(equipoId: Long): List<AceptacionTerminos>
}

/** Quién está autorizado como jurado o admin, por correo. */
interface AutorizacionRepository {
    /** El rol de ese correo (ya en minúsculas), o null si no está autorizado. */
    suspend fun rolDe(correo: String): Rol?
}

/** Configuración de la última versión disponible de la app. */
interface ActualizacionRepository {
    suspend fun obtenerConfiguracion(): com.utc.guerrarobots.domain.model.InfoActualizacion?
    suspend fun guardarConfiguracion(info: com.utc.guerrarobots.domain.model.InfoActualizacion)
}
