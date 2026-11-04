package com.utc.guerrarobots.data.memoria

import com.utc.guerrarobots.domain.model.AceptacionTerminos
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Terminos
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.AutorizacionRepository
import com.utc.guerrarobots.domain.repository.AuditoriaRepository
import com.utc.guerrarobots.domain.repository.TerminosRepository

class AuditoriaMemoria : AuditoriaRepository {
    private val datos = mutableListOf<EventoAuditoria>()
    override suspend fun insertar(evento: EventoAuditoria): Long {
        val id = (datos.maxOfOrNull { it.id } ?: 0L) + 1
        datos += evento.copy(id = id)
        return id
    }

    override suspend fun listar(limite: Int): List<EventoAuditoria> = datos.sortedByDescending { it.id }.take(limite)
}

class TerminosMemoria : TerminosRepository {
    private val versiones = mutableListOf<Terminos>()
    private val aceptaciones = mutableListOf<AceptacionTerminos>()
    override suspend fun vigente(): Terminos? = versiones.maxByOrNull { it.version }
    override suspend fun publicar(terminos: Terminos) {
        versiones += terminos
    }

    override suspend fun insertarAceptacion(aceptacion: AceptacionTerminos): Long {
        val id = (aceptaciones.maxOfOrNull { it.id } ?: 0L) + 1
        aceptaciones += aceptacion.copy(id = id)
        return id
    }

    override suspend fun aceptacionesDe(equipoId: Long): List<AceptacionTerminos> = aceptaciones.filter { it.equipoId == equipoId }
}

class AutorizacionMemoria(private val roles: Map<String, Rol> = emptyMap()) : AutorizacionRepository {
    override suspend fun rolDe(correo: String): Rol? = roles[correo]
}
