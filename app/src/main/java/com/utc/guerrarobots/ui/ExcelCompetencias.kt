package com.utc.guerrarobots.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoPartida
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Partida
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.rules.Tiempo
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generador de Reporte y Excel completo para Jueces y Administradores.
 * Integra:
 * 1) Hoja de Competencias (Partidas de llaves y Carreras de tiempo, con juez, fecha, hora, ganador y quién modificó).
 * 2) Hoja de Equipos y Asistencia (Checklist oficial).
 */
object ExcelCompetencias {

    private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

    data class FilaCompetencia(
        val categoriaNombre: String,
        val tipo: String, // "Llaves (Combate/Fútbol)" o "Carrera (Tiempo)"
        val identificador: String, // "Ronda 1 - Match 1" o "Intento 1"
        val equipoA: String,
        val equipoB: String,
        val resultadoDetalle: String, // "3 - 1" o "01:23.450"
        val ganador: String,
        val estado: String,
        val fechaHora: String,
        val juezResponsable: String,
        val modificadoPor: String,
    )

    /**
     * Construye las filas de competencias a partir de las partidas, intentos y bitácora de auditoría.
     */
    fun recopilarCompetencias(
        categorias: List<Categoria>,
        equipos: List<Equipo>,
        partidas: List<Partida>,
        intentos: List<Intento>,
        auditoria: List<EventoAuditoria>,
    ): List<FilaCompetencia> {
        val equiposMap = equipos.associateBy { it.id }
        val categoriasMap = categorias.associateBy { it.clave }
        val resultado = mutableListOf<FilaCompetencia>()

        // 1. Partidas (Llaves: Combate, Fútbol)
        partidas.sortedWith(compareBy({ it.categoria.valor }, { it.ronda }, { it.posicion })).forEach { p ->
            val cat = categoriasMap[p.categoria]
            val catNombre = cat?.nombre ?: p.categoria.valor
            val eqA = p.equipoAId?.let { equiposMap[it]?.nombre } ?: "Por definir"
            val eqB = p.equipoBId?.let { equiposMap[it]?.nombre } ?: if (p.esBye) "BYE" else "Por definir"

            val ganadorNombre = when {
                p.ganadorId != null -> equiposMap[p.ganadorId]?.nombre ?: "ID: ${p.ganadorId}"
                p.esBye && p.equipoAId != null -> equiposMap[p.equipoAId]?.nombre ?: "Pasa directo"
                else -> "Pendiente"
            }

            val marcador = when {
                p.golesA != null && p.golesB != null -> "${p.golesA} - ${p.golesB}"
                p.ganadorId != null -> "Ganador: $ganadorNombre"
                p.esBye -> "Pase directo"
                else -> "-"
            }

            val fechaHora = p.finalizadaEn?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "-"

            // Buscar en auditoría quién finalizó o modificó esta partida
            val eventoPartida = auditoria.firstOrNull {
                it.entidad == "partida" && it.entidadId == p.id
            }
            val juez = eventoPartida?.actor ?: "-"
            val quienModifico = eventoPartida?.let { "${it.actor} (${it.accion})" } ?: "-"

            resultado.add(
                FilaCompetencia(
                    categoriaNombre = catNombre,
                    tipo = "Llaves / Eliminatoria",
                    identificador = "Ronda ${p.ronda} - Cruce ${p.posicion}${p.cancha?.let { " (Cancha $it)" } ?: ""}",
                    equipoA = eqA,
                    equipoB = eqB,
                    resultadoDetalle = marcador,
                    ganador = ganadorNombre,
                    estado = p.estado.name,
                    fechaHora = fechaHora,
                    juezResponsable = juez,
                    modificadoPor = quienModifico,
                ),
            )
        }

        // 2. Intentos (Tiempo: Sigue líneas, Laberinto, Velocidad)
        intentos.sortedWith(compareBy({ it.categoria.valor }, { it.equipoId }, { it.numero })).forEach { i ->
            val cat = categoriasMap[i.categoria]
            val catNombre = cat?.nombre ?: i.categoria.valor
            val eq = equiposMap[i.equipoId]?.nombre ?: "Equipo #${i.equipoId}"

            val tiempoTxt = when (i.resultado) {
                ResultadoIntento.VALIDO -> {
                    val formatted = Tiempo.formatear(i.tiempoMs ?: 0)
                    if (i.penalizaciones > 0) {
                        "${Tiempo.formatear(i.tiempoEfectivoMs ?: 0)} (Bruto: $formatted + ${i.penalizaciones} pen.)"
                    } else formatted
                }
                ResultadoIntento.NO_TERMINO -> "No terminó"
                ResultadoIntento.DESCALIFICADO -> "Descalificado (${i.motivo ?: "s/m"})"
            }

            val fechaHora = formatoFecha.format(Instant.ofEpochMilli(i.capturadoEn))

            // Buscar en auditoría el evento del intento
            val eventoIntento = auditoria.firstOrNull {
                (it.entidad == "intento" && it.entidadId == i.id) ||
                    (it.entidad == "categoria" && it.detalle?.contains(eq, ignoreCase = true) == true)
            }
            val juez = eventoIntento?.actor ?: "-"
            val quienModifico = eventoIntento?.let { "${it.actor} (${it.accion})" } ?: "-"

            resultado.add(
                FilaCompetencia(
                    categoriaNombre = catNombre,
                    tipo = "Carrera / Tiempo",
                    identificador = "Intento #${i.numero}${i.pista?.let { " (Pista $it)" } ?: ""}",
                    equipoA = eq,
                    equipoB = "-",
                    resultadoDetalle = tiempoTxt,
                    ganador = if (i.resultado == ResultadoIntento.VALIDO) eq else "N/A",
                    estado = i.resultado.name,
                    fechaHora = fechaHora,
                    juezResponsable = juez,
                    modificadoPor = quienModifico,
                ),
            )
        }

        return resultado
    }

