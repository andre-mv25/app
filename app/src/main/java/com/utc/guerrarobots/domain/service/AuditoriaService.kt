package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.repository.Actor
import com.utc.guerrarobots.domain.repository.AuditoriaRepository
import com.utc.guerrarobots.domain.repository.Reloj
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bitácora de lo que pasa en el torneo. Anotar es de mejor esfuerzo: si la bitácora falla
 * (por ejemplo sin conexión), la operación que se estaba haciendo no se pierde ni se cancela.
 */
class AuditoriaService(
    private val repo: AuditoriaRepository,
    private val reloj: Reloj,
    private val actor: Actor,
) {
    suspend fun registrar(
        accion: String,
        entidad: String,
        entidadId: Long? = null,
        detalle: String? = null,
        categoria: ClaveCategoria? = null,
    ) {
        try {
            repo.insertar(
                EventoAuditoria(
                    actor = actor.actual(), accion = accion, entidad = entidad,
                    entidadId = entidadId, detalle = detalle, creadoEn = reloj.ahora(),
                    categoria = categoria?.valor,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La bitácora nunca debe impedir la operación.
        }
    }

    /** Los eventos más recientes primero. */
    suspend fun listar(limite: Int = 200): List<EventoAuditoria> = repo.listar(limite)
}
