package com.utc.guerrarobots.domain.model

/** Cómo se clasifica una categoría. */
enum class Metodo { RANKING_TIEMPO, LLAVES }

/**
 * Identificador estable de una categoría. Es solo una clave de texto: el nombre, el método y los
 * límites viven en [Categoria], que se guarda en la base. Agregar una categoría no toca el código.
 */
@JvmInline
value class ClaveCategoria(val valor: String) {
    val name: String get() = valor

    companion object {
        val SIGUE_LINEAS = ClaveCategoria("SIGUE_LINEAS")
        val FUTBOL = ClaveCategoria("FUTBOL")
        val LABERINTO = ClaveCategoria("LABERINTO")
        val COMBATE = ClaveCategoria("COMBATE")
        val VELOCIDAD = ClaveCategoria("VELOCIDAD")
    }
}

/**
 * Una categoría del torneo, tal como se guarda. [canchas] son las pistas o canchas disponibles y
 * [intentosMaximos] los intentos regulares por equipo (solo cuenta en las de tiempo).
 * Si un límite técnico es null, no se revisa.
 */
data class Categoria(
    val clave: ClaveCategoria,
    val nombre: String = clave.valor,
    val metodo: Metodo = Metodo.RANKING_TIEMPO,
    val pesoMinG: Int? = null,
    val pesoMaxG: Int? = null,
    val tamanoMaxCm: Double? = null,
    val cerrada: Boolean = false,
    val intentosMaximos: Int = 2,
    val canchas: Int = 1,
    /** Posición al mostrar la lista. */
    val orden: Int = 0,
)

/** Las categorías con las que nace el torneo. Después se pueden cambiar o agregar en la base. */
object CategoriasBase {
    val porDefecto: List<Categoria> = listOf(
        Categoria(ClaveCategoria.SIGUE_LINEAS, "Sigue líneas", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 2, orden = 1),
        Categoria(ClaveCategoria.FUTBOL, "Fútbol robot", Metodo.LLAVES, canchas = 4, orden = 2),
        Categoria(ClaveCategoria.LABERINTO, "Laberinto", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 1, orden = 3),
        Categoria(ClaveCategoria.COMBATE, "Combate", Metodo.LLAVES, canchas = 2, orden = 4),
        Categoria(ClaveCategoria.VELOCIDAD, "Robots de velocidad", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 1, orden = 5),
    )

    /** La categoría base con esa clave; si no existe, una genérica por tiempo. */
    fun de(clave: ClaveCategoria): Categoria = porDefecto.firstOrNull { it.clave == clave } ?: Categoria(clave)
}

enum class EstadoEquipo { PENDIENTE, REVISION, ACEPTADO, RECHAZADO }

data class Plantel(val id: Long, val nombre: String)

data class Robot(
    val nombre: String,
    val categoria: ClaveCategoria,
    val pesoG: Int,
    /** Lado más largo del robot, en centímetros. */
    val tamanoCm: Double,
)

data class Equipo(
    val id: Long = 0,
    val folio: String = "",
    val nombre: String,
    val capitan: String,
    val plantel: String,
    val jefeCarrera: String,
    val logoUri: String? = null,
    val estado: EstadoEquipo = EstadoEquipo.PENDIENTE,
    val integrantes: List<String>,
    val robot: Robot,
)

enum class Lado { A, B }

enum class EstadoPartida { PENDIENTE, PROGRAMADA, EN_CURSO, FINALIZADA }

/**
 * Un cruce de las llaves. La posición cuenta desde 1 dentro de su ronda.
 * Un "bye" es un cruce sin rival: el equipo pasa directo y la partida nace FINALIZADA.
 */
data class Partida(
    val id: Long = 0,
    val categoria: ClaveCategoria,
    val ronda: Int,
    val posicion: Int,
    val cancha: Int? = null,
    val turno: Int? = null,
    val equipoAId: Long? = null,
    val equipoBId: Long? = null,
    val esBye: Boolean = false,
    val estado: EstadoPartida = EstadoPartida.PENDIENTE,
    val siguientePartidaId: Long? = null,
    val siguienteLado: Lado? = null,
    val ganadorId: Long? = null,
    val golesA: Int? = null,
    val golesB: Int? = null,
    val finalizadaEn: Long? = null,
)

enum class TipoSorteo { ALEATORIO, MANUAL }

data class Sorteo(
    val id: Long = 0,
    val categoria: ClaveCategoria,
    val tipo: TipoSorteo,
    val semilla: Long,
    val creadoEn: Long,
)

enum class ResultadoIntento { VALIDO, NO_TERMINO, DESCALIFICADO }

