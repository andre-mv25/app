package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.AceptacionTerminos
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.ErrorDominio
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Terminos
import com.utc.guerrarobots.domain.model.TerminosBase
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.repository.TerminosRepository

/**
 * Términos y condiciones con versiones. Cada equipo deja constancia de qué versión aceptó al
 * registrarse; si los términos cambian, los equipos viejos conservan la versión que aceptaron.
 */
class TerminosService(
    private val repo: TerminosRepository,
    private val reloj: Reloj,
    private val auditoria: AuditoriaService? = null,
) {
    /** Los términos vigentes (los de la versión más reciente). */
    suspend fun vigente(): Terminos? = repo.vigente()

    /** Los términos para una categoría específica (comunes + reglas de esa categoría). */
    suspend fun paraCategoria(clave: ClaveCategoria): Terminos? {
        val reglas = TerminosBase.porCategoria[clave] ?: TerminosBase.reglas
        return Terminos(1, reglas, reloj.ahora())
    }

    /** Crea la versión 1 si todavía no hay ninguna. Se llama al abrir la app. */
    suspend fun sembrar() {
        if (repo.vigente() == null) repo.publicar(Terminos(1, TerminosBase.reglas, reloj.ahora()))
    }

    /** Publica una versión nueva con las reglas dadas (una por renglón, sin renglones vacíos). */
    suspend fun publicar(reglas: List<String>): Resultado<Terminos> {
        val limpias = reglas.map { it.trim() }.filter { it.isNotEmpty() }
        if (limpias.isEmpty()) return falla(Codigo.VALIDACION, "Escribe al menos una regla", "reglas")
        val nueva = Terminos((repo.vigente()?.version ?: 0) + 1, limpias, reloj.ahora())
        repo.publicar(nueva)
        auditoria?.registrar(Acciones.TERMINOS_PUBLICADOS, "terminos", nueva.version.toLong(), "Versión ${nueva.version}, ${limpias.size} reglas")
        return Resultado.Ok(nueva)
    }

    /**
     * Revisa que la versión que aceptó el participante sea la vigente. Devuelve el error a mostrar,
     * o null si está bien (o si todavía no hay términos publicados).
     */
    suspend fun revisarAceptacion(version: Int?): ErrorDominio? {
        val vigente = repo.vigente() ?: return null
        return if (version != vigente.version) {
            ErrorDominio(Codigo.VALIDACION, "Acepta los términos y condiciones vigentes (versión ${vigente.version})", "terminos")
        } else {
            null
        }
    }

    /** Deja constancia de que el equipo aceptó la [version]. */
    suspend fun registrarAceptacion(equipoId: Long, version: Int) {
        repo.insertarAceptacion(AceptacionTerminos(equipoId = equipoId, version = version, aceptadoEn = reloj.ahora()))
    }

    suspend fun aceptacionesDe(equipoId: Long): List<AceptacionTerminos> = repo.aceptacionesDe(equipoId)
}
