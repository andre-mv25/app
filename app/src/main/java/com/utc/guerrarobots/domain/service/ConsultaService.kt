package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.CatalogoRepository
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.rules.Texto

/**
 * Consulta para el participante: busca su equipo y le dice contra quién juega y en qué cancha o
 * pista (llaves), o su turno, sus intentos y su lugar (categorías por tiempo).
 * Solo lee: no cambia nada. Del rival solo muestra el nombre del equipo y del robot.
 */
class ConsultaService(
    private val equipos: EquipoRepository,
    private val catalogo: CatalogoRepository,
    private val servicioLlaves: LlavesService,
    private val servicioTiempos: TiemposService,
) {

    /** Lo único que se muestra de un rival. */
    data class Rival(val equipo: String, val robot: String)

    enum class EstadoLlaves { SIN_LLAVES, JUEGA_PRONTO, ESPERANDO_RIVAL, EN_CURSO, ELIMINADO, SUBCAMPEON, CAMPEON }

    data class SituacionLlaves(
        val estado: EstadoLlaves,
        val ronda: String? = null,
        val rival: Rival? = null,
        val cancha: Int? = null,
        val turno: Int? = null,
    )

    data class SituacionTiempos(
        val turno: Int?,
        val pista: Int?,
        val intentos: List<Intento>,
        val restantes: Int,
        val lugar: Int?,
        val mejorMs: Long?,
        val categoriaCerrada: Boolean,
    )

    /** Todo lo que ve el participante. Solo uno de [llaves] y [tiempos] viene lleno, según la categoría. */
    data class Consulta(
        val equipo: Equipo,
        val categoria: Categoria,
        val llaves: SituacionLlaves? = null,
        val tiempos: SituacionTiempos? = null,
    )

    /**
     * Busca equipos por nombre de equipo, nombre de robot o folio (sin importar mayúsculas ni
     * acentos). Pide al menos 2 letras y devuelve máximo 10. Los rechazados no aparecen.
     */
    suspend fun buscar(texto: String): List<Equipo> {
        val q = Texto.normalizar(texto)
        if (q.length < 2) return emptyList()
        return equipos.listar()
            .filter { it.estado != EstadoEquipo.RECHAZADO }
            .filter {
                Texto.normalizar(it.nombre).contains(q) ||
                    Texto.normalizar(it.robot.nombre).contains(q) ||
                    Texto.normalizar(it.folio).contains(q)
            }
            .sortedBy { Texto.normalizar(it.nombre) }
            .take(10)
    }

    /** Busca un equipo exacto por folio (sin importar mayúsculas ni acentos). Devuelve null si no existe. */
    suspend fun buscarPorFolio(folio: String): Equipo? {
        val q = Texto.normalizar(folio)
        return equipos.listar()
            .firstOrNull { Texto.normalizar(it.folio) == q && it.estado != EstadoEquipo.RECHAZADO }
    }

    /** La situación actual de un equipo. Si todavía no está aceptado, solo trae sus datos. */
    suspend fun consultar(equipoId: Long): Resultado<Consulta> {
        val equipo = equipos.obtener(equipoId)
            ?: return falla(Codigo.NO_ENCONTRADO, "No se encontró el equipo")
        val categoria = catalogo.categoria(equipo.robot.categoria)
        if (equipo.estado != EstadoEquipo.ACEPTADO) return Resultado.Ok(Consulta(equipo, categoria))
        val consulta = when (categoria.metodo) {
            Metodo.LLAVES -> Consulta(
                equipo, categoria,
                llaves = situacionLlaves(equipo.id, servicioLlaves.cuadro(categoria.clave)),
            )
            Metodo.RANKING_TIEMPO -> Consulta(equipo, categoria, tiempos = situacionTiempos(equipo, categoria))
        }
        return Resultado.Ok(consulta)
    }

    private suspend fun situacionTiempos(equipo: Equipo, categoria: Categoria): SituacionTiempos {
        val avance = servicioTiempos.avance(categoria.clave).firstOrNull { it.equipo.id == equipo.id }
        val fila = servicioTiempos.posiciones(categoria.clave).firstOrNull { it.equipo.id == equipo.id }?.fila
        return SituacionTiempos(
            turno = avance?.turno,
            pista = avance?.pista,
            intentos = avance?.intentos.orEmpty(),
            restantes = avance?.restantes ?: categoria.intentosMaximos,
            lugar = if (fila != null && !fila.sinTiempo) fila.lugar else null,
            mejorMs = fila?.mejorMs,
            categoriaCerrada = categoria.cerrada,
        )
    }

    companion object {
        /**
         * Dónde está un equipo dentro de las llaves. Es una función pura (recibe el cuadro ya
         * armado), así se prueba sin base de datos.
         */
        fun situacionLlaves(equipoId: Long, cuadro: List<LlavesService.RondaVista>): SituacionLlaves {
            if (cuadro.isEmpty()) return SituacionLlaves(EstadoLlaves.SIN_LLAVES)
            val ultimaRonda = cuadro.maxOf { it.ronda }
            val suyas = cuadro.sortedBy { it.ronda }.flatMap { r ->
                r.partidas
                    .filter { it.partida.equipoAId == equipoId || it.partida.equipoBId == equipoId }
                    .map { r to it }
            }
            if (suyas.isEmpty()) return SituacionLlaves(EstadoLlaves.SIN_LLAVES)

            val pendiente = suyas.firstOrNull { it.second.partida.estado != EstadoPartida.FINALIZADA }
            if (pendiente != null) {
                val (ronda, vista) = pendiente
                val p = vista.partida
                val rivalEquipo = if (p.equipoAId == equipoId) vista.equipoB else vista.equipoA
                val rival = rivalEquipo?.let { Rival(it.nombre, it.robot.nombre) }
                val estado = when {
                    p.estado == EstadoPartida.EN_CURSO -> EstadoLlaves.EN_CURSO
                    rival == null -> EstadoLlaves.ESPERANDO_RIVAL
                    else -> EstadoLlaves.JUEGA_PRONTO
                }
                return SituacionLlaves(estado, ronda.nombre, rival, p.cancha, p.turno)
            }

            // Todas sus partidas ya terminaron: la última dice cómo le fue.
            val (ronda, vista) = suyas.last()
            val gano = vista.partida.ganadorId == equipoId
            return when {
                gano && ronda.ronda == ultimaRonda -> SituacionLlaves(EstadoLlaves.CAMPEON, ronda.nombre)
                gano -> SituacionLlaves(EstadoLlaves.ESPERANDO_RIVAL)   // ganó, falta que se arme su siguiente cruce
                ronda.ronda == ultimaRonda -> SituacionLlaves(EstadoLlaves.SUBCAMPEON, ronda.nombre)
                else -> SituacionLlaves(EstadoLlaves.ELIMINADO, ronda.nombre)
            }
        }
    }
}
