package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.rules.Ranking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    private fun valido(equipo: Long, numero: Int, ms: Long) = Intento(
        categoria = ClaveCategoria.SIGUE_LINEAS, equipoId = equipo, numero = numero,
        tiempoMs = ms, resultado = ResultadoIntento.VALIDO, capturadoEn = 0,
    )

    private fun otro(equipo: Long, numero: Int, resultado: ResultadoIntento) = Intento(
        categoria = ClaveCategoria.SIGUE_LINEAS, equipoId = equipo, numero = numero,
        resultado = resultado, motivo = if (resultado == ResultadoIntento.DESCALIFICADO) "salida falsa" else null,
        capturadoEn = 0,
    )

    @Test
    fun `ejemplo del diseno con cinco equipos`() {
        // B=2, D=4, A=1, C=3, E=5
        val intentos = listOf(
            valido(2, 1, 78_350), valido(2, 2, 79_900),
            valido(4, 1, 78_350), valido(4, 2, 82_400),
            valido(1, 1, 79_000), otro(1, 2, ResultadoIntento.NO_TERMINO),
            valido(3, 1, 80_500), valido(3, 2, 81_000),
            otro(5, 1, ResultadoIntento.NO_TERMINO), otro(5, 2, ResultadoIntento.DESCALIFICADO),
        )
        val filas = Ranking.calcular(intentos)

        assertEquals(listOf(2L, 4L, 1L, 3L, 5L), filas.map { it.equipoId })
        assertEquals(listOf(1, 2, 3, 4, 5), filas.map { it.lugar })
        // B y D empatan en el mejor tiempo, pero el segundo tiempo los separa: no hay carrera extra.
        assertTrue(filas.none { it.empate })
        assertEquals(78_350L, filas[0].mejorMs)
        assertEquals(79_900L, filas[0].segundoMs)
        assertNull(filas[2].segundoMs)
        assertTrue(filas[4].sinTiempo)
        assertNull(filas[4].mejorMs)
    }

    @Test
    fun `empate total comparte lugar y pide carrera extra`() {
        val intentos = listOf(
            valido(1, 1, 70_000), valido(1, 2, 71_000),
            valido(2, 1, 70_000), valido(2, 2, 71_000),
            valido(3, 1, 72_000), valido(3, 2, 73_000),
        )
        val filas = Ranking.calcular(intentos).associateBy { it.equipoId }
        assertEquals(1, filas.getValue(1).lugar)
        assertEquals(1, filas.getValue(2).lugar)
        assertTrue(filas.getValue(1).empate)
        assertTrue(filas.getValue(2).empate)
        // Los lugares siguen como en el deporte: 1, 1, 3.
        assertEquals(3, filas.getValue(3).lugar)
        assertFalse(filas.getValue(3).empate)
    }

    @Test
    fun `quien tiene segundo tiempo gana el desempate a quien no lo tiene`() {
        val intentos = listOf(
            valido(1, 1, 70_000), otro(1, 2, ResultadoIntento.NO_TERMINO),
            valido(2, 1, 70_000), valido(2, 2, 99_000),
        )
        val filas = Ranking.calcular(intentos)
        assertEquals(listOf(2L, 1L), filas.map { it.equipoId })
        assertEquals(listOf(1, 2), filas.map { it.lugar })
    }

    @Test
    fun `dos equipos con un solo intento igual empatan`() {
        val intentos = listOf(valido(1, 1, 65_000), valido(2, 1, 65_000))
        val filas = Ranking.calcular(intentos)
        assertEquals(listOf(1, 1), filas.map { it.lugar })
        assertTrue(filas.all { it.empate })
    }

    @Test
    fun `solo cuenta el mejor tiempo aunque el primer intento sea el peor`() {
        val intentos = listOf(valido(1, 1, 90_000), valido(1, 2, 60_000), valido(2, 1, 70_000))
        val filas = Ranking.calcular(intentos)
        assertEquals(1L, filas[0].equipoId)
        assertEquals(60_000L, filas[0].mejorMs)
        assertEquals(90_000L, filas[0].segundoMs)
    }

    @Test
    fun `si nadie tiene tiempo valido todos quedan en el lugar uno`() {
        val intentos = listOf(
            otro(1, 1, ResultadoIntento.NO_TERMINO),
            otro(2, 1, ResultadoIntento.DESCALIFICADO),
        )
        val filas = Ranking.calcular(intentos)
        assertEquals(listOf(1, 1), filas.map { it.lugar })
        assertTrue(filas.all { it.sinTiempo && !it.empate })
    }

    @Test
    fun `los intentos de cada fila salen ordenados por numero`() {
        val intentos = listOf(valido(1, 2, 60_000), valido(1, 1, 90_000))
        val fila = Ranking.calcular(intentos).single()
        assertEquals(listOf(1, 2), fila.intentos.map { it.numero })
    }

    @Test
    fun `sin intentos no hay filas`() {
        assertTrue(Ranking.calcular(emptyList()).isEmpty())
    }
}
