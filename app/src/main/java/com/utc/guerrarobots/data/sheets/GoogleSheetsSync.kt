package com.utc.guerrarobots.data.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.rules.CodigoRegistro
import com.utc.guerrarobots.domain.service.Hoja
import com.utc.guerrarobots.domain.service.ReporteCategoria
import com.utc.guerrarobots.ui.ExcelChecklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Servicio de sincronización en tiempo real entre la app Android y una hoja de cálculo
 * compartida de Google Sheets a través de un Webhook de Google Apps Script.
 */
object GoogleSheetsSync {

    private const val PREFS_NAME = "guerra_robots_prefs"
    private const val KEY_WEBHOOK_URL = "google_sheets_webhook_url"
    private const val KEY_SHEET_URL = "google_sheets_view_url"

    /** URL oficial del Webhook de Google Sheets para el torneo. */
    const val URL_WEBHOOK_OFICIAL: String =
        "https://script.google.com/macros/s/AKfycbww_vmdfFPaOGDPdYEb9hVJvAscZXO1WFXg6XaVdjIBun6tBc-0AScpFTFMWrgjWZU/exec"

    /** Enlace oficial directo a la Hoja de Cálculo en Google Sheets. */
    const val URL_SHEET_OFICIAL: String =
        "https://docs.google.com/spreadsheets/d/1b6IiTWJLhKpFH6-NytjueiP9UOClTMijr1FHKHwFseE/edit?gid=0#gid=0"

    private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

