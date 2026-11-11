package com.utc.guerrarobots.data.firebase

import com.utc.guerrarobots.domain.model.*
import com.utc.guerrarobots.domain.rules.Texto

object Mapeo {
    private fun long(m: Map<String, Any?>, k: String): Long = (m[k] as Number).toLong()
    private fun int(m: Map<String, Any?>, k: String): Int = (m[k] as Number).toInt()
    private fun longOrNull(m: Map<String, Any?>, k: String): Long? = (m[k] as? Number)?.toLong()
    private fun intOrNull(m: Map<String, Any?>, k: String): Int? = (m[k] as? Number)?.toInt()
    private fun doubleOrNull(m: Map<String, Any?>, k: String): Double? = (m[k] as? Number)?.toDouble()
    private fun str(m: Map<String, Any?>, k: String): String = m[k] as String
    private fun strOrNull(m: Map<String, Any?>, k: String): String? = m[k] as? String

    fun aMapa(e: Equipo): Map<String, Any?> = mapOf(
        "id" to e.id, "folio" to e.folio, "nombre" to e.nombre, "nombreNorm" to Texto.normalizar(e.nombre),
        "capitan" to e.capitan, "plantel" to e.plantel, "jefeCarrera" to e.jefeCarrera, "logoUri" to e.logoUri,
        "estado" to e.estado.name, "integrantes" to e.integrantes,
        "robotNombre" to e.robot.nombre, "robotNombreNorm" to Texto.normalizar(e.robot.nombre),
        "robotCategoria" to e.robot.categoria.name, "robotPesoG" to e.robot.pesoG, "robotTamanoCm" to e.robot.tamanoCm,
        "asistencia" to e.asistencia, "horaLlegada" to e.horaLlegada,
    )

    fun aEquipo(m: Map<String, Any?>): Equipo = Equipo(
        id = long(m, "id"), folio = str(m, "folio"), nombre = str(m, "nombre"), capitan = str(m, "capitan"),
        plantel = str(m, "plantel"), jefeCarrera = str(m, "jefeCarrera"), logoUri = strOrNull(m, "logoUri"),
        estado = EstadoEquipo.valueOf(str(m, "estado")),
        integrantes = (m["integrantes"] as? List<*>)?.map { it.toString() } ?: emptyList(),
        robot = Robot(
            nombre = str(m, "robotNombre"), categoria = ClaveCategoria(str(m, "robotCategoria")),
            pesoG = int(m, "robotPesoG"), tamanoCm = (m["robotTamanoCm"] as Number).toDouble(),
        ),
        asistencia = (m["asistencia"] as? Boolean) ?: false,
        horaLlegada = longOrNull(m, "horaLlegada"),
    )

    fun aMapa(p: Partida): Map<String, Any?> = mapOf(
        "id" to p.id, "categoria" to p.categoria.name, "ronda" to p.ronda, "posicion" to p.posicion,
        "cancha" to p.cancha, "turno" to p.turno, "equipoAId" to p.equipoAId, "equipoBId" to p.equipoBId,
        "esBye" to p.esBye, "estado" to p.estado.name, "siguientePartidaId" to p.siguientePartidaId,
        "siguienteLado" to p.siguienteLado?.name, "ganadorId" to p.ganadorId,
        "golesA" to p.golesA, "golesB" to p.golesB, "finalizadaEn" to p.finalizadaEn,
    )

    fun aPartida(m: Map<String, Any?>): Partida = Partida(
        id = long(m, "id"), categoria = ClaveCategoria(str(m, "categoria")),
        ronda = int(m, "ronda"), posicion = int(m, "posicion"),
        cancha = intOrNull(m, "cancha"), turno = intOrNull(m, "turno"),
        equipoAId = longOrNull(m, "equipoAId"), equipoBId = longOrNull(m, "equipoBId"),
        esBye = m["esBye"] as Boolean, estado = EstadoPartida.valueOf(str(m, "estado")),
        siguientePartidaId = longOrNull(m, "siguientePartidaId"),
        siguienteLado = strOrNull(m, "siguienteLado")?.let { Lado.valueOf(it) },
        ganadorId = longOrNull(m, "ganadorId"), golesA = intOrNull(m, "golesA"), golesB = intOrNull(m, "golesB"),
        finalizadaEn = longOrNull(m, "finalizadaEn"),
    )

    fun aMapa(s: Sorteo): Map<String, Any?> = mapOf(
        "id" to s.id, "categoria" to s.categoria.name, "tipo" to s.tipo.name, "semilla" to s.semilla, "creadoEn" to s.creadoEn,
    )

