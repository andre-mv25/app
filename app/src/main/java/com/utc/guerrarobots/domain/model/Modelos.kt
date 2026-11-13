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
    /** Máximo de participantes por equipo, contando al capitán (reglamento: 2; fútbol robot: 4). */
    val maxParticipantes: Int = 2,
    /** Posición al mostrar la lista. */
    val orden: Int = 0,
)

/** Las categorías con las que nace el torneo. Después se pueden cambiar o agregar en la base. */
object CategoriasBase {
    val porDefecto: List<Categoria> = listOf(
        Categoria(ClaveCategoria.SIGUE_LINEAS, "Sigue líneas", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 2, orden = 1),
        Categoria(ClaveCategoria.FUTBOL, "Fútbol robot", Metodo.LLAVES, canchas = 4, maxParticipantes = 4, orden = 2),
        Categoria(ClaveCategoria.LABERINTO, "Laberinto", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 1, orden = 3),
        Categoria(ClaveCategoria.COMBATE, "Combate", Metodo.LLAVES, canchas = 2, orden = 4),
        Categoria(ClaveCategoria.VELOCIDAD, "Robots de velocidad", Metodo.RANKING_TIEMPO, intentosMaximos = 2, canchas = 1, orden = 5),
    )

    /** La categoría base con esa clave; si no existe, una genérica por tiempo. */
    fun de(clave: ClaveCategoria): Categoria = porDefecto.firstOrNull { it.clave == clave } ?: Categoria(clave)
}

/** Los planteles de la Universidad Tres Culturas que participan. */
object PlantelesBase {
    val todos: List<String> = listOf(
        "Aragón", "Atizapán", "Chalco", "Coacalco", "Cuautitlán", "Ecatepec",
        "Huehuetoca", "Ixtapaluca", "Iztapalapa", "Los Reyes", "Neza",
        "Observatorio", "Puebla", "Tlalpan", "Tlalnepantla", "Toluca",
        "Toreo", "Zona Rosa",
    )
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
    val asistencia: Boolean = false,
    val horaLlegada: Long? = null,
    val adminRegistro: String? = null,
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

enum class Rol { PARTICIPANTE, JURADO, ADMIN, SUPER_ADMIN }

/** Perfil individual de los administradores y jurados autorizados del torneo. */
data class AdminPerfil(
    val id: String,
    val nombre: String,
    val pin: String, // PIN de 7 dígitos individual (Admin / Jurado)
    val pinSuperAdmin: String? = null, // PIN de 7 dígitos exclusivo para Super Admin
) {
    val esSuperAdmin: Boolean get() = pinSuperAdmin != null
}

data class JuradoPerfil(
    val id: String,
    val nombre: String,
    val pin: String, // PIN de 7 dígitos individual
)

object AdminsLista {
    val todos = listOf(
        AdminPerfil("jose_guadalupe", "Jose Guadalupe", "1234567", pinSuperAdmin = "7777777"),
        AdminPerfil("delia_azucena", "Delia Azucena Lienzo", "7654321"),
        AdminPerfil("andre", "Andre", "1234568"),
        AdminPerfil("alan", "Alan", "2345678"),
        AdminPerfil("yosiamar", "Yosiamar", "3456789"),
        AdminPerfil("osvaldo", "Osvaldo", "4567890"),
        AdminPerfil("luis_angel", "Luis Angel", "5678901"),
        AdminPerfil("sevastian", "Sevastian", "6789012"),
        AdminPerfil("felipe", "Felipe", "7890123"),
        AdminPerfil("monse", "Monse", "8901234"),
        AdminPerfil("leo", "Leo", "9012345"),
        AdminPerfil("diego", "Diego", "1122334"),
    )

    fun buscarPorPin(pin: String): AdminPerfil? =
        todos.firstOrNull { it.pin == pin.trim() }

    fun buscarPorPinSuperAdmin(pin: String): AdminPerfil? =
        todos.firstOrNull { it.pinSuperAdmin != null && it.pinSuperAdmin == pin.trim() }