    fun obtenerWebhookUrl(context: Context): String {
        val guardada = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WEBHOOK_URL, "") ?: ""
        return guardada.ifBlank { URL_WEBHOOK_OFICIAL }
    }

    fun guardarWebhookUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEBHOOK_URL, url.trim())
            .apply()
    }

    fun obtenerSheetUrl(context: Context): String {
        val guardada = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SHEET_URL, "") ?: ""
        return guardada.ifBlank { URL_SHEET_OFICIAL }
    }

    fun guardarSheetUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHEET_URL, url.trim())
            .apply()
    }

    fun estaConfigurado(context: Context): Boolean =
        obtenerWebhookUrl(context).isNotBlank()

    /** Abre la hoja compartida de Google Sheets en el navegador o app oficial. */
    fun abrirGoogleSheet(context: Context): Boolean {
        val url = obtenerSheetUrl(context).ifBlank { obtenerWebhookUrl(context) }
        if (url.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Envía la actualización de asistencia de un equipo a Google Sheets en tiempo real.
     */
    suspend fun sincronizarAsistencia(
        context: Context,
        folio: String,
        asistencia: Boolean,
        horaMs: Long?,
        adminNombre: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val webhookUrl = obtenerWebhookUrl(context)
        if (webhookUrl.isBlank()) return@withContext false

        try {
            val horaTxt = if (asistencia) {
                horaMs?.let { formatoFecha.format(Instant.ofEpochMilli(it)) }
                    ?: formatoFecha.format(Instant.now())
            } else {
                "-"
            }

            val payload = JSONObject().apply {
                put("accion", "actualizarAsistencia")
                put("folio", folio)
                put("asistencia", asistencia)
                put("horaLlegada", horaTxt)
                put("adminRegistro", if (asistencia) (adminNombre ?: "Admin") else "-")
            }

            enviarPost(webhookUrl, payload.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sincroniza todos los equipos ordenados con Google Sheets (pobla o refresca toda la hoja).
     */
    suspend fun sincronizarTodo(
        context: Context,
        equipos: List<Equipo>,
        categorias: List<Categoria>,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val webhookUrl = obtenerWebhookUrl(context)
        if (webhookUrl.isBlank()) {
            return@withContext false to "Configura la URL del Webhook de Google Sheets en los ajustes."
        }

        try {
            val nombresCat = categorias.associate { it.clave to it.nombre }
            val ordenados = ExcelChecklist.ordenarEquipos(equipos, categorias)

            val arrayEquipos = JSONArray()
            ordenados.forEach { e ->
                val nombreCat = nombresCat[e.robot.categoria] ?: e.robot.categoria.valor
                val horaTxt = e.horaLlegada?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "-"
                val qrTexto = CodigoRegistro.texto(e, nombreCat).replace("\n", " | ")

                val item = JSONObject().apply {
                    put("folio", e.folio)
                    put("nombre", e.nombre)
                    put("robot", e.robot.nombre)
                    put("categoria", nombreCat)
                    put("plantel", e.plantel)
                    put("capitan", e.capitan)
                    put("integrantes", e.integrantes.joinToString(", "))
                    put("estado", e.estado.name)
                    put("asistencia", e.asistencia)
                    put("horaLlegada", horaTxt)
                    put("adminRegistro", e.adminRegistro ?: "-")
                    put("qr", qrTexto)
                }
                arrayEquipos.put(item)
            }

            val payload = JSONObject().apply {
                put("accion", "sincronizarTodo")
                put("equipos", arrayEquipos)
            }

            val exito = enviarPost(webhookUrl, payload.toString())
            if (exito) {
                true to "¡Google Sheets sincronizado con éxito (${ordenados.size} equipos)!"
            } else {
                false to "Error al comunicar con Google Sheets. Revisa la URL del Webhook."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false to "Fallo de conexión: ${e.message}"
        }
    }

    /**
     * Sincroniza un reporte completo (múltiples pestañas) con Google Sheets.
     */
    suspend fun sincronizarReporte(
        context: Context,
        reporte: ReporteCategoria,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val webhookUrl = obtenerWebhookUrl(context)
        if (webhookUrl.isBlank()) {
            return@withContext false to "Configura la URL del Webhook de Google Sheets en los ajustes."
        }

        try {
            val hojasArray = JSONArray()
            reporte.hojas.forEach { hoja ->
                val filasArray = JSONArray()
                hoja.filas.forEach { fila ->
                    val filaObj = JSONObject()
                    fila.forEachIndexed { index, valor ->
                        filaObj.put("col$index", valor)
                    }
                    filasArray.put(filaObj)
                }
                hojasArray.put(
                    JSONObject().apply {
                        put("titulo", hoja.titulo)
                        put("filas", filasArray)
                    }
                )
            }

            val payload = JSONObject().apply {
                put("accion", "sincronizarReporte")
                put("categoria", reporte.categoria.nombre)
                put("hojas", hojasArray)
            }

            val exito = enviarPost(webhookUrl, payload.toString())
            if (exito) {
                true to "¡Reporte de ${reporte.categoria.nombre} sincronizado con éxito!"
            } else {
                false to "Error al comunicar con Google Sheets. Revisa la URL del Webhook."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false to "Fallo de conexión: ${e.message}"
        }
    }

    private fun enviarPost(urlStr: String, jsonString: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 15_000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { os ->
                os.write(jsonString)
                os.flush()
            }

            val codigo = connection.responseCode
            // Google Apps Script suele retornar 200 OK o 302 Redirect (tras procesar con éxito el POST)
            codigo in 200..399
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Código de Google Apps Script listo para copiar y pegar en Extensiones -> Apps Script de la hoja de cálculo.
     */
    const val CODIGO_APPS_SCRIPT: String = """
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    
    if (data.accion === "actualizarAsistencia") {
      var values = sheet.getDataRange().getValues();
      var folioCol = 1; // Columna B (índice 1 en array 0-based)
      var filaEncontrada = -1;
      
      for (var i = 1; i < values.length; i++) {
        if (String(values[i][folioCol]).trim().toUpperCase() === String(data.folio).trim().toUpperCase()) {
          filaEncontrada = i + 1; // 1-based en Sheets
          break;
        }
      }
      
      if (filaEncontrada > 0) {
        var estadoTxt = data.asistencia ? "LLEGÓ (Presente)" : "PENDIENTE";
        var horaTxt = data.horaLlegada || "-";
        var adminTxt = data.adminRegistro || "-";
        
        sheet.getRange(filaEncontrada, 10).setValue(estadoTxt); // Columna J
        sheet.getRange(filaEncontrada, 11).setValue(horaTxt);   // Columna K
        sheet.getRange(filaEncontrada, 12).setValue(adminTxt);  // Columna L
        
        if (data.asistencia) {
          sheet.getRange(filaEncontrada, 10).setBackground("#C6F6D5").setFontColor("#22543D").setFontWeight("bold");
          sheet.getRange(filaEncontrada, 12).setFontColor("#2C5282").setFontWeight("bold");
        } else {
          sheet.getRange(filaEncontrada, 10).setBackground("#EDF2F7").setFontColor("#718096").setFontWeight("normal");
          sheet.getRange(filaEncontrada, 12).setFontColor("#4A5568").setFontWeight("normal");
        }
        
        return ContentService.createTextOutput(JSON.stringify({ status: "ok", folio: data.folio, fila: filaEncontrada }))
          .setMimeType(ContentService.MimeType.JSON);
      }
    }
    
    if (data.accion === "sincronizarTodo") {
      sheet.clear();
      var headers = [
        "No.", "Folio", "Nombre del Equipo", "Robot", "Categoría", "Plantel",
        "Capitán", "Integrantes", "Estado Registro", "Asistencia (Checklist)",
        "Hora de Llegada", "Registrado por (Admin)", "Contenido QR"
      ];
      sheet.appendRow(headers);
      var headerRange = sheet.getRange(1, 1, 1, headers.length);
      headerRange.setBackground("#1A365D").setFontColor("#FFFFFF").setFontWeight("bold").setHorizontalAlignment("center");
      
      var rows = data.equipos.map(function(eq, idx) {
        return [
          idx + 1,
          eq.folio,
          eq.nombre,
          eq.robot,
          eq.categoria,
          eq.plantel,
          eq.capitan,
          eq.integrantes,
          eq.estado,
          eq.asistencia ? "LLEGÓ (Presente)" : "PENDIENTE",
          eq.horaLlegada || "-",
          eq.adminRegistro || "-",
          eq.qr || ""
        ];
      });
      
      if (rows.length > 0) {
        sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
      }
      
      for (var col = 1; col <= headers.length; col++) {
        sheet.autoResizeColumn(col);
      }
      
      return ContentService.createTextOutput(JSON.stringify({ status: "ok", total: rows.length }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Acción no reconocida" }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
"""
}
