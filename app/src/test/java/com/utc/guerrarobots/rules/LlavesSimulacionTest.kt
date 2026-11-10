package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.Lado
import com.utc.guerrarobots.domain.rules.Llaves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Simulación del sorteo de llaves para fútbol robot y combate: con equipos de ejemplo se sortea
 * muchas veces y se comprueba que nadie se enfrente a su mismo plantel en su primer cruce, que los
 * equipos sin rival pasan directo y que cada sorteo da un cuadro distinto.
 *
 * Al correrla, cada prueba imprime un cuadro de ejemplo (se ve en la ventana Run de Android Studio).
 */
class LlavesSimulacionTest {

    private data class Eq(val id: Long, val nombre: String, val plantel: String)

    /** Arma equipos de ejemplo: ("Toluca" to 2) crea "Toluca 1" y "Toluca 2" del plantel Toluca. */
    private fun equipos(vararg grupos: Pair<String, Int>): List<Eq> {
        var id = 1L
        val lista = mutableListOf<Eq>()
        for ((plantel, cuantos) in grupos) {
            for (k in 1..cuantos) lista += Eq(id++, "$plantel $k", plantel)
        }
        return lista
    }

    private fun sortear(equipos: List<Eq>, semilla: Int): List<Llaves.PlanPartida> {
        val plantelDe = equipos.associate { it.id to it.plantel }
        val baraja = equipos.map { it.id }.shuffled(Random(semilla))
        return Llaves.construir(Llaves.evitarMismoPlantel(baraja, plantelDe))
    }

    /** Cruces seguros del mismo plantel: ronda 1, y ronda 2 entre dos equipos con bye. */
    private fun seguros(plan: List<Llaves.PlanPartida>, plantelDe: Map<Long, String>): Int =
        plan.filter { it.ronda <= 2 && it.equipoA != null && it.equipoB != null }
            .count { plantelDe.getValue(it.equipoA!!) == plantelDe.getValue(it.equipoB!!) }

    /** Equipo con bye contra uno de los dos que pelean por pasar, siendo del mismo plantel. */
    private fun posibles(plan: List<Llaves.PlanPartida>, plantelDe: Map<Long, String>): Int {
        val ronda2 = plan.filter { it.ronda == 2 }.associateBy { it.posicion }
        var total = 0
        for (p in plan.filter { it.ronda == 1 && !it.esBye }) {
            val sig = p.siguiente ?: continue
            val destino = ronda2.getValue(sig.posicion)
            val otroLado = if (sig.lado == Lado.A) destino.equipoB else destino.equipoA
            if (otroLado == null) continue   // el otro lado también es un cruce real: ya habrán jugado
            for (c in listOf(p.equipoA!!, p.equipoB!!)) {
                if (plantelDe.getValue(c) == plantelDe.getValue(otroLado)) total++
            }
        }
        return total
    }

    private fun mostrar(titulo: String, equipos: List<Eq>, plan: List<Llaves.PlanPartida>) {
        val porId = equipos.associateBy { it.id }
        fun nombre(id: Long?) = id?.let { "${porId.getValue(it).nombre}" } ?: "(por definir)"
        val tamano = plan.count { it.ronda == 1 } * 2
        println("=== $titulo · ${equipos.size} equipos · cuadro de $tamano (${tamano - equipos.size} pasan directo) ===")
        for (ronda in 1..2) {
            println("Ronda $ronda:")
            plan.filter { it.ronda == ronda }.forEach { p ->
                if (p.esBye) println("  ${nombre(p.ganador)} -> pasa directo (sin rival)")
                else println("  ${nombre(p.equipoA)}  vs  ${nombre(p.equipoB)}")
            }
        }
    }

    /** Sortea 200 veces: nunca debe haber choques de plantel, ni seguros ni posibles. */
    private fun comprobarEvitable(titulo: String, equipos: List<Eq>) {
        val plantelDe = equipos.associate { it.id to it.plantel }
        for (semilla in 1..200) {
            val plan = sortear(equipos, semilla)
            assertEquals("$titulo, sorteo $semilla: cruces seguros del mismo plantel", 0, seguros(plan, plantelDe))
            assertEquals("$titulo, sorteo $semilla: cruces posibles del mismo plantel", 0, posibles(plan, plantelDe))
        }
        mostrar(titulo, equipos, sortear(equipos, 1))
    }

