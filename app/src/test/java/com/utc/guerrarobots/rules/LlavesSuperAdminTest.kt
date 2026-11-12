package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.AdminsLista
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.PinStore
import com.utc.guerrarobots.domain.service.AccesoService
import com.utc.guerrarobots.domain.service.LlavesService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlavesSuperAdminTest {

    private class MemoriaPinStore : PinStore {
        private val mapa = mutableMapOf<Pair<String, Rol>, String>()
        override fun obtenerHash(correo: String, rol: Rol): String? = mapa[correo to rol]
        override fun guardarHash(correo: String, rol: Rol, hash: String) { mapa[correo to rol] = hash }
        override fun borrarHash(correo: String, rol: Rol) { mapa.remove(correo to rol) }
    }

    @Test
    fun joseGuadalupe_esSuperAdminYPuedeEntrarConSuPinExclusivo() {
        val servicio = AccesoService(MemoriaPinStore())

        // Su PIN de Admin (1234567) NO debe darle acceso como SUPER_ADMIN
        assertFalse("El PIN de Admin no debe dar rol SUPER_ADMIN", servicio.entrar(Rol.SUPER_ADMIN, "1234567"))

        // Su PIN dedicado de Super Admin (7777777) debe dar rol SUPER_ADMIN
        assertTrue("Jose Guadalupe debe poder entrar como SUPER_ADMIN con PIN 7777777", servicio.entrar(Rol.SUPER_ADMIN, "7777777"))
        assertEquals("Jose Guadalupe", servicio.nombreActual)
        assertEquals(Rol.SUPER_ADMIN, servicio.rolActual)
        assertTrue(servicio.puede(Rol.SUPER_ADMIN))
        assertTrue(servicio.puede(Rol.ADMIN))
        assertTrue(servicio.puede(Rol.JURADO))
    }

    @Test
    fun adminNormal_noPuedeEntrarComoSuperAdmin() {
        val servicio = AccesoService(MemoriaPinStore())

        // Delia (PIN: 7654321) es Admin, pero NO Super Admin
        assertFalse("Delia NO debe poder entrar como SUPER_ADMIN", servicio.entrar(Rol.SUPER_ADMIN, "7654321"))
    }

    @Test
    fun validarSinCrucesMismoPlantel_detectaEnfrentamientoMismoPlantel() {
        val plantelDe = mapOf(
            1L to "Toluca",
            2L to "Toluca",
            3L to "Ecatepec",
            4L to "Ecatepec",
        )

        // Orden donde 1 (Toluca) se enfrenta a 2 (Toluca) en el primer cruce de siembra [1 vs 4, 2 vs 3] -> orden (1, 3, 2, 4)
        // Para 4 equipos, la siembra es 1, 4, 2, 3 (cruce 1: pos 0 y pos 1 -> orden[0] vs orden[3]; cruce 2: orden[1] vs orden[2])
        // Si orden = [1, 3, 2, 2...], pongamos orden = [1, 2, 3, 4] -> cruce 1: 1 (Toluca) vs 4 (Ecatepec), cruce 2: 2 (Toluca) vs 3 (Ecatepec) -> OK
        // Si orden = [1, 3, 4, 2] -> cruce 1: 1 (Toluca) vs 2 (Toluca) -> INVALIDO!
        val invalidOrden = listOf(1L, 3L, 4L, 2L)
        val error = LlavesService(
            equipos = mockEquipoRepo(),
            partidas = mockPartidaRepo(),
            sorteos = mockSorteoRepo(),
            catalogo = mockCatalogoRepo(),
            tx = mockTx(),
            reloj = { 0L },
        ).validarSinCrucesMismoPlantel(invalidOrden, plantelDe)

        assertNotNull("Debe detectar que 1 y 2 pertenecen a Toluca", error)
        assertTrue(error!!.contains("Toluca"))
    }

    @Test
    fun validarSinCrucesMismoPlantel_aceptaOrdenValido() {
        val plantelDe = mapOf(
            1L to "Toluca",
            2L to "Toluca",
            3L to "Ecatepec",
            4L to "Ecatepec",
        )
        val validOrden = listOf(1L, 2L, 3L, 4L)
        val error = LlavesService(
            equipos = mockEquipoRepo(),
            partidas = mockPartidaRepo(),
            sorteos = mockSorteoRepo(),
            catalogo = mockCatalogoRepo(),
            tx = mockTx(),
            reloj = { 0L },
        ).validarSinCrucesMismoPlantel(validOrden, plantelDe)

        assertNull("No debe haber error en una distribución válida", error)
    }

    // Mocks mínimos para instanciar LlavesService en el test de regla pura
    private fun mockEquipoRepo(): com.utc.guerrarobots.domain.repository.EquipoRepository = object : com.utc.guerrarobots.domain.repository.EquipoRepository {
        override suspend fun insertar(equipo: com.utc.guerrarobots.domain.model.Equipo): Long = 0L
        override suspend fun obtener(id: Long): com.utc.guerrarobots.domain.model.Equipo? = null
        override suspend fun listar(estado: com.utc.guerrarobots.domain.model.EstadoEquipo?, categoria: com.utc.guerrarobots.domain.model.ClaveCategoria?): List<com.utc.guerrarobots.domain.model.Equipo> = emptyList()
        override suspend fun existeNombreEquipo(nombre: String): Boolean = false
        override suspend fun existeNombreRobot(nombre: String): Boolean = false
        override suspend fun actualizarEstado(id: Long, estado: com.utc.guerrarobots.domain.model.EstadoEquipo) {}
        override suspend fun actualizarFolio(id: Long, folio: String) {}
        override suspend fun actualizarLogo(id: Long, logoUri: String?) {}
        override suspend fun registrarAsistencia(id: Long, presente: Boolean, horaMs: Long?, adminNombre: String?) {}
        override suspend fun eliminar(id: Long) {}
    }
    private fun mockPartidaRepo(): com.utc.guerrarobots.domain.repository.PartidaRepository = object : com.utc.guerrarobots.domain.repository.PartidaRepository {
        override suspend fun insertar(partida: com.utc.guerrarobots.domain.model.Partida): Long = 0L
        override suspend fun obtener(id: Long): com.utc.guerrarobots.domain.model.Partida? = null
        override suspend fun listar(categoria: com.utc.guerrarobots.domain.model.ClaveCategoria, estado: com.utc.guerrarobots.domain.model.EstadoPartida?): List<com.utc.guerrarobots.domain.model.Partida> = emptyList()
        override suspend fun actualizar(partida: com.utc.guerrarobots.domain.model.Partida) {}
        override suspend fun eliminarPorCategoria(categoria: com.utc.guerrarobots.domain.model.ClaveCategoria) {}
    }
    private fun mockSorteoRepo(): com.utc.guerrarobots.domain.repository.SorteoRepository = object : com.utc.guerrarobots.domain.repository.SorteoRepository {
        override suspend fun insertar(sorteo: com.utc.guerrarobots.domain.model.Sorteo): Long = 0L
        override suspend fun ultimo(categoria: com.utc.guerrarobots.domain.model.ClaveCategoria): com.utc.guerrarobots.domain.model.Sorteo? = null
    }
    private fun mockCatalogoRepo(): com.utc.guerrarobots.domain.repository.CatalogoRepository = object : com.utc.guerrarobots.domain.repository.CatalogoRepository {
        override suspend fun planteles(): List<com.utc.guerrarobots.domain.model.Plantel> = emptyList()
        override suspend fun agregarPlantel(nombre: String): com.utc.guerrarobots.domain.model.Plantel = com.utc.guerrarobots.domain.model.Plantel(1L, nombre)
        override suspend fun categorias(): List<com.utc.guerrarobots.domain.model.Categoria> = emptyList()
        override suspend fun categoria(clave: com.utc.guerrarobots.domain.model.ClaveCategoria): com.utc.guerrarobots.domain.model.Categoria = com.utc.guerrarobots.domain.model.Categoria(clave)
        override suspend fun guardarCategoria(categoria: com.utc.guerrarobots.domain.model.Categoria) {}
        override suspend fun palabrasBloqueadas(): Set<String> = emptySet()
        override suspend fun agregarPalabraBloqueada(normalizada: String) {}
    }
    private fun mockTx(): com.utc.guerrarobots.domain.repository.Transaccion = object : com.utc.guerrarobots.domain.repository.Transaccion {
        override suspend fun <T> ejecutar(bloque: suspend () -> T): T = bloque()
    }
}
