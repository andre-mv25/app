package com.utc.guerrarobots.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utc.guerrarobots.domain.model.InfoActualizacion
import com.utc.guerrarobots.domain.service.ActualizacionService
import kotlinx.coroutines.launch

/**
 * Diálogo flotante en Compose que notifica una nueva versión disponible,
 * muestra las novedades y permite descargar e instalar la APK mediante [servicio].
 */
@Composable
fun DialogoActualizacion(
    info: InfoActualizacion,
    servicio: ActualizacionService,
    onCerrar: () -> Unit,
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    var descargando by remember { mutableStateOf(false) }
    var progreso by remember { mutableStateOf(0) }
    var errorMensaje by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!info.obligatoria && !descargando) {
                onCerrar()
            }
        },
        title = {
            Text(
                "¡Nueva actualización disponible! (v${info.versionName})",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hay una versión más reciente de la aplicación.")
                if (info.novedades.isNotBlank()) {
                    Text("Novedades:", fontWeight = FontWeight.SemiBold)
                    Text(info.novedades, style = MaterialTheme.typography.bodyMedium)
                }
                if (info.obligatoria) {
                    Text(
                        "Esta actualización es obligatoria para continuar usando el torneo.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (descargando) {
                    Spacer(Modifier.height(8.dp))
                    Text("Descargando actualización: $progreso%")
                    LinearProgressIndicator(
                        progress = { progreso / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                errorMensaje?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !descargando,
                onClick = {
                    descargando = true
                    errorMensaje = null
                    scope.launch {
                        val res = servicio.descargarEInstalarApk(contexto, info.urlDescarga) { p ->
                            progreso = p
                        }
                        descargando = false
                        res.onFailure {
                            errorMensaje = "Error al descargar: ${it.message}"
                        }
                    }
                },
            ) {
                Text(if (descargando) "Descargando..." else "Actualizar ahora")
            }
        },
        dismissButton = if (!info.obligatoria && !descargando) {
            {
                OutlinedButton(onClick = onCerrar) {
                    Text("Más tarde")
                }
            }
        } else null,
    )
}
