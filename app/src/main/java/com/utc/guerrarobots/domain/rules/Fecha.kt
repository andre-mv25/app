package com.utc.guerrarobots.domain.rules

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fecha y hora como se muestran en los reportes: hora de la Ciudad de México. */
object Fecha {
    fun formatear(ms: Long): String {
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT)
        formato.timeZone = TimeZone.getTimeZone("America/Mexico_City")
        return formato.format(Date(ms))
    }
}