    /**
     * Genera un libro HTML Spreadsheet (.xls) compatible con Microsoft Excel y Google Sheets,
     * con dos hojas / tablas visuales independientes:
     * 1) Competencias Oficiales (Resultados, Ganadores y Jueces).
     * 2) Checklist de Equipos y Asistencia.
     */
    fun generarExcelHtml(
        categorias: List<Categoria>,
        equipos: List<Equipo>,
        partidas: List<Partida>,
        intentos: List<Intento>,
        auditoria: List<EventoAuditoria>,
    ): String {
        val competencias = recopilarCompetencias(categorias, equipos, partidas, intentos, auditoria)
        val sb = StringBuilder()

        sb.append(
            """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Calibri, Arial, sans-serif; margin: 15px; }
                h2 { color: #1A365D; font-size: 16pt; margin-bottom: 2px; }
                h3 { color: #2B6CB0; font-size: 13pt; margin-top: 24px; margin-bottom: 6px; border-bottom: 2px solid #2B6CB0; padding-bottom: 4px; }
                p { color: #4A5568; font-size: 10pt; margin-top: 0; margin-bottom: 12px; }
                table { border-collapse: collapse; width: 100%; font-size: 9.5pt; margin-bottom: 25px; }
                th { background-color: #1A365D; color: #FFFFFF; font-weight: bold; border: 1px solid #CBD5E0; padding: 7px 9px; text-align: center; }
                td { border: 1px solid #E2E8F0; padding: 6px 8px; vertical-align: middle; }
                tr:nth-child(even) { background-color: #F7FAFC; }
                .center { text-align: center; }
                .bold { font-weight: bold; }
                .ganador { background-color: #C6F6D5; color: #22543D; font-weight: bold; text-align: center; }
                .juez { background-color: #EBF8FF; color: #2B6CB0; font-weight: bold; text-align: center; }
                .modificado { color: #4A5568; font-size: 8.5pt; }
                .llego { background-color: #C6F6D5; color: #22543D; font-weight: bold; text-align: center; }
                .pendiente { background-color: #EDF2F7; color: #718096; text-align: center; }
            </style>
            </head>
            <body>
            <h2>REPORTE OFICIAL DE COMPETENCIAS Y JUECES - GUERRA DE ROBOTS UTC</h2>
            <p>Generado el ${formatoFecha.format(Instant.now())} | Total partidas/intentos: ${competencias.size}</p>

            <h3>1. REGISTRO DE COMPETENCIAS, JUECES Y GANADORES</h3>
            <table>
              <thead>
                <tr>
                  <th>No.</th>
                  <th>Categoría</th>
                  <th>Modalidad</th>
                  <th>Ronda / Intento</th>
                  <th>Equipo A (o Competidor)</th>
                  <th>Equipo B</th>
                  <th>Marcador / Tiempo</th>
                  <th>Equipo Ganador</th>
                  <th>Estado</th>
                  <th>Fecha y Hora</th>
                  <th>Juez Responsable</th>
                  <th>Registrado / Modificado Por</th>
                </tr>
              </thead>
              <tbody>
            """.trimIndent(),
        )

        competencias.forEachIndexed { idx, c ->
            sb.append(
                """
                <tr>
                  <td class="center">${idx + 1}</td>
                  <td class="bold">${htmlEscape(c.categoriaNombre)}</td>
                  <td class="center">${htmlEscape(c.tipo)}</td>
                  <td>${htmlEscape(c.identificador)}</td>
                  <td>${htmlEscape(c.equipoA)}</td>
                  <td>${htmlEscape(c.equipoB)}</td>
                  <td class="center bold">${htmlEscape(c.resultadoDetalle)}</td>
                  <td class="ganador">${htmlEscape(c.ganador)}</td>
                  <td class="center">${htmlEscape(c.estado)}</td>
                  <td class="center">${htmlEscape(c.fechaHora)}</td>
                  <td class="juez">${htmlEscape(c.juezResponsable)}</td>
                  <td class="modificado">${htmlEscape(c.modificadoPor)}</td>
                </tr>
                """.trimIndent(),
            )
        }

        sb.append(
            """
              </tbody>
            </table>

            <h3>2. CHECKLIST Y ASISTENCIA DE EQUIPOS</h3>
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
                  <th>Asistencia</th>
                  <th>Hora Llegada</th>
                  <th>Registrado por (Admin)</th>
                </tr>
              </thead>
              <tbody>
            """.trimIndent(),
        )

        val equiposOrdenados = ExcelChecklist.ordenarEquipos(equipos, categorias)
        val nombresCat = categorias.associate { it.clave to it.nombre }
        equiposOrdenados.forEachIndexed { idx, e ->
            val catNombre = nombresCat[e.robot.categoria] ?: e.robot.categoria.valor
            val asistenciaTxt = if (e.asistencia) "LLEGÓ" else "PENDIENTE"
            val asistenciaClase = if (e.asistencia) "llego" else "pendiente"
            val horaTxt = e.horaLlegada?.let { formatoFecha.format(Instant.ofEpochMilli(it)) } ?: "-"
            val adminTxt = e.adminRegistro ?: "-"

            sb.append(
                """
                <tr>
                  <td class="center">${idx + 1}</td>
                  <td class="center bold">${htmlEscape(e.folio)}</td>
                  <td>${htmlEscape(e.nombre)}</td>
                  <td>${htmlEscape(e.robot.nombre)}</td>
                  <td>${htmlEscape(catNombre)}</td>
                  <td>${htmlEscape(e.plantel)}</td>
                  <td>${htmlEscape(e.capitan)}</td>
                  <td class="$asistenciaClase">${htmlEscape(asistenciaTxt)}</td>
                  <td class="center">${htmlEscape(horaTxt)}</td>
                  <td class="center bold">${htmlEscape(adminTxt)}</td>
                </tr>
                """.trimIndent(),
            )
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

    private fun htmlEscape(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /**
     * Guarda el reporte en almacenamiento temporal y abre el selector de compartir para enviarlo
     * o abrirlo en Excel / Google Sheets.
     */
    fun exportarYCompartir(
        contexto: Context,
        categorias: List<Categoria>,
        equipos: List<Equipo>,
        partidas: List<Partida>,
        intentos: List<Intento>,
        auditoria: List<EventoAuditoria>,
    ): File? {
        val html = generarExcelHtml(categorias, equipos, partidas, intentos, auditoria)
        val nombreArchivo = "Reporte_Competencias_Jueces_GuerraRobots.xls"

        return try {
            val cacheDir = contexto.cacheDir
            val archivo = File(cacheDir, nombreArchivo)
            archivo.writeText(html, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.ms-excel"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte de Competencias y Jueces - Guerra de Robots")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Abrir / Exportar Reporte de Competencias")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            contexto.startActivity(chooser)

            archivo
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
