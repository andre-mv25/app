package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.Lado
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Sorteo
import com.utc.guerrarobots.domain.model.TipoSorteo
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.CatalogoRepository
import com.utc.guerrarobots.domain.repository.AuditoriaRepository
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.repository.PartidaRepository
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.repository.SorteoRepository
import com.utc.guerrarobots.domain.repository.Transaccion
import com.utc.guerrarobots.domain.service.AuditoriaService
import com.utc.guerrarobots.domain.rules.Llaves
import kotlin.random.Random

/** Llaves de eliminación directa de fútbol robot y combate. */
class LlavesService(
    private val equipos: EquipoRepository,
    private val partidas: PartidaRepository,
    private val sorteos: SorteoRepository,
    private val catalogo: CatalogoRepository,
    private val tx: Transaccion,
    private val reloj: Reloj,
    private val auditoria: AuditoriaService? = null,
) {

    data class PartidaVista(
        val partida: Partida,
        val equipoA: Equipo?,
        val equipoB: Equipo?,
        val ganador: Equipo?,
    )

    data class RondaVista(val ronda: Int, val nombre: String, val partidas: List<PartidaVista>)

    data class Podio(val campeon: Equipo, val subcampeon: Equipo?)

    /**
     * Arma las llaves con los equipos ACEPTADOS de la categoría.
     * - ALEATORIO: baraja con [semilla] (si no se da, se usa la hora). Con la misma semilla
     *   sale el mismo sorteo, así se puede revisar.
     * - MANUAL: [ordenManual] es el orden de siembra; el primero es la semilla 1.
     * Se puede repetir mientras no haya partidos en curso o jugados.
     */
    suspend fun generar(
        categoria: ClaveCategoria,
        tipo: TipoSorteo = TipoSorteo.ALEATORIO,
        semilla: Long? = null,
        ordenManual: List<Long>? = null,
    ): Resultado<List<RondaVista>> = tx.ejecutar { generarInterno(categoria, tipo, semilla, ordenManual) }

    private suspend fun generarInterno(
        categoria: ClaveCategoria,
        tipo: TipoSorteo,
        semilla: Long?,
        ordenManual: List<Long>?,
    ): Resultado<List<RondaVista>> {
        val info = catalogo.categoria(categoria)
        if (info.metodo != Metodo.LLAVES) {
            return falla(Codigo.VALIDACION, "${info.nombre} se clasifica por tiempo, no por llaves", "categoria")
        }
        val aceptados = equipos.listar(EstadoEquipo.ACEPTADO, categoria)
        if (aceptados.size < 2) {
            return falla(Codigo.VALIDACION, "Se necesitan al menos 2 equipos aceptados (hay ${aceptados.size})")
        }
        val jugadas = partidas.listar(categoria).any {
            !it.esBye && (it.estado == EstadoPartida.EN_CURSO || it.estado == EstadoPartida.FINALIZADA)
        }
        if (jugadas) {
            return falla(Codigo.CONFLICTO, "Ya hay partidos en curso o jugados: no se puede repetir el sorteo")
        }

        val ids = aceptados.map { it.id }
        val (orden, semillaUsada) = when (tipo) {
            TipoSorteo.ALEATORIO -> {
                val s = semilla ?: reloj.ahora()
                val baraja = ids.sorted().shuffled(Random(s))
                // En la primera ronda no se enfrentan equipos del mismo plantel (en lo posible).
                val plantelDe = aceptados.associate { it.id to it.plantel }
                Pair(Llaves.evitarMismoPlantel(baraja, plantelDe), s)
            }
            TipoSorteo.MANUAL -> {
                if (ordenManual == null || ordenManual.size != ids.size || ordenManual.toSet() != ids.toSet()) {
                    return falla(
                        Codigo.VALIDACION,
                        "El orden manual debe incluir exactamente a los equipos aceptados, sin repetir",
                        "ordenManual",
                    )
                }
                Pair(ordenManual, 0L)
            }
        }

        partidas.eliminarPorCategoria(categoria)
        sorteos.insertar(Sorteo(categoria = categoria, tipo = tipo, semilla = semillaUsada, creadoEn = reloj.ahora()))

        // Se guardan de la final hacia atrás para conocer el id de la partida a la que avanza cada una.
        val idPorLugar = mutableMapOf<Pair<Int, Int>, Long>()
        val plan = Llaves.construir(orden)
            .sortedWith(compareByDescending<Llaves.PlanPartida> { it.ronda }.thenBy { it.posicion })
        for (p in plan) {
            val sig = p.siguiente
            val partida = Partida(
                categoria = categoria,
                ronda = p.ronda,
                posicion = p.posicion,
                equipoAId = p.equipoA,
                equipoBId = p.equipoB,
                esBye = p.esBye,
                estado = when {
                    p.esBye -> EstadoPartida.FINALIZADA
                    p.equipoA != null && p.equipoB != null -> EstadoPartida.PROGRAMADA
                    else -> EstadoPartida.PENDIENTE
                },
                siguientePartidaId = sig?.let { idPorLugar.getValue(it.ronda to it.posicion) },
                siguienteLado = sig?.lado,
                ganadorId = p.ganador,
                finalizadaEn = if (p.esBye) reloj.ahora() else null,
            )
            idPorLugar[p.ronda to p.posicion] = partidas.insertar(partida)
        }
        auditoria?.registrar(
            Acciones.LLAVES_GENERADAS, "categoria", null,
            "${info.nombre} · ${aceptados.size} equipos · sorteo ${if (tipo == TipoSorteo.MANUAL) "manual" else "aleatorio"}",
        )
        return Resultado.Ok(cuadro(categoria))
    }

    /**
     * Asigna cancha y turno a cada partido de cada ronda: se reparten por turnos entre las
     * canchas (1, 2, 3, 4, 1...). Los byes no ocupan cancha.
     */
    suspend fun programar(categoria: ClaveCategoria): Resultado<List<RondaVista>> =
        tx.ejecutar { programarInterno(categoria) }

    private suspend fun programarInterno(categoria: ClaveCategoria): Resultado<List<RondaVista>> {
        val info = catalogo.categoria(categoria)
        if (info.metodo != Metodo.LLAVES) {
            return falla(Codigo.VALIDACION, "${info.nombre} se clasifica por tiempo, no por llaves", "categoria")
        }
        val todas = partidas.listar(categoria)
        if (todas.isEmpty()) return falla(Codigo.CONFLICTO, "Primero genera las llaves")

        val canchas = info.canchas.coerceAtLeast(1)
        for ((_, deLaRonda) in todas.groupBy { it.ronda }) {
            val jugables = deLaRonda.filter { !it.esBye }.sortedBy { it.posicion }
            val reparto = Llaves.repartoCanchas(jugables.size, canchas)
            jugables.zip(reparto).forEach { (p, lugar) ->
                if (p.estado != EstadoPartida.FINALIZADA && p.estado != EstadoPartida.EN_CURSO) {
                    partidas.actualizar(p.copy(cancha = lugar.first, turno = lugar.second))
                }
            }
        }
        auditoria?.registrar(Acciones.LLAVES_PROGRAMADAS, "categoria", null, "${info.nombre} · ${canchas} canchas")
        return Resultado.Ok(cuadro(categoria))
    }

    /** Pasa una partida lista (con sus dos equipos) a EN_CURSO. Es idempotente. */
    suspend fun iniciar(partidaId: Long): Resultado<Partida> = tx.ejecutar {
        val p = partidas.obtener(partidaId)
            ?: return@ejecutar falla(Codigo.NO_ENCONTRADO, "No existe la partida", "partidaId")
        if (p.estado == EstadoPartida.EN_CURSO) return@ejecutar Resultado.Ok(p)
        if (p.estado != EstadoPartida.PROGRAMADA) {
            return@ejecutar falla(Codigo.CONFLICTO, "La partida no está lista: faltan equipos o ya terminó")
        }
        val enCurso = p.copy(estado = EstadoPartida.EN_CURSO)
        partidas.actualizar(enCurso)
        auditoria?.registrar(
            Acciones.PARTIDA_INICIADA, "partida", p.id, "${nombre(p.equipoAId)} vs ${nombre(p.equipoBId)}",
        )
        Resultado.Ok(enCurso)
    }

    /**
     * Resultado de fútbol. Gana quien meta más goles. Si hay empate se necesita [desempate]
     * (el lado que ganó en tiempo extra o penales). Repetir el mismo resultado es seguro;
     * mandar uno distinto a una partida ya cerrada da CONFLICTO.
     */
    suspend fun resultadoFutbol(
        partidaId: Long,
        golesA: Int,
        golesB: Int,
        desempate: Lado? = null,
    ): Resultado<Partida> = tx.ejecutar {
        val p = partidas.obtener(partidaId)
            ?: return@ejecutar falla(Codigo.NO_ENCONTRADO, "No existe la partida", "partidaId")
        if (p.categoria != ClaveCategoria.FUTBOL) {
            return@ejecutar falla(Codigo.VALIDACION, "Esta partida no es de fútbol", "partidaId")
        }
        if (golesA < 0 || golesB < 0) {
            return@ejecutar falla(Codigo.VALIDACION, "Los goles no pueden ser negativos", "goles")
        }
        val lado = when {
            golesA > golesB -> Lado.A
            golesB > golesA -> Lado.B
            else -> desempate
                ?: return@ejecutar falla(Codigo.VALIDACION, "Hay empate: define el ganador por tiempo extra o penales", "desempate")
        }
        val ganadorId = if (lado == Lado.A) p.equipoAId else p.equipoBId
        if (p.estado == EstadoPartida.FINALIZADA) {
            return@ejecutar if (p.ganadorId == ganadorId && p.golesA == golesA && p.golesB == golesB) {
                Resultado.Ok(p)
            } else {
                falla(Codigo.CONFLICTO, "La partida ya tiene otro resultado")
            }
        }
        if (p.estado != EstadoPartida.EN_CURSO || ganadorId == null) {
            return@ejecutar falla(Codigo.CONFLICTO, "La partida no está en curso")
        }
        cerrar(p, ganadorId, golesA, golesB)
    }

    /** Resultado de combate: gana el robot que quedó en pie. */
    suspend fun resultadoCombate(partidaId: Long, ganadorId: Long): Resultado<Partida> = tx.ejecutar {
        val p = partidas.obtener(partidaId)
            ?: return@ejecutar falla(Codigo.NO_ENCONTRADO, "No existe la partida", "partidaId")
        if (p.categoria != ClaveCategoria.COMBATE) {
            return@ejecutar falla(Codigo.VALIDACION, "Esta partida no es de combate", "partidaId")
        }
        if (ganadorId != p.equipoAId && ganadorId != p.equipoBId) {
            return@ejecutar falla(Codigo.VALIDACION, "El ganador debe ser uno de los dos equipos de la partida", "ganadorId")
        }
        if (p.estado == EstadoPartida.FINALIZADA) {
            return@ejecutar if (p.ganadorId == ganadorId) {
                Resultado.Ok(p)
            } else {
                falla(Codigo.CONFLICTO, "La partida ya tiene otro resultado")
            }
        }
        if (p.estado != EstadoPartida.EN_CURSO) {
            return@ejecutar falla(Codigo.CONFLICTO, "La partida no está en curso")
        }
        cerrar(p, ganadorId, null, null)
    }

    /** Cierra la partida y mete al ganador en su lugar de la siguiente ronda. */
    private suspend fun nombre(equipoId: Long?): String =
        equipoId?.let { equipos.obtener(it)?.nombre } ?: "por definir"

    private suspend fun cerrar(p: Partida, ganadorId: Long, golesA: Int?, golesB: Int?): Resultado<Partida> {
        val cerrada = p.copy(
            estado = EstadoPartida.FINALIZADA,
            ganadorId = ganadorId,
            golesA = golesA,
            golesB = golesB,
            finalizadaEn = reloj.ahora(),
        )
        partidas.actualizar(cerrada)
        val a = nombre(p.equipoAId)
        val b = nombre(p.equipoBId)
        auditoria?.registrar(
            Acciones.PARTIDA_FINALIZADA, "partida", p.id,
            (if (golesA != null && golesB != null) "$a $golesA - $golesB $b" else "$a vs $b") + " · ganó ${nombre(ganadorId)}",
        )

        val siguienteId = p.siguientePartidaId
        if (siguienteId != null) {
            val siguiente = partidas.obtener(siguienteId)
                ?: return falla(Codigo.CONFLICTO, "Las llaves están dañadas: falta la siguiente partida")
            val conGanador = when (p.siguienteLado) {
                Lado.A -> siguiente.copy(equipoAId = ganadorId)
                Lado.B -> siguiente.copy(equipoBId = ganadorId)
                null -> siguiente
            }
            val lista = conGanador.equipoAId != null && conGanador.equipoBId != null &&
                conGanador.estado == EstadoPartida.PENDIENTE
            partidas.actualizar(if (lista) conGanador.copy(estado = EstadoPartida.PROGRAMADA) else conGanador)
        }
        return Resultado.Ok(cerrada)
    }

    /** Las llaves completas, ronda por ronda, con los equipos ya resueltos. */
    suspend fun cuadro(categoria: ClaveCategoria): List<RondaVista> {
        val todas = partidas.listar(categoria)
        if (todas.isEmpty()) return emptyList()
        val porId = equipos.listar(categoria = categoria).associateBy { it.id }
        val total = todas.maxOf { it.ronda }
        return todas.groupBy { it.ronda }.toSortedMap().map { (ronda, deLaRonda) ->
            RondaVista(
                ronda = ronda,
                nombre = Llaves.nombreRonda(ronda, total),
                partidas = deLaRonda.sortedBy { it.posicion }.map { p ->
                    PartidaVista(p, p.equipoAId?.let { porId[it] }, p.equipoBId?.let { porId[it] }, p.ganadorId?.let { porId[it] })
                },
            )
        }
    }

    /** Campeón y subcampeón, cuando la final ya terminó. */
    suspend fun campeon(categoria: ClaveCategoria): Podio? {
        val todas = partidas.listar(categoria)
        if (todas.isEmpty()) return null
        val final = todas.maxByOrNull { it.ronda } ?: return null
        val ganadorId = final.ganadorId
        if (final.estado != EstadoPartida.FINALIZADA || ganadorId == null) return null
        val campeon = equipos.obtener(ganadorId) ?: return null
        val rivalId = if (final.equipoAId == ganadorId) final.equipoBId else final.equipoAId
        return Podio(campeon, rivalId?.let { equipos.obtener(it) })
    }

    /** Todas las partidas de todas las categorías. */
    suspend fun partidasTodas(): List<Partida> {
        val todas = mutableListOf<Partida>()
        catalogo.categorias().forEach { categoria ->
            todas.addAll(partidas.listar(categoria.clave))
        }
        return todas
    }
}
