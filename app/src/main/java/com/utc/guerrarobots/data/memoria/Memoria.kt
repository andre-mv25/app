package com.utc.guerrarobots.data.memoria

import com.utc.guerrarobots.domain.model.AceptacionTerminos
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.EventoAuditoria
import com.utc.guerrarobots.domain.model.Terminos
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.repository.AutorizacionRepository
import com.utc.guerrarobots.domain.repository.AuditoriaRepository
import com.utc.guerrarobots.domain.repository.PinStore
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

class EquipoMemoria : EquipoRepository {
    private val datos = mutableMapOf<Long, Equipo>()

    override suspend fun insertar(equipo: Equipo): Long {
        val id = (datos.keys.maxOrNull() ?: 0L) + 1
        datos[id] = equipo.copy(id = id)
        return id
    }

    override suspend fun obtener(id: Long): Equipo? = datos[id]

    override suspend fun listar(estado: EstadoEquipo?, categoria: ClaveCategoria?): List<Equipo> =
        datos.values.filter { e ->
            (estado == null || e.estado == estado) && (categoria == null || e.robot.categoria == categoria)
        }.sortedBy { it.id }

    override suspend fun existeNombreEquipo(nombre: String): Boolean =
        datos.values.any { it.nombre.equals(nombre, ignoreCase = true) }

    override suspend fun existeNombreRobot(nombre: String): Boolean =
        datos.values.any { it.robot.nombre.equals(nombre, ignoreCase = true) }

    override suspend fun actualizarEstado(id: Long, estado: EstadoEquipo) {
        datos[id]?.let { datos[id] = it.copy(estado = estado) }
    }

    override suspend fun actualizarFolio(id: Long, folio: String) {
        datos[id]?.let { datos[id] = it.copy(folio = folio) }
    }

    override suspend fun actualizarLogo(id: Long, logoUri: String?) {
        datos[id]?.let { datos[id] = it.copy(logoUri = logoUri) }
    }

    override suspend fun registrarAsistencia(id: Long, presente: Boolean, horaMs: Long?, adminNombre: String?) {
        datos[id]?.let {
            datos[id] = it.copy(
                asistencia = presente,
                horaLlegada = if (presente) (horaMs ?: System.currentTimeMillis()) else null,
                adminRegistro = if (presente) adminNombre else null,
            )
        }
    }

    override suspend fun eliminar(id: Long) {
        datos.remove(id)
    }
}

class PinMemoria : PinStore {
    private val pines = mutableMapOf<String, MutableMap<Rol, String>>()

    override fun obtenerHash(correo: String, rol: Rol): String? =
        pines[correo]?.get(rol)

    override fun guardarHash(correo: String, rol: Rol, hash: String) {
        pines.getOrPut(correo) { mutableMapOf() }[rol] = hash
    }

    override fun borrarHash(correo: String, rol: Rol) {
        pines[correo]?.remove(rol)
    }
}
