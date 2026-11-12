package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones

/** Textos para personas de las acciones y los usuarios de la bitácora (reportes y hojas de cálculo). */
object Etiquetas {
    fun accion(accion: String): String = when (accion) {
        Acciones.EQUIPO_REGISTRADO -> "Equipo registrado"
        Acciones.ESTADO_EQUIPO_CAMBIADO -> "Estado de equipo cambiado"
        Acciones.EQUIPO_ELIMINADO -> "Registro de equipo eliminado"
        Acciones.PIN_CAMBIADO -> "PIN de acceso cambiado"
        Acciones.LIMITES_ACTUALIZADOS -> "Límites de categoría cambiados"
        Acciones.ORDEN_SORTEADO -> "Orden de salida sorteado"
        Acciones.INTENTO_REGISTRADO -> "Intento capturado"
        Acciones.CARRERA_EXTRA_REGISTRADA -> "Carrera extra capturada"
        Acciones.INTENTO_ANULADO -> "Intento anulado"
        Acciones.CATEGORIA_CERRADA -> "Categoría cerrada"
        Acciones.CATEGORIA_REABIERTA -> "Categoría reabierta"
        Acciones.TERMINOS_PUBLICADOS -> "Términos y condiciones publicados"
        Acciones.LLAVES_GENERADAS -> "Llaves sorteadas"
        Acciones.LLAVES_PROGRAMADAS -> "Canchas asignadas"
        Acciones.PARTIDA_INICIADA -> "Partida iniciada"
        Acciones.PARTIDA_FINALIZADA -> "Resultado de partida"
        Acciones.SHEETS_SINCRONIZADO -> "Sincronizado con Google Sheets"
        else -> accion
    }

    fun actor(actor: String): String = when (actor) {
        "ADMIN" -> "Admin (PIN)"
        "JURADO" -> "Jurado (PIN)"
        "PARTICIPANTE" -> "Participante"
        else -> actor
    }
}
