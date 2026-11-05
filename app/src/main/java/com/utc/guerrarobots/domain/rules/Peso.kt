package com.utc.guerrarobots.domain.rules

import java.math.BigDecimal

/**
 * El peso de un robot se captura y se muestra en kilogramos, pero se guarda en gramos (entero)
 * para no perder precisión y para no tener que cambiar lo que ya está en la base.
 */
object Peso {
    /** "1.5" o "1,5" pasa a 1500 gramos. Devuelve null si no es un número mayor que cero. */
    fun kgAGramos(texto: String): Int? {
        val kg = texto.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (kg <= 0.0 || kg > 1_000.0) return null
        return Math.round(kg * 1_000).toInt()
    }

    /** 1500 gramos se muestran como "1.5 kg"; 2000 como "2 kg". */
    fun formatearKg(gramos: Int): String =
        BigDecimal(gramos).movePointLeft(3).stripTrailingZeros().toPlainString() + " kg"
}
