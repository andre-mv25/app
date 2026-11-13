package com.utc.guerrarobots.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.InfoActualizacion
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.ActualizacionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servicio encargado de comprobar si existe una nueva versión de la app en Firestore,
 * descargar el archivo .apk desde Firebase Hosting u otra URL pública e invocar el
 * instalador nativo del sistema Android.
 */
class ActualizacionService(
    private val repo: ActualizacionRepository,
    private val auditoria: AuditoriaService? = null,
) {
    /** Verifica si hay una versión con versionCode superior al [versionCodeActual]. */
    suspend fun comprobar(versionCodeActual: Int): InfoActualizacion? {
        val config = runCatching { repo.obtenerConfiguracion() }.getOrNull() ?: return null
        if (config.versionCode > versionCodeActual && config.urlDescarga.isNotBlank()) {
            return config
        }
        return null
    }

    /** Permite al Admin o Super Admin consultar la configuración guardada de actualización. */
    suspend fun obtenerConfiguracion(): InfoActualizacion {
        return runCatching { repo.obtenerConfiguracion() }.getOrNull() ?: InfoActualizacion()
    }

    /** Guarda la configuración de la versión disponible en Firestore. */
    suspend fun guardarConfiguracion(info: InfoActualizacion): Resultado<Unit> {
        if (info.versionCode < 1) {
            return falla(Codigo.VALIDACION, "El versionCode debe ser mayor a 0", "versionCode")
        }
        if (info.urlDescarga.isBlank() || (!info.urlDescarga.startsWith("http://") && !info.urlDescarga.startsWith("https://"))) {
            return falla(Codigo.VALIDACION, "Ingresa una URL válida de descarga (http:// o https://)", "urlDescarga")
        }
        return try {
            repo.guardarConfiguracion(info)
            auditoria?.registrar(
                Acciones.ACTUALIZACION_PUBLICADA,
                "config",
                null,
                "Versión v${info.versionName} (${info.versionCode}) publicada",
            )
            Resultado.Ok(Unit)
        } catch (e: Exception) {
            falla(Codigo.CONFLICTO, "No se pudo publicar la actualización: ${e.message}")
        }
    }

    /**
     * Descarga el archivo APK desde [urlDescarga] e invoca el instalador del sistema.
     * Reporta el porcentaje de progreso (0..100) mediante el callback [onProgreso].
     */
    suspend fun descargarEInstalarApk(
        contexto: Context,
        urlDescarga: String,
        onProgreso: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlDescarga)
            val conexion = url.openConnection() as HttpURLConnection
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 30_000
            conexion.instanceFollowRedirects = true
            conexion.connect()

            if (conexion.responseCode != HttpURLConnection.HTTP_OK) {
                error("Respuesta del servidor no válida (${conexion.responseCode})")
            }

            val tamanioTotal = conexion.contentLength
            val carpeta = contexto.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: contexto.cacheDir
            val archivoApk = File(carpeta, "GuerraRobots-update.apk")
            if (archivoApk.exists()) {
                archivoApk.delete()
            }

            conexion.inputStream.use { input ->
                archivoApk.outputStream().use { output ->
                    val buffer = ByteArray(8_192)
                    var leidos: Int
                    var acumulado = 0L
                    while (input.read(buffer).also { leidos = it } != -1) {
                        output.write(buffer, 0, leidos)
                        acumulado += leidos
                        if (tamanioTotal > 0) {
                            val porcentaje = ((acumulado * 100) / tamanioTotal).toInt()
                            onProgreso(porcentaje.coerceIn(0, 100))
                        }
                    }
                }
            }

            // Invocar el instalador de paquetes de Android con FileProvider
            val contentUri: Uri = FileProvider.getUriForFile(
                contexto,
                "${contexto.packageName}.fileprovider",
                archivoApk,
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            contexto.startActivity(intent)
        }
    }
}
