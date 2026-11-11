package com.utc.guerrarobots.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.utc.guerrarobots.domain.model.Equipo
import java.util.concurrent.Executors

/**
 * Diálogo de escaneo QR con la cámara del dispositivo, exclusivo para administradores.
 * Detecta en tiempo real el código QR de un equipo y registra su asistencia en el Excel / Checklist.
 */
@Composable
fun EscanerQrAdminDialog(
    esAdmin: Boolean,
    equipos: List<Equipo>,
    onRegistrarAsistencia: (Equipo) -> Unit,
    onCerrar: () -> Unit,
) {
    if (!esAdmin) return

    val contexto = LocalContext.current
    var tienePermisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcherPermiso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        tienePermisoCamara = concedido
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoCamara) {
            launcherPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    var ultimoEscaneado by remember { mutableStateOf<Equipo?>(null) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var textoManual by remember { mutableStateOf("") }

    fun procesarTextoQr(rawTexto: String) {
        val folio = extraerFolio(rawTexto)
        val equipo = equipos.firstOrNull { e ->
            e.folio.equals(folio, ignoreCase = true) || rawTexto.contains(e.folio, ignoreCase = true)
        }

        if (equipo != null) {
            mensajeError = null
            ultimoEscaneado = equipo
            onRegistrarAsistencia(equipo)
        } else {
            mensajeError = "No se encontró ningún equipo con Folio o QR: $folio"
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                "📷 Escáner QR de Asistencia (ADMIN)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ultimoEscaneado != null) {
                    val eq = ultimoEscaneado!!
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "✓ ¡ASISTENCIA REGISTRADA EN EXCEL!",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Folio: ${eq.folio}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Equipo: ${eq.nombre}", style = MaterialTheme.typography.bodyMedium)
                            Text("Robot: ${eq.robot.nombre}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = { ultimoEscaneado = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📷 Escanear siguiente equipo")
                    }
                } else if (tienePermisoCamara) {
                    Text(
                        "Apunta con la cámara al código QR del competidor para registrar su llegada en tiempo real:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        VistaCamaraPreview(onQrDetectado = { raw ->
                            procesarTextoQr(raw)
                        })

                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .border(2.dp, Color.Green.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                        )
                    }
                } else {
                    Text(
                        "Se requiere permiso de cámara para escanear los QR automáticamente. También puedes ingresar el folio manualmente:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { launcherPermiso.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder permiso de Cámara")
                    }
                }

                mensajeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Opción rápida manual o con lector externo
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("O escanear/pegar Folio manualmente:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = textoManual,
                            onValueChange = { textoManual = it },
                            placeholder = { Text("Ej. GR-0001") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                if (textoManual.isNotBlank()) {
                                    procesarTextoQr(textoManual)
                                    textoManual = ""
                                }
                            },
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) {
                Text("Cerrar Escáner")
            }
        },
    )
}

@Composable
private fun VistaCamaraPreview(onQrDetectado: (String) -> Unit) {
    val contexto = LocalContext.current
    val cicloVida = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(contexto) }

    LaunchedEffect(Unit) {
        val ejecutor = Executors.newSingleThreadExecutor()
        val proveedorFuturo = ProcessCameraProvider.getInstance(contexto)
        proveedorFuturo.addListener({
            try {
                val proveedor = proveedorFuturo.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analisis.setAnalyzer(ejecutor, QrAnalyzer(onQrDetectado))

                val selectorCamara = CameraSelector.DEFAULT_BACK_CAMERA

                proveedor.unbindAll()
                proveedor.bindToLifecycle(
                    cicloVida,
                    selectorCamara,
                    preview,
                    analisis,
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(contexto))
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

private class QrAnalyzer(private val onQrDetectado: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val lector = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    @Volatile
    private var procesando = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (procesando) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null && mediaImage.format == ImageFormat.YUV_420_888) {
            val buffer = mediaImage.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val fuente = PlanarYUVLuminanceSource(
                bytes,
                mediaImage.width,
                mediaImage.height,
                0,
                0,
                mediaImage.width,
                mediaImage.height,
                false,
            )
            val mapaBinario = BinaryBitmap(HybridBinarizer(fuente))
            try {
                val resultado = lector.decodeWithState(mapaBinario)
                val texto = resultado.text
                if (!texto.isNullOrBlank()) {
                    procesando = true
                    onQrDetectado(texto)
                }
            } catch (_: Exception) {
                // No se encontró código QR válido en este frame
            } finally {
                lector.reset()
            }
        }
        imageProxy.close()
    }
}

private fun extraerFolio(rawTexto: String): String {
    val regex = Regex("(?:Folio:\\s*|^)(GR-\\d+|[A-Za-z0-9-]+)", RegexOption.IGNORE_CASE)
    val match = regex.find(rawTexto)
    if (match != null) {
        return match.groupValues.getOrNull(1)?.trim() ?: match.value.trim()
    }
    return rawTexto.trim()
}
