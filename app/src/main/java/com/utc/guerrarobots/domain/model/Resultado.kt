package com.utc.guerrarobots.domain.model

enum class Codigo { VALIDACION, DUPLICADO, NO_ENCONTRADO, CONFLICTO, NO_PERMITIDO }

/** Un error de regla de negocio. [campo] indica qué campo del formulario marcar. */
data class ErrorDominio(
    val codigo: Codigo,
    val mensaje: String,
    val campo: String? = null,
)

/** Resultado de una operación de los servicios: o salió bien, o trae uno o más errores. */
sealed interface Resultado<out T> {
    data class Ok<out T>(val valor: T) : Resultado<T>

    data class Falla(val errores: List<ErrorDominio>) : Resultado<Nothing> {
        val primero: ErrorDominio get() = errores.first()
    }
}

fun falla(codigo: Codigo, mensaje: String, campo: String? = null): Resultado.Falla =
    Resultado.Falla(listOf(ErrorDominio(codigo, mensaje, campo)))
