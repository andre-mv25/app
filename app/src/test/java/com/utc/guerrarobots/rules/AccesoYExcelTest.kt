package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.AdminsLista
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.CategoriasBase
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.JuradosLista
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.model.Robot
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.PinStore
import com.utc.guerrarobots.domain.service.AccesoService
import com.utc.guerrarobots.ui.ExcelCompetencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccesoYExcelTest {

    private class MemoriaPinStore : PinStore {
        private val mapa = mutableMapOf<Rol, String>()
        override fun obtenerHash(rol: Rol): String? = mapa[rol]
        override fun guardarHash(rol: Rol, hash: String) { mapa[rol] = hash }
    }

    @Test
    fun admin_puedeEntrarComoAdminYComoJurado() {
        val servicio = AccesoService(MemoriaPinStore())

        // Jose Guadalupe (PIN: 1234567)
        assertTrue("Jose Guadalupe debe poder entrar como ADMIN", servicio.entrar(Rol.ADMIN, "1234567"))
        assertEquals("Jose Guadalupe", servicio.nombreAdminActual)
        assertEquals(Rol.ADMIN, servicio.rolActual)

        servicio.salir()

        assertTrue("Jose Guadalupe debe poder entrar como JURADO", servicio.entrar(Rol.JURADO, "1234567"))
        assertEquals("Jose Guadalupe", servicio.nombreAdminActual)
        assertEquals(Rol.JURADO, servicio.rolActual)

        servicio.salir()

        // Delia Azucena Lienzo (PIN: 7654321)
        assertTrue("Delia debe poder entrar como ADMIN", servicio.entrar(Rol.ADMIN, "7654321"))
        assertEquals("Delia Azucena Lienzo", servicio.nombreAdminActual)

        servicio.salir()

        assertTrue("Delia debe poder entrar como JURADO", servicio.entrar(Rol.JURADO, "7654321"))
        assertEquals("Delia Azucena Lienzo", servicio.nombreAdminActual)
    }

    @Test
    fun jurado_soloPuedeEntrarComoJurado() {
        val servicio = AccesoService(MemoriaPinStore())

        // Francisco Javier (PIN: 2100001)
        assertFalse("Un jurado NO debe poder entrar como ADMIN", servicio.entrar(Rol.ADMIN, "2100001"))

        assertTrue("Un jurado debe poder entrar como JURADO", servicio.entrar(Rol.JURADO, "2100001"))
        assertEquals("Francisco Javier", servicio.nombreAdminActual)
        assertEquals(Rol.JURADO, servicio.rolActual)

        servicio.salir()

        // Alejandra Victoria Vega García (PIN: 2100021)
        assertFalse("Un jurado NO debe poder entrar como ADMIN", servicio.entrar(Rol.ADMIN, "2100021"))
        assertTrue("Un jurado debe poder entrar como JURADO", servicio.entrar(Rol.JURADO, "2100021"))
        assertEquals("Alejandra Victoria Vega García", servicio.nombreAdminActual)
    }

    @Test
    fun pinIncorrecto_esRechazado() {
        val servicio = AccesoService(MemoriaPinStore())
        assertFalse(servicio.entrar(Rol.ADMIN, "9999999"))
        assertFalse(servicio.entrar(Rol.JURADO, "0000000"))
    }

    @Test
    fun totalDeAdminsYJurados_esCorrecto() {
        assertEquals("Debe haber exactamente 2 administradores oficiales", 2, AdminsLista.todos.size)
        assertEquals("Debe haber exactamente 21 jurados oficiales", 21, JuradosLista.todos.size)

        // Verificar que ningún PIN se repita
        val todosLosPins = AdminsLista.todos.map { it.pin } + JuradosLista.todos.map { it.pin }
        assertEquals("Todos los PINs deben ser únicos", todosLosPins.size, todosLosPins.toSet().size)
    }

    @Test
    fun excelCompetencias_generaHtmlCorrectamenteConJuezYGanador() {
        val categorias = CategoriasBase.porDefecto
        val eq1 = Equipo(
            id = 1L, folio = "GR-0001", nombre = "Titanes", capitan = "Carlos",
            plantel = "Toluca", jefeCarrera = "Prof. Martinez",
            integrantes = listOf("Carlos", "Luis"),
            robot = Robot("Destructor", ClaveCategoria.COMBATE, 2000, 30.0),
            asistencia = true, horaLlegada = 1700000000000L, adminRegistro = "Jose Guadalupe",
        )
        val eq2 = Equipo(
            id = 2L, folio = "GR-0002", nombre = "CyberRobots", capitan = "Ana",
            plantel = "Ecatepec", jefeCarrera = "Prof. Gómez",
            integrantes = listOf("Ana", "Pedro"),
            robot = Robot("Fénix", ClaveCategoria.COMBATE, 1900, 28.0),
            asistencia = true, horaLlegada = 1700000500000L, adminRegistro = "Delia Azucena Lienzo",
        )

        val partida = Partida(
            id = 10L, categoria = ClaveCategoria.COMBATE, ronda = 1, posicion = 1,
            cancha = 1, equipoAId = 1L, equipoBId = 2L, estado = EstadoPartida.FINALIZADA,
            ganadorId = 1L, finalizadaEn = 1700001000000L,
        )

        val auditoria = listOf(
            EventoAuditoria(
                id = 100L, actor = "Francisco Javier", accion = "PARTIDA_FINALIZADA",
                entidad = "partida", entidadId = 10L, detalle = "Ganador Titanes",
                creadoEn = 1700001000000L,
            ),
        )

        val html = ExcelCompetencias.generarExcelHtml(
            categorias = categorias,
            equipos = listOf(eq1, eq2),
            partidas = listOf(partida),
            intentos = emptyList(),
            auditoria = auditoria,
        )

        assertNotNull(html)
        assertTrue("El HTML debe contener el título de reporte", html.contains("REPORTE OFICIAL DE COMPETENCIAS Y JUECES"))
        assertTrue("Debe mostrar el nombre del ganador", html.contains("Titanes"))
        assertTrue("Debe mostrar el nombre del juez responsable", html.contains("Francisco Javier"))
        assertTrue("Debe mostrar la hora de llegada y admin de registro", html.contains("Jose Guadalupe"))
        assertTrue("Debe mostrar la segunda competidora y admin", html.contains("Delia Azucena Lienzo"))
    }
}
