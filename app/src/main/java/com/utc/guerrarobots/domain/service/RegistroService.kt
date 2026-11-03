package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.ConfigTorneo
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.ErrorDominio
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Plantel
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Robot
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.CatalogoRepository
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.repository.PartidaRepository
import com.utc.guerrarobots.domain.repository.Transaccion
import com.utc.guerrarobots.domain.service.AuditoriaService
import com.utc.guerrarobots.domain.service.TerminosService
import com.utc.guerrarobots.domain.rules.Texto

/** Registro de equipos y revisión técnica. */
class RegistroService(
    private val equipos: EquipoRepository,
    private val catalogo: CatalogoRepository,
    private val partidas: PartidaRepository,
    private val tx: Transaccion,
    private val config: ConfigTorneo = ConfigTorneo(),
    private val auditoria: AuditoriaService? = null,
    private val terminos: TerminosService? = null,
) {

    /** Lo que captura la pantalla "Datos de registro". */
    data class SolicitudRegistro(
        val nombreEquipo: String,
        val capitan: String,
        val integrantes: List<String>,
        val jefeCarrera: String,
        val plantel: String,
        val nombreRobot: String,
        val categoria: ClaveCategoria,
        val pesoG: Int,
        /** Lado más largo del robot, en centímetros. */
        val tamanoCm: Double,
        val logoUri: String? = null,
        /** Versión de los términos y condiciones que aceptó el participante. */
        val terminosVersion: Int? = null,
    )

    /**
     * Valida todo junto y devuelve todos los errores a la vez para marcar los campos.
     * Si el peso o el tamaño salen del rango de la categoría, el equipo queda en REVISION
     * (no se rechaza); si todo está bien queda PENDIENTE hasta que el admin lo acepte.
     */
    suspend fun registrar(s: SolicitudRegistro): Resultado<Equipo> =
        tx.ejecutar { registrarInterno(s) }

    private suspend fun registrarInterno(s: SolicitudRegistro): Resultado<Equipo> {
        val errores = mutableListOf<ErrorDominio>()
        fun error(campo: String, mensaje: String, codigo: Codigo = Codigo.VALIDACION) {
            errores += ErrorDominio(codigo, mensaje, campo)
        }

        val nombreEquipo = s.nombreEquipo.trim()
        val capitan = s.capitan.trim()
        val jefe = s.jefeCarrera.trim()
        val nombreRobot = s.nombreRobot.trim()
        val integrantes = s.integrantes.map { it.trim() }

        terminos?.revisarAceptacion(s.terminosVersion)?.let { errores += it }

        if (nombreEquipo.isEmpty()) error("nombreEquipo", "Escribe el nombre del equipo")
        if (capitan.isEmpty()) error("capitan", "Escribe el nombre del capitán")
        if (jefe.isEmpty()) error("jefeCarrera", "Escribe el nombre del jefe de carrera")
        if (integrantes.size !in config.minIntegrantes..config.maxIntegrantes || integrantes.any { it.isEmpty() }) {
            error(
                "integrantes",
                "Registra de ${config.minIntegrantes} a ${config.maxIntegrantes} integrantes, todos con nombre",
            )
        }
        if (nombreRobot.isEmpty()) {
            error("nombreRobot", "Escribe el nombre del robot")
        } else if (nombreRobot.length > config.maxNombreRobot) {
            error("nombreRobot", "El nombre del robot puede tener máximo ${config.maxNombreRobot} caracteres")
        }
        if (s.pesoG <= 0) error("pesoG", "El peso debe ser mayor que cero")
        if (s.tamanoCm <= 0.0) error("tamanoCm", "El tamaño debe ser mayor que cero")

        val plantel = catalogo.planteles()
            .firstOrNull { Texto.normalizar(it.nombre) == Texto.normalizar(s.plantel) }
        if (plantel == null) error("plantel", "Elige un plantel de la lista")

        val bloqueadas = catalogo.palabrasBloqueadas()
        if (nombreEquipo.isNotEmpty() && Texto.contieneBloqueada(nombreEquipo, bloqueadas)) {
            error("nombreEquipo", "El nombre del equipo contiene palabras no permitidas")
        }
        if (nombreRobot.isNotEmpty() && Texto.contieneBloqueada(nombreRobot, bloqueadas)) {
            error("nombreRobot", "El nombre del robot contiene palabras no permitidas")
        }

        if (nombreEquipo.isNotEmpty() && equipos.existeNombreEquipo(nombreEquipo)) {
            error("nombreEquipo", "Ya existe un equipo con ese nombre", Codigo.DUPLICADO)
        }
        if (nombreRobot.isNotEmpty() && equipos.existeNombreRobot(nombreRobot)) {
            error("nombreRobot", "Otro equipo ya usa ese nombre de robot", Codigo.DUPLICADO)
        }

        val info = catalogo.categorias().firstOrNull { it.clave == s.categoria }
        if (info == null) {
            error("categoria", "Esa categoría no existe")
        } else if (partidas.listar(s.categoria).isNotEmpty()) {
            error("categoria", "Las llaves de ${info.nombre} ya se generaron: el registro cerró", Codigo.CONFLICTO)
        }

        if (errores.isNotEmpty() || plantel == null) return Resultado.Falla(errores)

        val limites = catalogo.categoria(s.categoria)
        val fueraDeRango =
            (limites.pesoMinG != null && s.pesoG < limites.pesoMinG) ||
                (limites.pesoMaxG != null && s.pesoG > limites.pesoMaxG) ||
                (limites.tamanoMaxCm != null && s.tamanoCm > limites.tamanoMaxCm)

        val nuevo = Equipo(
            nombre = nombreEquipo,
            capitan = capitan,
            plantel = plantel.nombre,
            jefeCarrera = jefe,
            logoUri = s.logoUri,
            estado = if (fueraDeRango) EstadoEquipo.REVISION else EstadoEquipo.PENDIENTE,
            integrantes = integrantes,
            robot = Robot(nombreRobot, s.categoria, s.pesoG, s.tamanoCm),
        )
        val id = equipos.insertar(nuevo)
        equipos.actualizarFolio(id, "GR-" + id.toString().padStart(4, '0'))
        val creado = equipos.obtener(id)!!
        if (s.terminosVersion != null) terminos?.registrarAceptacion(id, s.terminosVersion)
        auditoria?.registrar(
            Acciones.EQUIPO_REGISTRADO, "equipo", id,
            "${creado.folio} ${creado.nombre} · robot ${creado.robot.nombre} · ${limites.nombre}",
        )
        return Resultado.Ok(creado)
    }

    /** Acepta, rechaza o manda a revisión. Un equipo que ya está en las llaves no se puede mover. */
    suspend fun cambiarEstado(equipoId: Long, nuevo: EstadoEquipo): Resultado<Equipo> =
        tx.ejecutar { cambiarEstadoInterno(equipoId, nuevo) }

    private suspend fun cambiarEstadoInterno(equipoId: Long, nuevo: EstadoEquipo): Resultado<Equipo> {
        val equipo = equipos.obtener(equipoId)
            ?: return falla(Codigo.NO_ENCONTRADO, "No existe el equipo", "equipoId")
        if (equipo.estado == nuevo) return Resultado.Ok(equipo)
        if (equipo.estado == EstadoEquipo.ACEPTADO) {
            val enLlaves = partidas.listar(equipo.robot.categoria)
                .any { it.equipoAId == equipoId || it.equipoBId == equipoId }
            if (enLlaves) {
                return falla(Codigo.CONFLICTO, "El equipo ya está en las llaves y no se puede cambiar su estado")
            }
        }
        equipos.actualizarEstado(equipoId, nuevo)
        auditoria?.registrar(
            Acciones.ESTADO_EQUIPO_CAMBIADO, "equipo", equipoId,
            "${equipo.folio} ${equipo.nombre}: ${equipo.estado.name} → ${nuevo.name}",
        )
        return Resultado.Ok(equipos.obtener(equipoId)!!)
    }

    /** Las categorías del torneo, en su orden. */
    suspend fun categorias(): List<Categoria> = catalogo.categorias()

    suspend fun listar(estado: EstadoEquipo? = null, categoria: ClaveCategoria? = null): List<Equipo> =
        equipos.listar(estado, categoria)

    suspend fun guardarLogo(equipoId: Long, logoUri: String?): Resultado<Equipo> {
        if (equipos.obtener(equipoId) == null) {
            return falla(Codigo.NO_ENCONTRADO, "No existe el equipo", "equipoId")
        }
        equipos.actualizarLogo(equipoId, logoUri)
        return Resultado.Ok(equipos.obtener(equipoId)!!)
    }

    /**
     * Cambia los límites técnicos de una categoría. No se revisan de nuevo los equipos que
     * ya se registraron: el admin puede mandarlos a revisión a mano.
     */
    suspend fun actualizarLimites(
        clave: ClaveCategoria,
        pesoMinG: Int?,
        pesoMaxG: Int?,
        tamanoMaxCm: Double?,
    ): Resultado<Categoria> {
        val errores = mutableListOf<ErrorDominio>()
        if (pesoMinG != null && pesoMinG <= 0) errores += ErrorDominio(Codigo.VALIDACION, "El peso mínimo debe ser mayor que cero", "pesoMinG")
        if (pesoMaxG != null && pesoMaxG <= 0) errores += ErrorDominio(Codigo.VALIDACION, "El peso máximo debe ser mayor que cero", "pesoMaxG")
        if (pesoMinG != null && pesoMaxG != null && pesoMinG > pesoMaxG) {
            errores += ErrorDominio(Codigo.VALIDACION, "El peso mínimo no puede ser mayor que el máximo", "pesoMinG")
        }
        if (tamanoMaxCm != null && tamanoMaxCm <= 0.0) {
            errores += ErrorDominio(Codigo.VALIDACION, "El tamaño máximo debe ser mayor que cero", "tamanoMaxCm")
        }
        if (errores.isNotEmpty()) return Resultado.Falla(errores)

        val actualizada = catalogo.categoria(clave).copy(
            pesoMinG = pesoMinG,
            pesoMaxG = pesoMaxG,
            tamanoMaxCm = tamanoMaxCm,
        )
        catalogo.guardarCategoria(actualizada)
        auditoria?.registrar(
            Acciones.LIMITES_ACTUALIZADOS, "categoria", null,
            "${actualizada.nombre} · peso ${pesoMinG ?: "-"} a ${pesoMaxG ?: "-"} g · tamaño máx. ${tamanoMaxCm ?: "-"} cm",
        )
        return Resultado.Ok(actualizada)
    }

    suspend fun agregarPlantel(nombre: String): Resultado<Plantel> {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return falla(Codigo.VALIDACION, "Escribe el nombre del plantel", "nombre")
        if (catalogo.planteles().any { Texto.normalizar(it.nombre) == Texto.normalizar(limpio) }) {
            return falla(Codigo.DUPLICADO, "Ese plantel ya existe", "nombre")
        }
        return Resultado.Ok(catalogo.agregarPlantel(limpio))
    }

    /** Agrega una palabra al filtro de nombres. Solo letras, sin espacios. */
    suspend fun agregarPalabraBloqueada(palabra: String): Resultado<String> {
        val normalizada = Texto.normalizar(palabra)
        if (!normalizada.matches(Regex("[a-z]{2,}"))) {
            return falla(Codigo.VALIDACION, "Escribe una sola palabra, solo con letras", "palabra")
        }
        catalogo.agregarPalabraBloqueada(normalizada)
        return Resultado.Ok(normalizada)
    }
}
