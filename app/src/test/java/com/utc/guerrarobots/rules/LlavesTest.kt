package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.Lado
import com.utc.guerrarobots.domain.rules.Llaves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlavesTest {

    @Test
    fun `el cuadro es la siguiente potencia de 2`() {
        assertEquals(2, Llaves.tamanoCuadro(2))
        assertEquals(4, Llaves.tamanoCuadro(3))
        assertEquals(4, Llaves.tamanoCuadro(4))
        assertEquals(8, Llaves.tamanoCuadro(5))
        assertEquals(64, Llaves.tamanoCuadro(60))
        assertEquals(64, Llaves.tamanoCuadro(64))
        assertEquals(128, Llaves.tamanoCuadro(65))
    }

    @Test
    fun `menos de dos equipos no arma llaves`() {
        assertThrows(IllegalArgumentException::class.java) { Llaves.tamanoCuadro(1) }
        assertThrows(IllegalArgumentException::class.java) { Llaves.construir(listOf(1L)) }
    }

    @Test
    fun `la siembra estandar cruza el primero con el ultimo`() {
        assertEquals(listOf(1, 2), Llaves.ordenSemillas(2))
        assertEquals(listOf(1, 4, 2, 3), Llaves.ordenSemillas(4))
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), Llaves.ordenSemillas(8))
    }

    @Test
    fun `seis equipos dan dos byes para las primeras semillas`() {
        val plan = Llaves.construir(listOf(101L, 102L, 103L, 104L, 105L, 106L))
        val ronda1 = plan.filter { it.ronda == 1 }
        assertEquals(4, ronda1.size)
        assertEquals(listOf(101L, 102L), ronda1.filter { it.esBye }.map { it.ganador })
        assertEquals(2, plan.count { it.ronda == 2 })
        assertEquals(1, plan.count { it.ronda == 3 })

        // Los equipos con bye ya esperan en semifinales, cada uno en su cruce.
        val semis = plan.filter { it.ronda == 2 }.sortedBy { it.posicion }
        assertEquals(101L, semis[0].equipoA)
        assertNull(semis[0].equipoB)
        assertEquals(102L, semis[1].equipoA)
        assertNull(semis[1].equipoB)
    }

    @Test
    fun `sesenta equipos dan cuadro de 64 con 4 byes y 59 partidos`() {
        val equipos = (1L..60L).toList()
        val plan = Llaves.construir(equipos)

        assertEquals(6, plan.maxOf { it.ronda })
        val ronda1 = plan.filter { it.ronda == 1 }
        assertEquals(32, ronda1.size)
        assertEquals(4, ronda1.count { it.esBye })
        assertEquals(28, ronda1.count { !it.esBye })
        assertEquals(59, plan.count { !it.esBye })

        // Cada equipo aparece una sola vez en la primera ronda.
        val enRonda1 = ronda1.flatMap { listOfNotNull(it.equipoA, it.equipoB) }
        assertEquals(60, enRonda1.size)
        assertEquals(equipos.toSet(), enRonda1.toSet())

        // Nunca hay dos byes en el mismo cruce.
        assertTrue(ronda1.none { it.equipoA == null && it.equipoB == null })

        // Los partidos por ronda son 28, 16, 8, 4, 2, 1.
        val jugados = (1..6).map { r -> plan.count { it.ronda == r && !it.esBye } }
        assertEquals(listOf(28, 16, 8, 4, 2, 1), jugados)
    }

    @Test
    fun `una potencia de dos exacta no tiene byes`() {
        val plan = Llaves.construir((1L..8L).toList())
        assertEquals(7, plan.size)
        assertTrue(plan.none { it.esBye })
        assertEquals(4, plan.count { it.ronda == 1 })
    }

    @Test
    fun `dos equipos juegan solo la final`() {
        val plan = Llaves.construir(listOf(7L, 9L))
        assertEquals(1, plan.size)
        assertEquals(7L, plan[0].equipoA)
        assertEquals(9L, plan[0].equipoB)
        assertNull(plan[0].siguiente)
    }

    @Test
    fun `tres equipos dan un bye al primero`() {
        val plan = Llaves.construir(listOf(1L, 2L, 3L))
        val ronda1 = plan.filter { it.ronda == 1 }.sortedBy { it.posicion }
        assertTrue(ronda1[0].esBye)
        assertEquals(1L, ronda1[0].ganador)
        assertFalse(ronda1[1].esBye)
        assertEquals(2L, ronda1[1].equipoA)
        assertEquals(3L, ronda1[1].equipoB)
    }

    @Test
    fun `equipos repetidos fallan`() {
        assertThrows(IllegalArgumentException::class.java) { Llaves.construir(listOf(1L, 2L, 2L)) }
    }

    @Test
    fun `cada cruce apunta a su lugar en la siguiente ronda`() {
        val plan = Llaves.construir((1L..8L).toList())
        plan.filter { it.ronda < 3 }.forEach { p ->
            val s = p.siguiente!!
            assertEquals(p.ronda + 1, s.ronda)
            assertEquals((p.posicion + 1) / 2, s.posicion)
            assertEquals(if (p.posicion % 2 == 1) Lado.A else Lado.B, s.lado)
        }
        assertNull(plan.single { it.ronda == 3 }.siguiente)
    }

    @Test
    fun `nombres de ronda`() {
        assertEquals("Final", Llaves.nombreRonda(6, 6))
        assertEquals("Semifinal", Llaves.nombreRonda(5, 6))
        assertEquals("Cuartos de final", Llaves.nombreRonda(4, 6))
        assertEquals("Octavos de final", Llaves.nombreRonda(3, 6))
        assertEquals("Dieciseisavos de final", Llaves.nombreRonda(2, 6))
        assertEquals("Treintaidosavos de final", Llaves.nombreRonda(1, 6))
        assertEquals("Ronda 1", Llaves.nombreRonda(1, 8))
    }

    @Test
    fun `los partidos se reparten por turnos entre las canchas`() {
        assertEquals(
            listOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 1 to 2, 2 to 2),
            Llaves.repartoCanchas(6, 4),
        )
        assertEquals(listOf(1 to 1, 2 to 1, 1 to 2), Llaves.repartoCanchas(3, 2))
        assertTrue(Llaves.repartoCanchas(0, 4).isEmpty())
    }
}
