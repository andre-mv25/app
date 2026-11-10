package com.utc.guerrarobots.domain.rules

import com.utc.guerrarobots.domain.model.Equipo

/**
 * Lo que dice el código QR de un registro. Es texto legible: cualquier lector de QR (la cámara del
 * teléfono) muestra el folio y los datos del registro, sin necesitar la app ni internet.
 * El folio va incluido, así que el código de cada equipo es distinto.
 */
object CodigoRegistro {
    /** Los nombres de los participantes se recortan para que el QR no se llene de puntos. */
    private const val MAX_PARTICIPANTES = 120

    fun texto(equipo: Equipo, nombreCategoria: String): String {
        val lineas = mutableListOf(
            "5° TORNEO GUERRA DE ROBOTS · UTC",
            "Folio: ${equipo.folio}",
            "Equipo: ${equipo.nombre}",
            "Robot: ${equipo.robot.nombre}",
            "Categoría: $nombreCategoria",
            "Plantel: ${equipo.plantel}",
            "Capitán: ${equipo.capitan}",
        )
        if (equipo.integrantes.isNotEmpty()) {
            lineas += "Participantes: ${recortar(equipo.integrantes.joinToString(", "))}"
        }
        lineas += "Jefe de carrera: ${equipo.jefeCarrera}"
        lineas += "Peso: ${Peso.formatearKg(equipo.robot.pesoG)} · Tamaño: ${equipo.robot.tamanoCm} cm"
        return lineas.joinToString("\n")
    }

    private fun recortar(texto: String): String =
        if (texto.length <= MAX_PARTICIPANTES) texto else texto.take(MAX_PARTICIPANTES - 1).trimEnd() + "…"
}
