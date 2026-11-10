package com.utc.guerrarobots.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.rules.CodigoRegistro

object QrBitmap {
    /** El QR en negro sobre blanco, con su margen, como lo necesitan los lectores. */
    fun generar(texto: String, lado: Int = 720): Bitmap {
        val pistas = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 3,
        )
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado, pistas)
        val ancho = matriz.width
        val alto = matriz.height
        val pixeles = IntArray(ancho * alto)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                pixeles[y * ancho + x] = if (matriz[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
    }

    /** Guarda el QR en Imágenes/GuerraRobots. Solo en Android 10 o más nuevo (sin pedir permisos). */
    fun guardarEnGaleria(contexto: Context, qr: Bitmap, nombre: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val valores = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$nombre.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GuerraRobots")
            }
            val resolver = contexto.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            if (uri == null) {
                false
            } else {
                resolver.openOutputStream(uri)?.use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}

/** Muestra un QR ya generado. */
@Composable
fun QrImagen(qr: Bitmap, tamano: Dp = 280.dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = qr.asImageBitmap(),
        contentDescription = "Código QR del registro",
        modifier = modifier.size(tamano).clip(RoundedCornerShape(12.dp)),
    )
}

/** El QR de un equipo: lleva su folio y los datos de su registro. */
@Composable
fun QrRegistro(equipo: Equipo, nombreCategoria: String, tamano: Dp = 280.dp, modifier: Modifier = Modifier) {
    val qr = remember(equipo, nombreCategoria) {
        QrBitmap.generar(CodigoRegistro.texto(equipo, nombreCategoria))
    }
    QrImagen(qr, tamano, modifier)
}