data class Intento(
    val id: Long = 0,
    val categoria: ClaveCategoria,
    val equipoId: Long,
    val numero: Int,
    val pista: Int? = null,
    val tiempoMs: Long? = null,
    val resultado: ResultadoIntento,
    val motivo: String? = null,
    val capturadoEn: Long,
    /** Cuántas penalizaciones puso el jurado en este intento. */
    val penalizaciones: Int = 0,
    /** Tiempo que suman esas penalizaciones, calculado al capturar. */
    val penalizacionMs: Long = 0,
) {
    /** Tiempo cronometrado + penalizaciones. Es el que cuenta para el ranking. */
    val tiempoEfectivoMs: Long? get() = tiempoMs?.let { it + penalizacionMs }
}

enum class Rol { PARTICIPANTE, JURADO, ADMIN }

/**
 * Parámetros generales del torneo. Lo propio de cada categoría (pistas, intentos, límites)
 * vive en [Categoria].
 */
data class ConfigTorneo(
    val minIntegrantes: Int = 0,
    val maxIntegrantes: Int = 4,
    val maxNombreRobot: Int = 15,
    /** Tiempo que suma cada penalización. Provisional: confirmar con la escuela. */
    val penalizacionMs: Long = 5_000,
    val maxPenalizaciones: Int = 10,
)

/**
 * Un renglón de la bitácora: quién hizo qué y cuándo. [actor] es hoy el rol de quien tenía la
 * sesión (ADMIN, JURADO o PARTICIPANTE); cuando haya inicio de sesión con Google será su correo.
 */
data class EventoAuditoria(
    val id: Long = 0,
    val actor: String,
    val accion: String,
    val entidad: String,
    val entidadId: Long? = null,
    val detalle: String? = null,
    val creadoEn: Long,
)

/** Nombres de las acciones que se guardan en la bitácora. */
object Acciones {
    const val EQUIPO_REGISTRADO = "EQUIPO_REGISTRADO"
    const val ESTADO_EQUIPO_CAMBIADO = "ESTADO_EQUIPO_CAMBIADO"
    const val LIMITES_ACTUALIZADOS = "LIMITES_ACTUALIZADOS"
    const val ORDEN_SORTEADO = "ORDEN_SORTEADO"
    const val INTENTO_REGISTRADO = "INTENTO_REGISTRADO"
    const val CARRERA_EXTRA_REGISTRADA = "CARRERA_EXTRA_REGISTRADA"
    const val INTENTO_ANULADO = "INTENTO_ANULADO"
    const val CATEGORIA_CERRADA = "CATEGORIA_CERRADA"
    const val CATEGORIA_REABIERTA = "CATEGORIA_REABIERTA"
    const val TERMINOS_PUBLICADOS = "TERMINOS_PUBLICADOS"
    const val LLAVES_GENERADAS = "LLAVES_GENERADAS"
    const val LLAVES_PROGRAMADAS = "LLAVES_PROGRAMADAS"
    const val PARTIDA_INICIADA = "PARTIDA_INICIADA"
    const val PARTIDA_FINALIZADA = "PARTIDA_FINALIZADA"
    const val EQUIPO_ELIMINADO = "EQUIPO_ELIMINADO"
    const val PIN_CAMBIADO = "PIN_CAMBIADO"
}

/** Una versión publicada de los términos y condiciones. Cada cambio es una versión nueva (1, 2, 3...). */
data class Terminos(
    val version: Int,
    /** Un renglón por regla. */
    val reglas: List<String>,
    val publicadoEn: Long,
)

/** Constancia de que un equipo aceptó una versión de los términos al registrarse. */
data class AceptacionTerminos(
    val id: Long = 0,
    val equipoId: Long,
    val version: Int,
    val aceptadoEn: Long,
)

/** Los términos con los que nace el torneo (versión 1). */
object TerminosBase {
    val reglas: List<String> = listOf(
        "Cada equipo tiene un capitán.",
        "Un equipo registra un robot en una sola categoría.",
        "El nombre del robot puede tener máximo 15 caracteres.",
        "No se permiten nombres con palabras ofensivas.",
        "No puede haber dos equipos ni dos robots con el mismo nombre.",
        "Si el peso o el tamaño del robot sale de los límites de la categoría, el registro pasa a revisión técnica.",
        "Fútbol robot y combate se juegan por llaves de eliminación directa.",
        "Sigue líneas, laberinto y robots de velocidad se clasifican por el mejor tiempo.",
        "Las decisiones del jurado son definitivas.",
    )
}

/** Una persona que inició sesión (hoy, con su cuenta de Google). */
data class Usuario(
    val uid: String,
    val correo: String,
    val nombre: String? = null,
)
