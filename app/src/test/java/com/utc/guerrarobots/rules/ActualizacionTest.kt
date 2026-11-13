package com.utc.guerrarobots.rules

import com.utc.guerrarobots.domain.model.InfoActualizacion
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.repository.ActualizacionRepository
import com.utc.guerrarobots.domain.service.ActualizacionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualizacionTest {

    private class MemoriaActualizacionRepo(var config: InfoActualizacion? = null) : ActualizacionRepository {
        override suspend fun obtenerConfiguracion(): InfoActualizacion? = config
        override suspend fun guardarConfiguracion(info: InfoActualizacion) {
            config = info
        }
    }

    @Test
    fun comprobar_detectaNuevaVersionCuandoVersionCodeEsMayor() = runBlocking {
        val repo = MemoriaActualizacionRepo(
            InfoActualizacion(
                versionCode = 2,
                versionName = "2.0",
                urlDescarga = "https://guerrarobots.web.app/releases/app-release.apk",
                novedades = "Nuevas funciones de super admin",
            )
        )
        val servicio = ActualizacionService(repo)

        val actualizacion = servicio.comprobar(versionCodeActual = 1)
        assertNotNull(actualizacion)
        assertEquals(2, actualizacion?.versionCode)
        assertEquals("2.0", actualizacion?.versionName)
    }

    @Test
    fun comprobar_ignoraCuandoVersionCodeEsIgualOMenor() = runBlocking {
        val repo = MemoriaActualizacionRepo(
            InfoActualizacion(
                versionCode = 1,
                versionName = "1.0",
                urlDescarga = "https://guerrarobots.web.app/releases/app-release.apk",
            )
        )
        val servicio = ActualizacionService(repo)

        val actualizacion = servicio.comprobar(versionCodeActual = 1)
        assertNull(actualizacion)
    }

    @Test
    fun guardarConfiguracion_validaDatosRequeridos() = runBlocking {
        val repo = MemoriaActualizacionRepo()
        val servicio = ActualizacionService(repo)

        // URL inválida
        val r1 = servicio.guardarConfiguracion(InfoActualizacion(versionCode = 2, urlDescarga = "invalid_url"))
        assertTrue(r1 is Resultado.Falla)

        // Configuración válida
        val r2 = servicio.guardarConfiguracion(
            InfoActualizacion(
                versionCode = 2,
                versionName = "2.0",
                urlDescarga = "https://guerrarobots.web.app/releases/v2.apk",
            )
        )
        assertTrue(r2 is Resultado.Ok)
        assertEquals(2, repo.config?.versionCode)
    }
}
