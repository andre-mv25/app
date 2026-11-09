package com.utc.guerrarobots.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Prepara la foto del robot: la lee de la galería, la endereza según como se tomó, la reduce
 * (lado más largo de 960 px) y la comprime a JPEG hasta que pese unos 250 KB. Así se guarda en
 * Firestore sin problema y sube rápido aunque el internet sea lento.
 */
object FotoRobot {
    private const val LADO_MAX = 960
    private const val BYTES_OBJETIVO = 250_000

    /** Devuelve el JPEG listo, o null si no se pudo leer la imagen. */
    suspend fun preparar(contexto: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val resolver = contexto.contentResolver

            // 1) Solo las medidas, para no cargar una foto enorme completa en memoria.
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return@withContext null

            // 2) Se lee ya reducida a un tamaño cercano al final.
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO_MAX) muestra *= 2
            val lectura = BitmapFactory.Options().apply { inSampleSize = muestra }
            val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, lectura) }
                ?: return@withContext null

            // 3) Se endereza (muchas fotos del celular vienen giradas) y se ajusta al lado máximo.
            val giro = resolver.openInputStream(uri)?.use { leerGiro(it) } ?: 0
            val escala = minOf(1f, LADO_MAX.toFloat() / maxOf(original.width, original.height))
            val matriz = Matrix().apply {
                postScale(escala, escala)
                postRotate(giro.toFloat())
            }
            val listo = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)

            // 4) Se comprime bajando la calidad hasta que pese lo esperado.
            var calidad = 85
            var bytes = ByteArray(0)
            do {
                val salida = ByteArrayOutputStream()
                listo.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                bytes = salida.toByteArray()
                calidad -= 10
            } while (bytes.size > BYTES_OBJETIVO && calidad >= 35)
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun leerGiro(entrada: java.io.InputStream): Int =
        when (ExifInterface(entrada).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
