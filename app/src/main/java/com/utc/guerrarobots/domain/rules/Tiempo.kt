package com.utc.guerrarobots.domain.rules

import java.util.Locale

/** Conversión entre minutos, segundos y milésimas (como los captura el jurado) y milisegundos. */
object Tiempo {

    /** Segundos de 0 a 59 y milésimas de 0 a 999. */
    fun esValido(minutos: Int, segundos: Int, milesimas: Int): Boolean =
        minutos >= 0 && segundos in 0..59 && milesimas in 0..999

    /** 1 min, 23 s, 456 ms = 83 456 ms. */
    fun aMilisegundos(minutos: Int, segundos: Int, milesimas: Int): Long {
        require(esValido(minutos, segundos, milesimas)) { "Tiempo fuera de rango" }
        return minutos * 60_000L + segundos * 1_000L + milesimas
    }

    /** 83 456 ms = "01:23.456". */
    fun formatear(ms: Long): String =
        String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, (ms / 1_000) % 60, ms % 1_000)
}