    fun buscarPorNombre(nombre: String): AdminPerfil? =
        todos.firstOrNull { it.nombre.equals(nombre.trim(), ignoreCase = true) }
}

object JuradosLista {
    val todos = listOf(
        JuradoPerfil("francisco_javier", "Francisco Javier", "2100001"),
        JuradoPerfil("ignacio_mendoza", "Ignacio Mendoza", "2100002"),
        JuradoPerfil("gonzalo_ivan", "Gonzalo Iván Riego", "2100003"),
        JuradoPerfil("maria_teresa", "María Teresa", "2100004"),
        JuradoPerfil("rafael_ramirez", "Rafael Ramírez", "2100005"),
        JuradoPerfil("karen_giselle", "Karen Giselle", "2100006"),
        JuradoPerfil("samantha_mendoza", "Samantha Mendoza", "2100007"),
        JuradoPerfil("cecilia_cabrioto", "Cecilia Alejandra Cabrioto Flores", "2100008"),
        JuradoPerfil("alexis_javier", "Alexis Javier", "2100009"),
        JuradoPerfil("adriana_perez", "Adriana Guadalupe Pérez", "2100010"),
        JuradoPerfil("victor_oliva", "Víctor Manuel Oliva Sandoval", "2100011"),
        JuradoPerfil("stefany_granados", "Stefany Granados Ñañez", "2100012"),
        JuradoPerfil("rodolfo_marin", "Rodolfo Ángel Marín", "2100013"),
        JuradoPerfil("tania_vazquez", "Tania Belén Vázquez", "2100014"),
        JuradoPerfil("sandra_merida", "Sandra Aurora Mérida", "2100015"),
        JuradoPerfil("julieta_gonzalez", "Julieta González Bravo", "2100016"),
        JuradoPerfil("arizbeth_sanchez", "Arizbeth Sánchez", "2100017"),
        JuradoPerfil("heriberto_sanchez", "Heriberto Arturo Sánchez", "2100018"),
        JuradoPerfil("delfos_vazquez", "Delfos Bolívar Vázquez", "2100019"),
        JuradoPerfil("jose_jesus", "José de Jesús Hernández García", "2100020"),
        JuradoPerfil("alejandra_vega", "Alejandra Victoria Vega García", "2100021"),
    )

    fun buscarPorPin(pin: String): JuradoPerfil? =
        todos.firstOrNull { it.pin == pin.trim() }

    fun buscarPorNombre(nombre: String): JuradoPerfil? =
        todos.firstOrNull { it.nombre.equals(nombre.trim(), ignoreCase = true) }
}

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
    val categoria: String? = null,
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
    const val SHEETS_SINCRONIZADO = "SHEETS_SINCRONIZADO"
    const val ACTUALIZACION_PUBLICADA = "ACTUALIZACION_PUBLICADA"
}

/** Configuración de la última versión de la aplicación publicada en Firebase. */
data class InfoActualizacion(
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val urlDescarga: String = "",
    val obligatoria: Boolean = false,
    val novedades: String = "",
)

