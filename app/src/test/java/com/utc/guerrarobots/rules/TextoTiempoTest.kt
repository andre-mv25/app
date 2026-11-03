package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.rules.Texto
import com.utc.guerrarobots.domain.rules.Tiempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoTest {
    private val bloqueadas = setOf("puto", "pendejo", "puta")

    @Test
    fun `normalizar quita acentos mayusculas y espacios repetidos`() {
        assertEquals("arbol rojo", Texto.normalizar("  ÁrBol   Rojo "))
        assertEquals("canon", Texto.normalizar("Cañón"))
    }

    @Test
    fun `detecta palabras bloqueadas sin importar mayusculas`() {
        assertTrue(Texto.contieneBloqueada("PUTO el que lea", bloqueadas))
        assertTrue(Texto.contieneBloqueada("Super Pendejo", bloqueadas))
    }

    @Test
    fun `detecta sustituciones de numeros y simbolos`() {
        assertTrue(Texto.contieneBloqueada("p3nd3j0", bloqueadas))
        assertTrue(Texto.contieneBloqueada("pu7o", bloqueadas))
    }

    @Test
    fun `detecta letras separadas y plurales simples`() {
        assertTrue(Texto.contieneBloqueada("p u t o", bloqueadas))
        assertTrue(Texto.contieneBloqueada("Los Putos", bloqueadas))
    }

    @Test
    fun `no marca palabras normales que contienen una bloqueada`() {
        assertFalse(Texto.contieneBloqueada("Computadora", bloqueadas))
        assertFalse(Texto.contieneBloqueada("Robot 1", bloqueadas))
        assertFalse(Texto.contieneBloqueada("Los Tornillos", bloqueadas))
    }

    @Test
    fun `sin palabras bloqueadas todo pasa`() {
        assertFalse(Texto.contieneBloqueada("cualquier cosa", emptySet()))
    }
}

class TiempoTest {
    @Test
    fun `convierte minutos segundos y milesimas a milisegundos`() {
        assertEquals(83_456L, Tiempo.aMilisegundos(1, 23, 456))
        assertEquals(0L, Tiempo.aMilisegundos(0, 0, 0))
        assertEquals(599_999L, Tiempo.aMilisegundos(9, 59, 999))
    }

    @Test
    fun `rechaza tiempos fuera de rango`() {
        assertFalse(Tiempo.esValido(0, 60, 0))
        assertFalse(Tiempo.esValido(0, 0, 1000))
        assertFalse(Tiempo.esValido(-1, 0, 0))
        assertFalse(Tiempo.esValido(0, -1, 0))
        assertThrows(IllegalArgumentException::class.java) { Tiempo.aMilisegundos(0, 60, 0) }
    }

    @Test
    fun `formatea como minutos segundos y milesimas`() {
        assertEquals("01:23.456", Tiempo.formatear(83_456))
        assertEquals("00:00.000", Tiempo.formatear(0))
        assertEquals("10:05.007", Tiempo.formatear(605_007))
    }
}
