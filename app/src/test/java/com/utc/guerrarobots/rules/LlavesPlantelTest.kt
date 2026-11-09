package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.rules.Llaves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LlavesPlantelTest {

    /** Cuántos cruces de la primera ronda enfrentan a dos equipos del mismo plantel. */
    private fun choques(orden: List<Long>, plantelDe: Map<Long, String>): Int =
        Llaves.construir(orden)
            .filter { it.ronda == 1 && !it.esBye }
            .count { plantelDe.getValue(it.equipoA!!) == plantelDe.getValue(it.equipoB!!) }

    private fun plantelesPorGrupo(vararg tamanos: Int): Map<Long, String> {
        var id = 1L
        val mapa = mutableMapOf<Long, String>()
        tamanos.forEachIndexed { grupo, cuantos -> repeat(cuantos) { mapa[id++] = "Plantel $grupo" } }
        return mapa
    }

    private fun barajado(plantelDe: Map<Long, String>, semilla: Int): List<Long> =
        plantelDe.keys.sorted().shuffled(Random(semilla))

    @Test
    fun `dos planteles de cuatro equipos nunca se cruzan entre si mismos`() {
        val plantelDe = plantelesPorGrupo(4, 4)
        for (semilla in 1..50) {
            val orden = Llaves.evitarMismoPlantel(barajado(plantelDe, semilla), plantelDe)
            assertEquals(0, choques(orden, plantelDe))
        }
    }

    @Test
    fun `dieciseis equipos de cuatro planteles no chocan`() {
        val plantelDe = plantelesPorGrupo(4, 4, 4, 4)
        for (semilla in 1..50) {
            val orden = Llaves.evitarMismoPlantel(barajado(plantelDe, semilla), plantelDe)
            assertEquals(0, choques(orden, plantelDe))
        }
    }

    @Test
    fun `con byes tambien se evita el choque`() {
        val plantelDe = plantelesPorGrupo(5, 5)
        for (semilla in 1..50) {
            val orden = Llaves.evitarMismoPlantel(barajado(plantelDe, semilla), plantelDe)
            assertEquals(0, choques(orden, plantelDe))
        }
    }

    @Test
    fun `conserva a los mismos equipos`() {
        val plantelDe = plantelesPorGrupo(3, 3, 2)
        val original = barajado(plantelDe, 7)
        val orden = Llaves.evitarMismoPlantel(original, plantelDe)
        assertEquals(original.sorted(), orden.sorted())
    }

    @Test
    fun `si un plantel tiene casi todos los equipos no empeora y deja el minimo posible`() {
        val plantelDe = plantelesPorGrupo(6, 2)
        for (semilla in 1..20) {
            val original = barajado(plantelDe, semilla)
            val orden = Llaves.evitarMismoPlantel(original, plantelDe)
            assertTrue(choques(orden, plantelDe) <= choques(original, plantelDe))
            assertEquals(2, choques(orden, plantelDe)) // 6 contra 2: cuatro cruces, dos no se pueden evitar
        }
    }

    @Test
    fun `con menos de dos equipos no hace nada`() {
        assertEquals(listOf(1L), Llaves.evitarMismoPlantel(listOf(1L), mapOf(1L to "A")))
    }
}