/** Una versión publicada de los términos y condiciones. Cada cambio es una versión nueva (1, 2, 3...). */
data class Terminos(
    val version: Int,
    /** Un renglón por regla. */
    val reglas: List<String>,
    val publicadoEn: Long,
    /** Sanciones que se aplican en la categoría. Vacío en las versiones viejas. */
    val sanciones: List<String> = emptyList(),
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
    const val GENERAL = "GENERAL"

    /** Reglas de todas las categorías. */
    val comunes: List<String> = listOf(
        "Pueden participar estudiantes de Ingeniería en Sistemas Computacionales de la Universidad Tres Culturas, de cualquier plantel. Los integrantes diseñan, construyen y programan su robot.",
        "El equipo debe tener un capitán y los integrantes que marca la categoría, todos del mismo plantel.",
        "El nombre del robot puede tener máximo 15 caracteres, sin palabras ofensivas, y no puede repetirse con el de otro equipo o robot.",
        "Los datos del registro (nombre del equipo y del robot, integrantes, plantel, peso y tamaño) deben ser verdaderos.",
        "El registro es del 3 al 30 de noviembre. El día del evento (14 de diciembre) solo se registran equipos a consideración de los jueces y siempre que cumplan los requisitos de la categoría.",
        "El día del evento se revisan las características técnicas del robot. Si no cumple los requisitos de la categoría, el equipo queda descalificado.",
        "El robot lleva pegada la etiqueta con su número de registro. No se permite suplantarla ni modificarla.",
        "El robot puede llevar el nombre del equipo y del plantel en un lugar visible. El uso de publicidad es libre.",
        "El robot debe tener un interruptor de encendido visible y accesible para los jueces y el público.",
        "Si el robot tiene varias funciones, solo usa la de su categoría y muestra al juez cómo la activa.",
        "El orden de participación se avisa minutos antes de empezar. Cada equipo debe estar atento a las indicaciones; no presentarse a tiempo puede causar la descalificación.",
        "Se expulsa del evento a quien insulte o agreda a la organización o a otros competidores, o dañe a propósito la pista, el recinto o los robots rivales.",
        "Los jueces aplican el reglamento. Sus decisiones son definitivas e inapelables y pueden expulsar, penalizar o descalificar en casos no previstos. La organización puede modificar las reglas si lo considera necesario.",
        "Participar implica aceptar por completo el reglamento oficial del torneo.",
    )

    private val tiemposAutonomo: List<String> = listOf(
        "El robot debe ser autónomo: sin enlaces alámbricos o inalámbricos ni control remoto. Debe tener dos o más ruedas y funcionar con baterías (pueden ser recargables).",
        "No hay límite de sensores ni de microcontroladores.",
        "El robot debe superar imperfecciones de la pista de hasta 1 mm de alto y de separación.",
    )

    private val seguidorYVelocista: List<String> = listOf(
        "Dimensiones máximas: 30 cm de largo por 30 cm de ancho, con todos los accesorios extendidos, y 20 cm de alto. El peso es libre.",
    ) + tiemposAutonomo + listOf(
        "Se puede probar en la pista hasta 30 minutos antes de la competencia.",
        "Cuando el robot es llamado debe estar listo; si no lo está, queda eliminado con una tolerancia de 30 segundos.",
        "Los robots salen al mismo tiempo con el semáforo de salida. El turno termina cuando uno de los tres robots en pista cruza la meta.",
        "El tiempo máximo para completar la trayectoria es de 2 minutos; quien no la complete queda descalificado.",
        "Gana el robot con el menor tiempo y pasa a la siguiente ronda. Si nadie termina, gana el que quedó más cerca de la meta. Entre rondas hay tiempo para ajustar y reprogramar.",
        "El tiempo lo mide un sistema electrónico o un juez con cronómetro, y el tiempo registrado es definitivo.",
        "El robot debe funcionar con iluminación variable; no se pueden pedir condiciones especiales de luz.",
        "El robot no puede tomar atajos durante el recorrido.",
        "Es motivo de descalificación: manipular el robot una vez iniciado el partido, dañar la pista a propósito, usar dispositivos que puedan dañar a las personas, no ser autónomo después de cruzar la línea de inicio y salirse del área de competencia.",
    )

    private val sigueLineas: List<String> = seguidorYVelocista + listOf(
        "Si una persona ajena empuja o interrumpe al robot en su recorrido, el robot queda descalificado.",
    )

    private val velocidad: List<String> = seguidorYVelocista + listOf(
        "Si una persona ajena empuja o interrumpe al robot en su recorrido, será sancionada; si es del mismo plantel del robot, el plantel queda descalificado de la categoría.",
    )

    private val futbol: List<String> = listOf(
        "El equipo puede ser de hasta 4 integrantes del mismo plantel.",
        "Dimensiones máximas: 15 cm de largo, 15 cm de ancho y 15 cm de alto, con todos los accesorios extendidos. Peso máximo: 500 g.",
        "El robot es de control remoto (radiofrecuencia, Bluetooth o Wi-Fi), con dos o más ruedas y baterías (pueden ser recargables). Debe superar las imperfecciones de la cancha.",
        "Debe tener una defensa delantera en forma de semicírculo o trapecio que sobresalga máximo 5 cm del frente, para manejar el balón.",
        "Cada robot tiene un rol: atacante o portero. Los porteros inician dentro de la portería y los atacantes a los lados del círculo central.",
        "Al activar los robots hay 5 segundos de seguridad en los que permanecen detenidos y los responsables salen del área de juego.",
        "Cada partido dura máximo 10 minutos y empieza solo cuando el juez principal lo indica. Gana quien anote primero dos goles o quien tenga más goles al terminar el tiempo.",
        "Si hay empate, el partido continúa hasta que un robot anote el primer gol. Después de cada gol los robots vuelven a su posición y el balón al centro.",
        "Hay fase eliminatoria, semifinal y final.",
        "El capitán puede pedir una pausa de reparación de máximo 3 minutos, hasta 2 veces por partido. Si el capitán pide detener la competencia, es derrota automática.",
        "Si el equipo no se presenta 2 minutos después de ser llamado, pierde el partido.",
        "Si el robot se desarma en más del 60% de sus partes durante el partido, el equipo queda descalificado.",
        "Los jueces son jefes de carrera de planteles que no participan. La última decisión es del juez de cancha y es irrevocable.",
        "Es motivo de descalificación: dañar a propósito al robot rival o el área de juego y usar dispositivos que puedan dañar a las personas.",
    )

    private val laberinto: List<String> = listOf(
        "Dimensiones máximas: 30 cm de largo por 30 cm de ancho, con todos los accesorios extendidos, y 20 cm de alto. El peso es libre.",
        "El robot debe ser autónomo: sin enlaces alámbricos o inalámbricos ni control remoto. Debe tener dos o más ruedas y funcionar con baterías (pueden ser recargables).",
        "No hay límite de sensores ni de microcontroladores. El robot debe superar imperfecciones de la pista de hasta 1 mm.",
        "La competencia es a ronda única. Cada robot tiene 3 minutos para ir de la entrada a la salida del laberinto.",
        "Si el robot no empieza a avanzar 5 segundos después del banderazo, queda descalificado.",
        "Si el robot pierde partes por chocar con las paredes, o queda atrapado sin poder continuar por sí mismo, queda descalificado de inmediato.",
        "Gana el robot que salga en el menor tiempo. Si nadie sale, gana el que llegue más lejos sin quedar atrapado.",
        "El juez cronometra cada recorrido y los resultados se registran en tarjetas visibles para todos.",
        "Los representantes del equipo deben estar en la entrada del laberinto durante la competencia, salvo si su robot ya fue descalificado o terminó su tiempo; si no, el equipo queda descalificado.",
        "Si el equipo no se presenta 2 minutos después de ser llamado, queda descalificado. Si hay indisciplina que impida la competencia, el equipo queda descalificado.",
        "Es motivo de descalificación: manipular el robot una vez iniciada la competencia, dañar el área de juego a propósito y usar dispositivos que puedan dañar a las personas.",
        "La decisión del juez del laberinto es irrevocable.",
    )

    private val combate: List<String> = listOf(
        "Esta categoría tiene reglas de seguridad más estrictas porque hay riesgo para las personas y para los robots.",
        "Dimensiones máximas: 60 cm de largo por 60 cm de ancho, con todos los accesorios extendidos. La altura y el peso son libres.",
        "El robot es de control remoto (radiofrecuencia, Bluetooth o Wi-Fi), manejado por uno o dos pilotos, con dos o más ruedas y baterías (pueden ser recargables). Debe superar las imperfecciones del piso.",
        "Está prohibido que el robot arroje proyectiles o lance llamas.",
        "Está estrictamente prohibido usar materiales peligrosos: sustancias inflamables, corrosivas o elementos cortantes sin control. Es motivo de descalificación.",
        "Se permiten armas de contacto (mazos, defensas, sierras circulares, inmovilizadores eléctricos, brazos tipo grúa, etc.) siempre que cumplan las normas de seguridad y no pongan en riesgo a las personas ni a otros robots. Se recomienda una coraza resistente.",
        "Cada combate es de un solo round (muerte súbita) y 1 contra 1. Gana quien deje incapacitado al rival y pasa a la siguiente ronda.",
        "Si no hay un ganador claro, hay un round de desempate: el juez decide según el número de impactos directos, siempre que no haya daño catastrófico, inmovilización o ventaja evidente.",
        "Los pilotos son los del registro oficial y no pueden cambiar durante la competencia, salvo fuerza mayor justificada.",
        "El equipo tiene un capitán, que puede o no ser piloto. Solo el capitán puede pedir tiempo, retirarse de la competencia o presentar reclamaciones.",
        "Los integrantes que no son pilotos ni capitán son apoyo técnico y son los únicos que pueden reparar o ajustar el robot.",
        "Se permiten hasta dos pausas tácticas que suman 30 segundos. Mientras están activas no se puede tocar el robot; hacerlo es motivo de descalificación.",
        "En el combate de clasificación no se puede cambiar la programación del robot. Después de cada asalto se pueden hacer reparaciones, cambiar piezas y reprogramar.",
        "Cuando el juez da la indicación, los responsables salen del coliseo y no pueden volver a entrar, salvo que su robot sea descalificado o termine su tiempo. Quedarse dentro es descalificación directa.",
        "Si el equipo no se presenta 2 minutos después de ser llamado, pierde el combate por default.",
        "Es motivo de descalificación: usar dispositivos que puedan dañar a las personas, dañar la arena a propósito, la indisciplina y no presentarse a tiempo.",
        "La última decisión es del juez de coliseo y es irrevocable.",
    )

    // ---- Sanciones (salen del reglamento oficial; las que dependen de la categoría van aparte) ----

    private val expulsionComun: List<String> = listOf(
        "Expulsión del evento: insultar o agredir a miembros de la organización o a otros competidores.",
        "Expulsión del evento: provocar daños de manera intencionada al área de competencia o al recinto.",
    )

    private const val EXPULSION_DANO_ROBOT =
        "Expulsión del evento: causar desperfectos de manera intencionada y deliberada a los robots oponentes."

    private val descalificacionComun: List<String> = listOf(
        "Descalificación: usar dispositivos que puedan causar daños físicos a las personas.",
        "Descalificación: provocar desperfectos intencionados al área de juego.",
        "Descalificación: manipular el robot de forma externa, por cualquier medio, una vez iniciada la competencia.",
    )

    /** Sanciones que valen en todas las categorías. */
    val sancionesGenerales: List<String> = listOf(
        "Si en la revisión técnica del día del evento el robot no cumple los requisitos de su categoría, el equipo queda descalificado.",
        "No presentarse a tiempo cuando se llama al equipo puede causar la descalificación.",
        "Los jueces pueden expulsar, penalizar o descalificar en los casos no previstos. Sus decisiones son definitivas e inapelables.",
        "Los jefes de carrera y docentes vigilan el comportamiento de sus participantes durante toda la competencia.",
    )

    private val sancionesTiempoComun: List<String> = listOf(
        "Descalificación: que el robot no sea plenamente autónomo después de cruzar la línea de inicio.",
        "Descalificación: que el robot abandone el área de competencia.",
        "Descalificación: no completar la trayectoria en el tiempo máximo de 2 minutos.",
        "Eliminación: no estar listo cuando el robot es llamado (hay una tolerancia de 30 segundos).",
    )

    private val sancionesSigueLineas: List<String> =
        expulsionComun + EXPULSION_DANO_ROBOT + descalificacionComun + sancionesTiempoComun + listOf(
            "Descalificación: que una persona ajena empuje o interrumpa al robot durante su recorrido.",
        )

    private val sancionesVelocidad: List<String> =
        expulsionComun + EXPULSION_DANO_ROBOT + descalificacionComun + sancionesTiempoComun + listOf(
            "Sanción: si una persona ajena empuja o interrumpe al robot, será sancionada; si es del mismo plantel del robot, el plantel queda descalificado de la categoría.",
        )

    private val sancionesFutbol: List<String> =
        expulsionComun + EXPULSION_DANO_ROBOT + descalificacionComun + listOf(
            "Descalificación: causar desperfectos de manera intencionada a un robot oponente.",
            "Descalificación: que el robot se desarme en más del 60% de sus partes durante el partido.",
            "Pérdida del partido: no presentarse 2 minutos después de ser llamado.",
            "Derrota automática: que el capitán solicite detener la competencia.",
        )

    private val sancionesLaberinto: List<String> =
        expulsionComun + descalificacionComun + listOf(
            "Descalificación: no estar en la entrada del laberinto durante la competencia (salvo que el robot ya haya sido descalificado o terminado).",
            "Descalificación: no encontrar la salida del laberinto en el tiempo establecido.",
            "Descalificación: que el robot no empiece a avanzar 5 segundos después del banderazo de salida.",
            "Descalificación inmediata: que el robot pierda partes por chocar con las paredes o quede atrapado sin poder continuar por sí mismo.",
            "Descalificación directa: indisciplina que impida el desarrollo normal de la competencia.",
            "Descalificación: no presentarse 2 minutos después de ser llamado.",
        )

    private val sancionesCombate: List<String> =
        expulsionComun + descalificacionComun + listOf(
            "Descalificación: usar materiales peligrosos (sustancias inflamables, materiales corrosivos o elementos cortantes sin control).",
            "Descalificación: tocar el robot mientras está activa una pausa táctica.",
            "Descalificación directa: quedarse en el área del coliseo cuando el juez indicó salir.",
            "Descalificación directa: indisciplina que impida el desarrollo normal de la competencia.",
            "Pérdida del combate por default: no presentarse 2 minutos después de ser llamado.",
        )

    /** Las sanciones de cada categoría, más las generales. */
    val sancionesPorCategoria: Map<ClaveCategoria, List<String>> = mapOf(
        ClaveCategoria.SIGUE_LINEAS to sancionesSigueLineas + sancionesGenerales,
        ClaveCategoria.FUTBOL to sancionesFutbol + sancionesGenerales,
        ClaveCategoria.LABERINTO to sancionesLaberinto + sancionesGenerales,
        ClaveCategoria.COMBATE to sancionesCombate + sancionesGenerales,
        ClaveCategoria.VELOCIDAD to sancionesVelocidad + sancionesGenerales,
    )

    /** Aviso de privacidad que se muestra antes de finalizar el registro. */
    val avisoPrivacidad: List<String> = listOf(
        "Los datos del registro (nombre del equipo y del robot, capitán, participantes, jefe de carrera, plantel, peso, tamaño y foto del robot) se usan solo para organizar el torneo: revisión técnica, llaves, tiempos y premiación.",
        "La foto del robot se usa para la revisión técnica y solo la ve el comité organizador; no se publica.",
        "Al finalizar el registro recibirás un código QR con tu folio y los datos de tu registro: muéstralo solo a quien lo necesite, por ejemplo al comité en el evento.",
        "En las pantallas públicas de la app solo se muestran el nombre del equipo, el robot y el plantel. Los nombres de las personas solo los ve el comité organizador.",
        "Al aceptar confirmas que los datos son verdaderos y que conoces el reglamento del torneo.",
    )

    /** Los términos de cada categoría: las reglas comunes más las suyas. */
    val porCategoria: Map<ClaveCategoria, List<String>> = mapOf(
        ClaveCategoria.SIGUE_LINEAS to comunes + sigueLineas,
        ClaveCategoria.FUTBOL to comunes + futbol,
        ClaveCategoria.LABERINTO to comunes + laberinto,
        ClaveCategoria.COMBATE to comunes + combate,
        ClaveCategoria.VELOCIDAD to comunes + velocidad,
    )

    /** Términos de respaldo para una categoría nueva que todavía no tenga los suyos. */
    val reglas: List<String> = comunes
}

/** Una persona que inició sesión (hoy, con su cuenta de Google). */
data class Usuario(
    val uid: String,
    val correo: String,
    val nombre: String? = null,
)