    fun aSorteo(m: Map<String, Any?>): Sorteo = Sorteo(
        id = long(m, "id"), categoria = ClaveCategoria(str(m, "categoria")),
        tipo = TipoSorteo.valueOf(str(m, "tipo")), semilla = long(m, "semilla"), creadoEn = long(m, "creadoEn"),
    )

    fun aMapa(i: Intento): Map<String, Any?> = mapOf(
        "id" to i.id, "categoria" to i.categoria.name, "equipoId" to i.equipoId, "numero" to i.numero,
        "pista" to i.pista, "tiempoMs" to i.tiempoMs, "resultado" to i.resultado.name,
        "motivo" to i.motivo, "capturadoEn" to i.capturadoEn,
        "penalizaciones" to i.penalizaciones, "penalizacionMs" to i.penalizacionMs,
    )

    fun aIntento(m: Map<String, Any?>): Intento = Intento(
        id = long(m, "id"), categoria = ClaveCategoria(str(m, "categoria")),
        equipoId = long(m, "equipoId"), numero = int(m, "numero"), pista = intOrNull(m, "pista"),
        tiempoMs = longOrNull(m, "tiempoMs"), resultado = ResultadoIntento.valueOf(str(m, "resultado")),
        motivo = strOrNull(m, "motivo"), capturadoEn = long(m, "capturadoEn"),
        penalizaciones = intOrNull(m, "penalizaciones") ?: 0, penalizacionMs = longOrNull(m, "penalizacionMs") ?: 0L,
    )

    fun aMapa(e: EventoAuditoria): Map<String, Any?> = mapOf(
        "id" to e.id, "actor" to e.actor, "accion" to e.accion, "entidad" to e.entidad,
        "entidadId" to e.entidadId, "detalle" to e.detalle, "creadoEn" to e.creadoEn,
    )

    fun aEvento(m: Map<String, Any?>): EventoAuditoria = EventoAuditoria(
        id = long(m, "id"), actor = str(m, "actor"), accion = str(m, "accion"), entidad = str(m, "entidad"),
        entidadId = longOrNull(m, "entidadId"), detalle = strOrNull(m, "detalle"), creadoEn = long(m, "creadoEn"),
    )

    fun aMapa(t: Terminos): Map<String, Any?> = mapOf("version" to t.version, "reglas" to t.reglas, "publicadoEn" to t.publicadoEn)
    fun aTerminos(m: Map<String, Any?>): Terminos = Terminos(
        version = int(m, "version"),
        reglas = (m["reglas"] as? List<*>)?.map { it.toString() } ?: emptyList(),
        publicadoEn = long(m, "publicadoEn"),
    )

    fun aMapa(a: AceptacionTerminos): Map<String, Any?> =
        mapOf("id" to a.id, "equipoId" to a.equipoId, "version" to a.version, "aceptadoEn" to a.aceptadoEn)
    fun aAceptacion(m: Map<String, Any?>): AceptacionTerminos = AceptacionTerminos(
        id = long(m, "id"), equipoId = long(m, "equipoId"), version = int(m, "version"), aceptadoEn = long(m, "aceptadoEn"),
    )

    fun aMapa(c: Categoria): Map<String, Any?> = mapOf(
        "clave" to c.clave.valor, "nombre" to c.nombre, "metodo" to c.metodo.name,
        "pesoMinG" to c.pesoMinG, "pesoMaxG" to c.pesoMaxG, "tamanoMaxCm" to c.tamanoMaxCm,
        "cerrada" to c.cerrada, "intentosMaximos" to c.intentosMaximos, "canchas" to c.canchas, "orden" to c.orden,
        "maxParticipantes" to c.maxParticipantes,
    )

    /** Los documentos viejos no traen nombre, método, intentos ni canchas: se completan con la categoría base. */
    fun aCategoria(m: Map<String, Any?>): Categoria {
        val clave = ClaveCategoria(str(m, "clave"))
        val base = CategoriasBase.de(clave)
        return Categoria(
            clave = clave,
            nombre = strOrNull(m, "nombre") ?: base.nombre,
            metodo = strOrNull(m, "metodo")?.let { Metodo.valueOf(it) } ?: base.metodo,
            pesoMinG = intOrNull(m, "pesoMinG"), pesoMaxG = intOrNull(m, "pesoMaxG"),
            tamanoMaxCm = doubleOrNull(m, "tamanoMaxCm"),
            cerrada = m["cerrada"] as? Boolean ?: false,
            intentosMaximos = intOrNull(m, "intentosMaximos") ?: base.intentosMaximos,
            canchas = intOrNull(m, "canchas") ?: base.canchas,
            orden = intOrNull(m, "orden") ?: base.orden,
            maxParticipantes = intOrNull(m, "maxParticipantes") ?: base.maxParticipantes,
        )
    }
}
