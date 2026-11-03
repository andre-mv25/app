package com.utc.guerrarobots.domain.rules

import java.text.Normalizer

/** Normalización de nombres y filtro de palabras bloqueadas. */
object Texto {
    private val MARCAS = Regex("\\p{M}+")
    private val ESPACIOS = Regex("\\s+")
    private val LETRAS = Regex("[a-z]+")

    /** Cambios típicos para esquivar un filtro: "p3ndejo" -> "pendejo". */
    private val SUSTITUCIONES = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a',
        '5' to 's', '7' to 't', '@' to 'a', '$' to 's',
    )

    /** Minúsculas, sin acentos y con espacios simples. Se usa para comparar nombres. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(MARCAS, "")
            .lowercase()
            .replace(ESPACIOS, " ")

    /**
     * Dice si el texto contiene una palabra bloqueada. Compara palabra por palabra
     * para no marcar por error palabras largas (por ejemplo "computadora"), y
     * también la versión sin espacios para atrapar "p u t o".
     * [bloqueadas] debe venir normalizada.
     */
    fun contieneBloqueada(texto: String, bloqueadas: Set<String>): Boolean {
        if (bloqueadas.isEmpty()) return false
        val limpio = normalizar(texto).map { SUSTITUCIONES[it] ?: it }.joinToString("")
        val palabras = LETRAS.findAll(limpio).map { it.value }.toList()
        // También se prueban los plurales simples: "putos" y "pendejos".
        val prohibida = { p: String ->
            p in bloqueadas || p.removeSuffix("s") in bloqueadas || p.removeSuffix("es") in bloqueadas
        }
        if (palabras.any(prohibida)) return true
        return prohibida(palabras.joinToString(""))
    }
}
