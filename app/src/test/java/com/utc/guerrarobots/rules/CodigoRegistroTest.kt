package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Robot
import com.utc.guerrarobots.domain.rules.CodigoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigoRegistroTest {

    private fun equipo(folio: String, integrantes: List<String> = listOf("Luis Pérez")) = Equipo(
        id = 7,
        folio = folio,
        nombre = "Los Rayos",
        capitan = "Ana Gómez",
        plantel = "Toluca",
        jefeCarrera = "Ing. Ramírez",
        estado = EstadoEquipo.PENDIENTE,
        integrantes = integrantes,
        robot = Robot("Rayito", ClaveCategoria.SIGUE_LINEAS, 1500, 18.5),
    )

    @Test
    fun `el texto trae el folio y los datos del registro`() {
        val texto = CodigoRegistro.texto(equipo("GR-0007"), "Sigue líneas")
        listOf(
            "Folio: GR-0007", "Equipo: Los Rayos", "Robot: Rayito", "Categoría: Sigue líneas",
            "Plantel: Toluca", "Capitán: Ana Gómez", "Participantes: Luis Pérez", "Jefe de carrera: Ing. Ramírez",
        ).forEach { assertTrue("falta \"$it\"", texto.contains(it)) }
    }

    @Test
    fun `cada folio da un codigo distinto`() {
        val a = CodigoRegistro.texto(equipo("GR-0007"), "Sigue líneas")
        val b = CodigoRegistro.texto(equipo("GR-0008"), "Sigue líneas")
        assertNotEquals(a, b)
    }

    @Test
    fun `el mismo registro siempre da el mismo codigo`() {
        assertEquals(
            CodigoRegistro.texto(equipo("GR-0007"), "Sigue líneas"),
            CodigoRegistro.texto(equipo("GR-0007"), "Sigue líneas"),
        )
    }

    @Test
    fun `sin otros participantes no sale esa linea`() {
        val texto = CodigoRegistro.texto(equipo("GR-0007", integrantes = emptyList()), "Sigue líneas")
        assertFalse(texto.contains("Participantes:"))
    }

    @Test
    fun `una lista larga de participantes se recorta para que el QR no se sature`() {
        val muchos = List(12) { "Participante con nombre bastante largo número $it" }
        val texto = CodigoRegistro.texto(equipo("GR-0007", muchos), "Fútbol robot")
        val linea = texto.lines().first { it.startsWith("Participantes:") }
        assertTrue(linea.length <= "Participantes: ".length + 120)
        assertTrue(linea.endsWith("…"))
    }
}
