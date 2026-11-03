package com.utc.guerrarobots.domain.rules

import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.ResultadoIntento

data class FilaPosicion(
    val equipoId: Long,
    val lugar: Int,
    val mejorMs: Long?,
    val segundoMs: Long?,
    val empate: Boolean,
    val sinTiempo: Boolean,
    val intentos: List<Intento>,
)

object Ranking {

    private class Base(
        val equipoId: Long,
        val validos: List<Long>,
        val extras: List<Long?>,
        val todos: List<Intento>,
    ) {
        val mejor: Long get() = validos[0]
        val segundo: Long? get() = validos.getOrNull(1)
    }

    fun calcular(intentos: List<Intento>, maxIntentos: Int = 2): List<FilaPosicion> {
        val bases = intentos.groupBy { it.equipoId }.map { (equipoId, lista) ->
            val ordenados = lista.sortedBy { it.numero }
            val validos = ordenados
                .filter { it.numero <= maxIntentos && it.resultado == ResultadoIntento.VALIDO && it.tiempoEfectivoMs != null }
                .map { it.tiempoEfectivoMs!! }
                .sorted()
            val extras = ordenados
                .filter { it.numero > maxIntentos }
                .map { if (it.resultado == ResultadoIntento.VALIDO) it.tiempoEfectivoMs else null }
            Base(equipoId, validos, extras, ordenados)
        }

        val conTiempo = bases.filter { it.validos.isNotEmpty() }
        val grupos = conTiempo.groupBy { it.mejor to it.segundo }.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second ?: Long.MAX_VALUE }))
            .map { e -> e.value.sortedBy { it.equipoId } }

        val filas = mutableListOf<FilaPosicion>()
        var lugar = 1
        for (grupo in grupos.flatMap { desempatar(it, 0) }) {
            val empate = grupo.size > 1
            grupo.forEach { b -> filas += FilaPosicion(b.equipoId, lugar, b.mejor, b.segundo, empate, false, b.todos) }
            lugar += grupo.size
        }

        bases.filter { it.validos.isEmpty() }.sortedBy { it.equipoId }.forEach { b ->
            filas += FilaPosicion(b.equipoId, conTiempo.size + 1, null, null, false, true, b.todos)
        }
        return filas
    }

    private fun desempatar(grupo: List<Base>, ronda: Int): List<List<Base>> {
        if (grupo.size == 1) return listOf(grupo)
        if (grupo.any { it.extras.size <= ronda }) return listOf(grupo)
        return grupo.groupBy { it.extras[ronda] ?: Long.MAX_VALUE }
            .toSortedMap()
            .values
            .flatMap { desempatar(it, ronda + 1) }
    }
}
