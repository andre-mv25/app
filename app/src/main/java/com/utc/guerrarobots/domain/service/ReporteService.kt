package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.repository.Actor
import com.utc.guerrarobots.domain.repository.AuditoriaRepository
import com.utc.guerrarobots.domain.repository.CatalogoRepository
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.repository.IntentoRepository
import com.utc.guerrarobots.domain.repository.PartidaRepository
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.rules.Fecha
import com.utc.guerrarobots.domain.rules.Peso
import com.utc.guerrarobots.domain.rules.Tiempo

/** Una pestaña de la hoja de cálculo: su título y sus filas (las 3 primeras: resumen, vacía y encabezado). */
data class Hoja(val titulo: String, val filas: List<List<String>>)

/** Todo lo que se manda a la hoja de cálculo de una categoría. */
data class ReporteCategoria(val categoria: Categoria, val hojas: List<Hoja>)

/**
 * Arma, para una categoría, las tablas que se sincronizan con Google Sheets: equipos, resultados
 * (tiempos o llaves) y bitácora, cada fila con fecha/hora y el usuario que la capturó. Solo lee datos;
 * escribirlos en la hoja es trabajo de la capa de datos.
 */
class ReporteService(
    private val equipos: EquipoRepository,
    private val catalogo: CatalogoRepository,
    private val intentos: IntentoRepository,
    private val partidas: PartidaRepository,
    private val bitacora: AuditoriaRepository,
    private val tiempos: TiemposService,
    private val llaves: LlavesService,
    private val reloj: Reloj,
    private val limiteBitacora: Int = 5000,
    private val quien: Actor,
) {
    suspend fun reporte(clave: ClaveCategoria): ReporteCategoria {
        val info = catalogo.categoria(clave)
        val deLaCategoria = equipos.listar(categoria = clave).sortedBy { it.id }
        val porId = deLaCategoria.associateBy { it.id }
        val eventos = bitacora.listar(limiteBitacora).sortedBy { it.creadoEn }
        val intentosCat = intentos.listar(clave)
        val partidasCat = partidas.listar(clave)

        val pertenecen = eventos.filter {
            perteneceA(it, clave, info.nombre, porId.keys, intentosCat.map { i -> i.id }.toSet(), partidasCat.map { p -> p.id }.toSet())
        }
        val quienCapturo = pertenecen.filter { it.entidadId != null }
            .groupBy { it.entidad to it.entidadId }

        val resumen = listOf(
            "Sincronizado el", Fecha.formatear(reloj.ahora()), "por", quien.actual(), "Competencia", info.nombre,
        )
        val hojas = mutableListOf<Hoja>()
        hojas += hoja(info, "Equipos", resumen, ENCABEZADO_EQUIPOS, deLaCategoria.map { filaEquipo(it, quienCapturo) })
        if (info.metodo == Metodo.LLAVES) {
            hojas += hoja(info, "Partidas", resumen, ENCABEZADO_PARTIDAS, filasPartidas(clave, quienCapturo))
        } else {
            hojas += hoja(info, "Resultados", resumen, ENCABEZADO_RESULTADOS, filasResultados(clave, info))
            hojas += hoja(info, "Intentos", resumen, ENCABEZADO_INTENTOS, filasIntentos(intentosCat, porId, info, quienCapturo))
        }
        hojas += hoja(info, "Bitácora", resumen, ENCABEZADO_BITACORA, pertenecen.map {
            listOf(Fecha.formatear(it.creadoEn), Etiquetas.actor(it.actor), Etiquetas.accion(it.accion), it.detalle.orEmpty())
        })
        return ReporteCategoria(info, hojas)
    }

    /** Un reporte por cada categoría (para el admin). */
    suspend fun reporteTodas(): List<ReporteCategoria> = catalogo.categorias().map { reporte(it.clave) }

    private fun hoja(info: Categoria, nombre: String, resumen: List<String>, encabezado: List<String>, filas: List<List<String>>) =
        Hoja(titulo(info, nombre), listOf(resumen, emptyList(), encabezado) + filas)

    /** "Sigue líneas - Equipos". Google no acepta : \ / ? * [ ] en el nombre de una pestaña y limita a 100 caracteres. */
    private fun titulo(info: Categoria, nombre: String): String =
        "${info.nombre} - $nombre".replace(Regex("[:\\\\/?*\\[\\]]"), " ").take(100)

    private fun perteneceA(
        e: EventoAuditoria, clave: ClaveCategoria, nombre: String,
        equiposIds: Set<Long>, intentosIds: Set<Long>, partidasIds: Set<Long>,
    ): Boolean {
        // Los eventos nuevos traen su categoría; los viejos se deducen de a qué pertenece lo que cambiaron.
        e.categoria?.let { return it == clave.valor }
        return when (e.entidad) {
            "equipo" -> e.entidadId in equiposIds
            "intento" -> e.entidadId in intentosIds
            "partida" -> e.entidadId in partidasIds
            "categoria" -> e.detalle?.startsWith(nombre) == true
            else -> false
        }
    }

    private fun filaEquipo(e: Equipo, eventos: Map<Pair<String, Long?>, List<EventoAuditoria>>): List<String> {
        val alta = eventos["equipo" to e.id].orEmpty().firstOrNull { it.accion == Acciones.EQUIPO_REGISTRADO }
        return listOf(
            e.folio, e.nombre, e.robot.nombre, e.plantel, etiquetaEstado(e.estado),
            Peso.formatearKg(e.robot.pesoG), e.robot.tamanoCm.toString(),
            e.capitan, e.integrantes.joinToString(", "), e.jefeCarrera,
            alta?.let { Fecha.formatear(it.creadoEn) }.orEmpty(), alta?.let { Etiquetas.actor(it.actor) }.orEmpty(),
        )
    }

    private suspend fun filasResultados(clave: ClaveCategoria, info: Categoria): List<List<String>> {
        val estado = if (info.cerrada) "Cerrada" else "Abierta"
        return tiempos.posiciones(clave).map { p ->
            listOf(
                if (p.fila.sinTiempo) "—" else p.fila.lugar.toString(),
                p.equipo.folio, p.equipo.nombre, p.equipo.robot.nombre, p.equipo.plantel,
                p.fila.mejorMs?.let { Tiempo.formatear(it) } ?: "Sin tiempo válido",
                p.fila.segundoMs?.let { Tiempo.formatear(it) }.orEmpty(),
                if (p.fila.empate) "Sí" else "No",
                estado,
            )
        }
    }

    private fun filasIntentos(
        lista: List<Intento>, porId: Map<Long, Equipo>, info: Categoria,
        eventos: Map<Pair<String, Long?>, List<EventoAuditoria>>,
    ): List<List<String>> = lista.sortedBy { it.capturadoEn }.map { i ->
        val equipo = porId[i.equipoId]
        val captura = eventos["intento" to i.id].orEmpty().firstOrNull {
            it.accion == Acciones.INTENTO_REGISTRADO || it.accion == Acciones.CARRERA_EXTRA_REGISTRADA
        }
        listOf(
            Fecha.formatear(i.capturadoEn),
            equipo?.folio.orEmpty(), equipo?.nombre ?: "equipo ${i.equipoId}", equipo?.robot?.nombre.orEmpty(),
            if (i.numero > info.intentosMaximos) "Carrera extra" else "Intento ${i.numero}",
            i.pista?.toString().orEmpty(),
            when (i.resultado) {
                ResultadoIntento.VALIDO -> "Válido"
                ResultadoIntento.NO_TERMINO -> "No terminó"
                ResultadoIntento.DESCALIFICADO -> "Descalificado"
            },
            i.tiempoMs?.let { Tiempo.formatear(it) }.orEmpty(),
            i.penalizaciones.toString(),
            i.tiempoEfectivoMs?.let { Tiempo.formatear(it) }.orEmpty(),
            i.motivo.orEmpty(),
            captura?.let { Etiquetas.actor(it.actor) }.orEmpty(),
        )
    }

    private suspend fun filasPartidas(
        clave: ClaveCategoria, eventos: Map<Pair<String, Long?>, List<EventoAuditoria>>,
    ): List<List<String>> = llaves.cuadro(clave).flatMap { ronda ->
        ronda.partidas.map { pv ->
            val p = pv.partida
            val cierre = eventos["partida" to p.id].orEmpty().lastOrNull { it.accion == Acciones.PARTIDA_FINALIZADA }
            listOf(
                ronda.nombre, p.posicion.toString(), p.cancha?.toString().orEmpty(), p.turno?.toString().orEmpty(),
                pv.equipoA?.nombre.orEmpty(),
                if (p.esBye) "pasa directo" else pv.equipoB?.nombre.orEmpty(),
                when (p.estado) {
                    EstadoPartida.PENDIENTE -> "Esperando rival"
                    EstadoPartida.PROGRAMADA -> "Por jugar"
                    EstadoPartida.EN_CURSO -> "En juego"
                    EstadoPartida.FINALIZADA -> "Terminada"
                },
                if (p.golesA != null && p.golesB != null) "${p.golesA} - ${p.golesB}" else "",
                pv.ganador?.nombre.orEmpty(),
                p.finalizadaEn?.let { Fecha.formatear(it) }.orEmpty(),
                cierre?.let { Etiquetas.actor(it.actor) }.orEmpty(),
            )
        }
    }

    private fun etiquetaEstado(e: EstadoEquipo): String = when (e) {
        EstadoEquipo.PENDIENTE -> "Pendiente"
        EstadoEquipo.REVISION -> "En revisión"
        EstadoEquipo.ACEPTADO -> "Aceptado"
        EstadoEquipo.RECHAZADO -> "Rechazado"
    }

    private companion object {
        val ENCABEZADO_EQUIPOS = listOf(
            "Folio", "Equipo", "Robot", "Plantel", "Estado", "Peso", "Tamaño (cm)",
            "Capitán", "Integrantes", "Jefe de carrera", "Registrado el", "Registrado por",
        )
        val ENCABEZADO_RESULTADOS = listOf(
            "Lugar", "Folio", "Equipo", "Robot", "Plantel", "Mejor tiempo", "Segundo mejor", "Empate", "Categoría",
        )
        val ENCABEZADO_INTENTOS = listOf(
            "Fecha y hora", "Folio", "Equipo", "Robot", "Intento", "Pista", "Resultado",
            "Tiempo", "Penalizaciones", "Tiempo efectivo", "Motivo", "Capturado por",
        )
        val ENCABEZADO_PARTIDAS = listOf(
            "Ronda", "Partida", "Cancha", "Turno", "Equipo A", "Equipo B", "Estado",
            "Marcador", "Ganador", "Finalizada el", "Resultado capturado por",
        )
        val ENCABEZADO_BITACORA = listOf("Fecha y hora", "Usuario", "Acción", "Detalle")
    }
}