    @Test
    fun `futbol con 11 equipos`() = comprobarEvitable(
        "FÚTBOL ROBOT",
        equipos("Toluca" to 2, "Neza" to 2, "Ecatepec" to 2, "Aragón" to 1, "Chalco" to 1, "Puebla" to 1, "Zona Rosa" to 1, "Toreo" to 1),
    )

    @Test
    fun `futbol con 16 equipos`() = comprobarEvitable(
        "FÚTBOL ROBOT",
        equipos("Toluca" to 4, "Neza" to 4, "Ecatepec" to 4, "Aragón" to 4),
    )

    @Test
    fun `combate con 9 equipos, la mayoria pasa directo`() = comprobarEvitable(
        "COMBATE DE ROBOTS",
        equipos("Toluca" to 2, "Neza" to 2, "Aragón" to 1, "Chalco" to 1, "Puebla" to 1, "Toreo" to 1, "Ixtapaluca" to 1),
    )

    @Test
    fun `combate con 14 equipos`() = comprobarEvitable(
        "COMBATE DE ROBOTS",
        equipos("Toluca" to 3, "Neza" to 3, "Ecatepec" to 2, "Aragón" to 2, "Chalco" to 1, "Puebla" to 1, "Toreo" to 1, "Zona Rosa" to 1),
    )

    @Test
    fun `combate con 20 equipos`() = comprobarEvitable(
        "COMBATE DE ROBOTS",
        equipos("Toluca" to 4, "Neza" to 4, "Ecatepec" to 3, "Aragón" to 3, "Chalco" to 2, "Puebla" to 2, "Toreo" to 1, "Zona Rosa" to 1),
    )

    @Test
    fun `los equipos sin rival pasan directo y nadie se repite`() {
        val eqs = equipos("Toluca" to 2, "Neza" to 2, "Ecatepec" to 2, "Aragón" to 1, "Chalco" to 1, "Puebla" to 1, "Zona Rosa" to 1, "Toreo" to 1)
        for (semilla in 1..50) {
            val plan = sortear(eqs, semilla)
            val ronda1 = plan.filter { it.ronda == 1 }
            val byes = ronda1.filter { it.esBye }
            assertEquals("cuadro de 16 con 11 equipos: 5 pasan directo", 5, byes.size)
            byes.forEach { assertTrue("el bye ya tiene ganador", it.ganador != null) }
            val presentes = ronda1.flatMap { listOfNotNull(it.equipoA, it.equipoB) }
            assertEquals("cada equipo aparece una sola vez", eqs.map { it.id }.sorted(), presentes.sorted())
            // Los que pasaron directo ya ocupan su lugar en la ronda 2.
            val enRonda2 = plan.filter { it.ronda == 2 }.flatMap { listOfNotNull(it.equipoA, it.equipoB) }
            assertEquals(byes.mapNotNull { it.ganador }.sorted(), enRonda2.sorted())
        }
    }

    @Test
    fun `cada sorteo da un cuadro distinto`() {
        val eqs = equipos("Toluca" to 2, "Neza" to 2, "Ecatepec" to 2, "Aragón" to 1, "Chalco" to 1, "Puebla" to 1, "Zona Rosa" to 1, "Toreo" to 1)
        val cuadros = (1..30).map { semilla ->
            sortear(eqs, semilla).filter { it.ronda == 1 }.map { it.equipoA to it.equipoB }
        }.toSet()
        assertTrue("30 sorteos dieron solo ${cuadros.size} cuadros distintos", cuadros.size >= 20)
    }

    @Test
    fun `si un plantel trae casi todos los equipos queda un solo cruce inevitable`() {
        val eqs = equipos("Toluca" to 5, "Neza" to 1, "Puebla" to 1)
        val plantelDe = eqs.associate { it.id to it.plantel }
        for (semilla in 1..100) {
            val plan = sortear(eqs, semilla)
            // 5 de 7 equipos son de Toluca: no se puede evitar que dos de ellos se enfrenten.
            assertEquals("sorteo $semilla", 1, seguros(plan, plantelDe))
        }
        mostrar("CASO LÍMITE (5 de 7 son de Toluca)", eqs, sortear(eqs, 1))
    }
}
