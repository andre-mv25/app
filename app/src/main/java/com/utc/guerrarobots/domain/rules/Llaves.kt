package com.utc.guerrarobots.domain.rules

import com.utc.guerrarobots.domain.model.Lado

/**
 * Llaves de eliminación directa, como en un torneo de fútbol.
 *
 * El cuadro es del tamaño de la siguiente potencia de 2 (8, 16, 32, 64...). Los lugares
 * que sobran son "byes": el equipo pasa directo a la siguiente ronda. Los equipos se
 * colocan con la siembra estándar (1 contra el último, 2 contra el penúltimo...), así que
 * los byes caen en los primeros lugares y nunca se enfrentan dos byes entre sí.
 */
object Llaves {

    data class Siguiente(val ronda: Int, val posicion: Int, val lado: Lado)

    /** Un cruce del plan. [ganador] solo viene lleno en los byes. */
    data class PlanPartida(
        val ronda: Int,
        val posicion: Int,
        val equipoA: Long?,
        val equipoB: Long?,
        val esBye: Boolean,
        val ganador: Long?,
        val siguiente: Siguiente?,
    )

    fun tamanoCuadro(equipos: Int): Int {
        require(equipos >= 2) { "Se necesitan al menos 2 equipos" }
        var tamano = 1
        while (tamano < equipos) tamano *= 2
        return tamano
    }

    fun totalRondas(tamanoCuadro: Int): Int = Integer.numberOfTrailingZeros(tamanoCuadro)

    /** Orden de la siembra estándar. Para 8: 1,8,4,5,2,7,3,6 (cruces 1-8, 4-5, 2-7, 3-6). */
    fun ordenSemillas(tamano: Int): List<Int> {
        require(tamano >= 2 && tamano and (tamano - 1) == 0) { "El tamaño debe ser potencia de 2" }
        var orden = listOf(1)
        while (orden.size < tamano) {
            val suma = orden.size * 2 + 1
            orden = orden.flatMap { listOf(it, suma - it) }
        }
        return orden
    }

    private class Cruce(var a: Long? = null, var b: Long? = null, var bye: Boolean = false, var ganador: Long? = null)

    /**
     * Arma todas las rondas. [equiposEnOrden] es el orden de siembra: el primero es la
     * semilla 1. Para un sorteo aleatorio basta con barajar la lista antes de llamar.
     */
    fun construir(equiposEnOrden: List<Long>): List<PlanPartida> {
        val n = equiposEnOrden.size
        require(equiposEnOrden.toSet().size == n) { "Hay equipos repetidos" }
        val tamano = tamanoCuadro(n)
        val rondas = totalRondas(tamano)
        val orden = ordenSemillas(tamano)

        val cruces: List<List<Cruce>> = (1..rondas).map { r -> List(tamano shr r) { Cruce() } }

        for (i in 0 until tamano / 2) {
            val cruce = cruces[0][i]
            cruce.a = equiposEnOrden.getOrNull(orden[2 * i] - 1)
            cruce.b = equiposEnOrden.getOrNull(orden[2 * i + 1] - 1)
            if (cruce.a == null || cruce.b == null) {
                cruce.bye = true
                cruce.ganador = cruce.a ?: cruce.b
            }
        }

        // Los equipos con bye ya ocupan su lugar en la segunda ronda.
        if (rondas >= 2) {
            cruces[0].forEachIndexed { i, cruce ->
                val ganador = cruce.ganador ?: return@forEachIndexed
                val destino = cruces[1][i / 2]
                if (i % 2 == 0) destino.a = ganador else destino.b = ganador
            }
        }

        val plan = mutableListOf<PlanPartida>()
        for (r in 1..rondas) {
            cruces[r - 1].forEachIndexed { i, cruce ->
                val posicion = i + 1
                val siguiente = if (r < rondas) {
                    Siguiente(r + 1, (posicion + 1) / 2, if (posicion % 2 == 1) Lado.A else Lado.B)
                } else null
                plan += PlanPartida(r, posicion, cruce.a, cruce.b, cruce.bye, cruce.ganador, siguiente)
            }
        }
        return plan
    }

    fun nombreRonda(ronda: Int, totalRondas: Int): String = when (totalRondas - ronda) {
        0 -> "Final"
        1 -> "Semifinal"
        2 -> "Cuartos de final"
        3 -> "Octavos de final"
        4 -> "Dieciseisavos de final"
        5 -> "Treintaidosavos de final"
        else -> "Ronda $ronda"
    }

    /**
     * Reparte [cantidad] partidos por turnos entre las canchas: 1, 2, 3, 4, 1, 2...
     * Devuelve (cancha, turno) para cada partido, en orden.
     */
    fun repartoCanchas(cantidad: Int, canchas: Int): List<Pair<Int, Int>> {
        require(canchas >= 1) { "Debe haber al menos una cancha" }
        return List(cantidad) { i -> Pair(i % canchas + 1, i / canchas + 1) }
    }
}
