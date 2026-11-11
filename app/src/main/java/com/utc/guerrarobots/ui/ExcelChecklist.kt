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
     * Ordena la lista de equipos en el reporte: por Categoría, luego los que ya llegaron, y por Folio.
     */
    fun ordenarEquipos(equipos: List<Equipo>, categorias: List<Categoria>): List<Equipo> {
        val nombresCat = categorias.associate { it.clave to it.nombre }
        return equipos.sortedWith(
            compareBy(
                { nombresCat[it.robot.categoria] ?: it.robot.categoria.valor },
                { !it.asistencia },
                { it.folio },
            ),
        )
    }

    /**
     * Genera un archivo HTML Spreadsheet (.xls) con diseño profesional, colores,
     * columnas independientes A-M, encabezados destacados y el Administrador que registró cada cambio.
     */
    fun generarExcelHtml(equipos: List<Equipo>, categorias: List<Categoria>): String {
        val nombresCat = categorias.associate { it.clave to it.nombre }
        val equiposOrdenados = ordenarEquipos(equipos, categorias)
        val sb = StringBuilder()

        sb.append(
            """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Calibri, Arial, sans-serif; margin: 10px; }
                h2 { color: #1A365D; font-size: 16pt; margin-bottom: 4px; }
                p { color: #4A5568; font-size: 10pt; margin-top: 0; margin-bottom: 12px; }
                table { border-collapse: collapse; width: 100%; font-size: 10pt; }
                th { background-color: #1A365D; color: #FFFFFF; font-weight: bold; border: 1px solid #CBD5E0; padding: 8px 10px; text-align: center; }
                td { border: 1px solid #E2E8F0; padding: 6px 10px; vertical-align: middle; }
                tr:nth-child(even) { background-color: #F7FAFC; }
                .center { text-align: center; }
                .folio { font-weight: bold; font-family: Consolas, monospace; color: #2B6CB0; text-align: center; }
                .llego { background-color: #C6F6D5; color: #22543D; font-weight: bold; text-align: center; }
                .pendiente { background-color: #EDF2F7; color: #718096; text-align: center; }
                .aceptado { color: #276749; font-weight: bold; }
                .revision { color: #DD6B20; font-weight: bold; }
                .rechazado { color: #E53E3E; font-weight: bold; }
                .admin { color: #2C5282; font-weight: bold; text-align: center; }
            </style>
            </head>
            <body>
            <h2>CHECKLIST DE EQUIPOS EN TIEMPO REAL - GUERRA DE ROBOTS UTC</h2>
            <p>Reporte generado el ${formatoFecha.format(Instant.now())} | Total equipos: ${equipos.size}</p>
            <table>
              <thead>
                <tr>
                  <th>No.</th>
                  <th>Folio</th>
                  <th>Nombre del Equipo</th>
                  <th>Robot</th>
                  <th>Categoría</th>
                  <th>Plantel</th>
                  <th>Capitán</th>
                  <th>Integrantes</th>
                  <th>Estado Registro</th>
                  <th>Asistencia (Checklist)</th>
                  <th>Hora de Llegada</th>
                  <th>Registrado por (Admin)</th>
                  <th>Contenido QR</th>
                </tr>
              </thead>
              <tbody>
            """.trimIndent(),
        ).append("\n")

        equiposOrdenados.forEachIndexed { i, e ->
            val nombreCat = nombresCat[e.robot.categoria] ?: e.robot.categoria.valor
            val integrantes = if (e.integrantes.isNotEmpty()) e.integrantes.joinToString(", ") else "Solo capitán"
            val (estadoTxt, estadoClase) = when (e.estado) {
                EstadoEquipo.ACEPTADO -> "Aceptado" to "aceptado"
                EstadoEquipo.REVISION -> "En revisión" to "revision"
                EstadoEquipo.PENDIENTE -> "Pendiente" to "pendiente"
                EstadoEquipo.RECHAZADO -> "Rechazado" to "rechazado"
            }
            val (asistenciaTxt, asistenciaClase) = if (e.asistencia) {
                "LLEGÓ (Presente)" to "llego"
            } else {
                "PENDIENTE" to "pendiente"
            }
            val horaLlegadaTxt = e.horaLlegada?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "-"
            val adminTxt = e.adminRegistro ?: "-"
            val qrTexto = CodigoRegistro.texto(e, nombreCat).replace("\n", " | ")

            sb.append(
                """
                <tr>
                  <td class="center">${i + 1}</td>
                  <td class="folio">${htmlEscape(e.folio)}</td>
                  <td><b>${htmlEscape(e.nombre)}</b></td>
                  <td>${htmlEscape(e.robot.nombre)}</td>
                  <td>${htmlEscape(nombreCat)}</td>
                  <td>${htmlEscape(e.plantel)}</td>
                  <td>${htmlEscape(e.capitan)}</td>
                  <td>${htmlEscape(integrantes)}</td>
                  <td class="center $estadoClase">${htmlEscape(estadoTxt)}</td>
                  <td class="$asistenciaClase">${htmlEscape(asistenciaTxt)}</td>
                  <td class="center">${htmlEscape(horaLlegadaTxt)}</td>
                  <td class="admin">${htmlEscape(adminTxt)}</td>
                  <td>${htmlEscape(qrTexto)}</td>
                </tr>
                """.trimIndent(),
            ).append("\n")
        }

        sb.append(
            """
              </tbody>
            </table>
            </body>
            </html>
            """.trimIndent(),
        )

        return sb.toString()
    }

    /**
     * Genera archivo CSV ordenado con comando sep=; explícito para que Excel en español
     * separe automáticamente las columnas en A, B, C, D, E...
     */
    fun generarCsv(equipos: List<Equipo>, categorias: List<Categoria>): String {
        val nombresCat = categorias.associate { it.clave to it.nombre }
        val equiposOrdenados = ordenarEquipos(equipos, categorias)
        val sb = StringBuilder()

        sb.append("sep=;\n")
        sb.append("\uFEFF") // BOM UTF-8

        sb.append("No.;Folio;Nombre Equipo;Robot;Categoría;Plantel;Capitán;Integrantes;Estado Registro;Asistencia (Checklist);Hora de Llegada;Registrado por (Admin);Contenido QR\n")

        equiposOrdenados.forEachIndexed { i, e ->
            val nombreCat = nombresCat[e.robot.categoria] ?: e.robot.categoria.valor
            val integrantes = if (e.integrantes.isNotEmpty()) e.integrantes.joinToString(", ") else "Solo capitán"
            val estadoTxt = when (e.estado) {
                EstadoEquipo.ACEPTADO -> "Aceptado"
                EstadoEquipo.REVISION -> "En revisión"
                EstadoEquipo.PENDIENTE -> "Pendiente"
                EstadoEquipo.RECHAZADO -> "Rechazado"
            }
            val asistenciaTxt = if (e.asistencia) "LLEGÓ (Presente)" else "PENDIENTE"
            val horaLlegadaTxt = e.horaLlegada?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "-"
            val adminTxt = e.adminRegistro ?: "-"
            val qrTexto = CodigoRegistro.texto(e, nombreCat).replace("\n", " | ").replace("\"", "\"\"")

            sb.append("${i + 1};")
            sb.append("\"${csvEscape(e.folio)}\";")
            sb.append("\"${csvEscape(e.nombre)}\";")
            sb.append("\"${csvEscape(e.robot.nombre)}\";")
            sb.append("\"${csvEscape(nombreCat)}\";")
            sb.append("\"${csvEscape(e.plantel)}\";")
            sb.append("\"${csvEscape(e.capitan)}\";")
            sb.append("\"${csvEscape(integrantes)}\";")
            sb.append("\"${csvEscape(estadoTxt)}\";")
            sb.append("\"${csvEscape(asistenciaTxt)}\";")
            sb.append("\"${csvEscape(horaLlegadaTxt)}\";")
            sb.append("\"${csvEscape(adminTxt)}\";")
            sb.append("\"${qrTexto}\"\n")
        }

        return sb.toString()
    }

    private fun csvEscape(texto: String): String = texto.replace("\"", "\"\"")
    private fun htmlEscape(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * Exporta el checklist formateado en orden como archivo Excel (.xls)
     * y abre el selector de aplicaciones de Android (Excel, Sheets, WhatsApp, etc.).
     */
    fun guardarYCompartirExcel(contexto: Context, equipos: List<Equipo>, categorias: List<Categoria>): File? {
        val contenidoHtml = generarExcelHtml(equipos, categorias)
        val nombreArchivo = "Checklist_Equipos_GuerraRobots.xls"

        return try {
            val cacheDir = contexto.cacheDir
            val archivo = File(cacheDir, nombreArchivo)
            archivo.writeText(contenidoHtml, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.ms-excel"
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
