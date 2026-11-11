package com.utc.guerrarobots.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.rules.CodigoRegistro
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExcelChecklist {
    private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

    /**
     * Genera el archivo CSV estructurado para Excel con la codificación BOM UTF-8 (\uFEFF)
     * para que Microsoft Excel y Google Sheets reconozcan tildes, columnas y el estado
     * del checklist en tiempo real.
     */
    fun generarCsv(equipos: List<Equipo>, categorias: List<Categoria>): String {
        val nombresCat = categorias.associate { it.clave to it.nombre }
        val sb = StringBuilder()

        // BOM para abrir directamente en Excel con UTF-8
        sb.append("\uFEFF")

        // Encabezados de Excel
        sb.append("Folio;Nombre Equipo;Robot;Categoría;Plantel;Capitán;Integrantes;Estado Registro;Asistencia (Checklist);Hora de Llegada;Contenido QR\n")

        equipos.forEach { e ->
            val nombreCat = nombresCat[e.robot.categoria] ?: e.robot.categoria.valor
            val integrantes = (listOf(e.capitan) + e.integrantes).joinToString(", ")
            val estadoTxt = when (e.estado) {
                EstadoEquipo.ACEPTADO -> "Aceptado"
                EstadoEquipo.REVISION -> "En revisión"
                EstadoEquipo.PENDIENTE -> "Pendiente"
                EstadoEquipo.RECHAZADO -> "Rechazado"
            }
            val asistenciaTxt = if (e.asistencia) "LLEGÓ (Presente)" else "PENDIENTE"
            val horaLlegadaTxt = e.horaLlegada?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "N/A"
            val qrTexto = CodigoRegistro.texto(e, nombreCat).replace("\n", " | ").replace("\"", "\"\"")

            sb.append("\"${escape(e.folio)}\";")
            sb.append("\"${escape(e.nombre)}\";")
            sb.append("\"${escape(e.robot.nombre)}\";")
            sb.append("\"${escape(nombreCat)}\";")
            sb.append("\"${escape(e.plantel)}\";")
            sb.append("\"${escape(e.capitan)}\";")
            sb.append("\"${escape(integrantes)}\";")
            sb.append("\"${escape(estadoTxt)}\";")
            sb.append("\"${escape(asistenciaTxt)}\";")
            sb.append("\"${escape(horaLlegadaTxt)}\";")
            sb.append("\"${qrTexto}\"\n")
        }

        return sb.toString()
    }

    private fun escape(texto: String): String = texto.replace("\"", "\"\"")

    /**
     * Guarda el checklist como archivo .csv (compatible con Excel) y abre el diálogo para
     * compartirlo o abrirlo en Excel / Google Sheets / WhatsApp.
     */
    fun guardarYCompartirExcel(contexto: Context, equipos: List<Equipo>, categorias: List<Categoria>): File? {
        val contenido = generarCsv(equipos, categorias)
        val nombreArchivo = "Checklist_Equipos_GuerraRobots.csv"

        return try {
            val cacheDir = contexto.cacheDir
            val archivo = File(cacheDir, nombreArchivo)
            archivo.writeText(contenido, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Checklist en Tiempo Real - Guerra de Robots")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Exportar Checklist a Excel")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            contexto.startActivity(chooser)

            archivo
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
