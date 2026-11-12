package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Acciones
import com.utc.guerrarobots.domain.model.Categoria
import com.utc.guerrarobots.domain.model.ClaveCategoria
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.ConfigTorneo
import com.utc.guerrarobots.domain.model.Equipo
import com.utc.guerrarobots.domain.model.EstadoEquipo
import com.utc.guerrarobots.domain.model.Intento
import com.utc.guerrarobots.domain.model.Metodo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.ResultadoIntento
import com.utc.guerrarobots.domain.model.Sorteo
import com.utc.guerrarobots.domain.model.TipoSorteo
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.CatalogoRepository
import com.utc.guerrarobots.domain.repository.EquipoRepository
import com.utc.guerrarobots.domain.repository.IntentoRepository
import com.utc.guerrarobots.domain.repository.Reloj
import com.utc.guerrarobots.domain.repository.SorteoRepository
import com.utc.guerrarobots.domain.repository.Transaccion
import com.utc.guerrarobots.domain.service.AuditoriaService
import com.utc.guerrarobots.domain.rules.FilaPosicion
import com.utc.guerrarobots.domain.rules.Ranking
import com.utc.guerrarobots.domain.rules.Tiempo
import kotlin.random.Random

/** Categorías por tiempo (sigue líneas, laberinto, velocidad...): orden de salida, captura de intentos, carrera extra y tabla de tiempos. */
class TiemposService(
    private val equipos: EquipoRepository,
    private val intentos: IntentoRepository,
    private val sorteos: SorteoRepository,
    private val catalogo: CatalogoRepository,
    private val tx: Transaccion,
    private val reloj: Reloj,
    private val config: ConfigTorneo = ConfigTorneo(),
    private val auditoria: AuditoriaService? = null,
) {

    data class TurnoSalida(val equipo: Equipo, val turno: Int, val pista: Int)

    data class PosicionVista(val fila: FilaPosicion, val equipo: Equipo)

    /**
     * Cómo va un equipo: sus intentos, cuántos le quedan y si le toca una carrera extra.
     * Se ordena como el orden de salida si ya se sorteó.
     */
    data class AvanceEquipo(
        val equipo: Equipo,
        val intentos: List<Intento>,
        val restantes: Int,
        val carreraExtraPendiente: Boolean,
        val turno: Int?,
        val pista: Int?,
    )

    private class DatosIntento(val tiempoMs: Long?, val motivo: String?, val penalizaciones: Int, val penalizacionMs: Long)

    /** Falla si la categoría no se clasifica por tiempo. */
    private suspend fun soloTiempo(categoria: ClaveCategoria): Resultado.Falla? {
        val c = catalogo.categoria(categoria)
        return if (c.metodo != Metodo.RANKING_TIEMPO) {
            falla(Codigo.VALIDACION, "${c.nombre} se clasifica por llaves, no por tiempo", "categoria")
        } else {
            null
        }
    }

    /** Tiempo que suma cada penalización. */
    val penalizacionMs: Long get() = config.penalizacionMs

    suspend fun estaCerrada(categoria: ClaveCategoria): Boolean = catalogo.categoria(categoria).cerrada

    /**
     * Sortea el orden de salida de los equipos aceptados y los reparte por turnos entre las
     * pistas.
     * Conviene sortear cuando ya cerró el registro: si después se acepta otro equipo, el orden cambia.
     */
    suspend fun generarOrden(categoria: ClaveCategoria, semilla: Long? = null): Resultado<List<TurnoSalida>> =
        tx.ejecutar {
            soloTiempo(categoria)?.let { return@ejecutar it }
            if (catalogo.categoria(categoria).cerrada) {
                return@ejecutar falla(Codigo.CONFLICTO, "La categoría ya está cerrada")
            }
            val usada = semilla ?: reloj.ahora()
            val orden = calcularOrden(categoria, usada)
            if (orden.isEmpty()) {
                return@ejecutar falla(Codigo.VALIDACION, "No hay equipos aceptados en ${catalogo.categoria(categoria).nombre}")
            }
            sorteos.insertar(Sorteo(categoria = categoria, tipo = TipoSorteo.ALEATORIO, semilla = usada, creadoEn = reloj.ahora()))
            auditoria?.registrar(
                Acciones.ORDEN_SORTEADO, "categoria", null,
                "${catalogo.categoria(categoria).nombre} · ${orden.size} equipos · semilla $usada",
                categoria,
            )
            Resultado.Ok(orden)
        }

    /** El último orden de salida sorteado, calculado otra vez con su semilla. */
    suspend fun ordenSalida(categoria: ClaveCategoria): Resultado<List<TurnoSalida>> {
        soloTiempo(categoria)?.let { return it }
        val sorteo = sorteos.ultimo(categoria)
            ?: return falla(Codigo.NO_ENCONTRADO, "Todavía no se sortea el orden de salida")
        return Resultado.Ok(calcularOrden(categoria, sorteo.semilla))
    }

    private suspend fun calcularOrden(categoria: ClaveCategoria, semilla: Long): List<TurnoSalida> {
        val aceptados = equipos.listar(EstadoEquipo.ACEPTADO, categoria).sortedBy { it.id }
        val pistas = catalogo.categoria(categoria).canchas.coerceAtLeast(1)
        return aceptados.shuffled(Random(semilla)).mapIndexed { i, equipo ->
            TurnoSalida(equipo, turno = i / pistas + 1, pista = i % pistas + 1)
        }
    }

    /** Valida lo que captura el jurado según el resultado y devuelve el tiempo y el motivo ya limpios. */
    private fun datosDelIntento(
        resultado: ResultadoIntento,
        minutos: Int,
        segundos: Int,
        milesimas: Int,
        motivo: String?,
        penalizaciones: Int,
    ): Resultado<DatosIntento> {
        if (penalizaciones < 0 || penalizaciones > config.maxPenalizaciones) {
            return falla(Codigo.VALIDACION, "Las penalizaciones van de 0 a ${config.maxPenalizaciones}", "penalizaciones")
        }
        if (penalizaciones > 0 && resultado != ResultadoIntento.VALIDO) {
            return falla(Codigo.VALIDACION, "Solo un intento válido lleva penalizaciones", "penalizaciones")
        }
        return when (resultado) {
            ResultadoIntento.VALIDO -> {
                if (!Tiempo.esValido(minutos, segundos, milesimas)) {
                    falla(Codigo.VALIDACION, "Tiempo no válido: segundos de 0 a 59 y milésimas de 0 a 999", "tiempo")
                } else {
                    val ms = Tiempo.aMilisegundos(minutos, segundos, milesimas)
                    if (ms <= 0) falla(Codigo.VALIDACION, "El tiempo debe ser mayor que cero", "tiempo")
                    else Resultado.Ok(DatosIntento(ms, null, penalizaciones, penalizaciones * config.penalizacionMs))
                }
            }
            ResultadoIntento.DESCALIFICADO ->
                if (motivo.isNullOrBlank()) falla(Codigo.VALIDACION, "Escribe el motivo de la descalificación", "motivo")
                else Resultado.Ok(DatosIntento(null, motivo.trim(), 0, 0))
            ResultadoIntento.NO_TERMINO -> Resultado.Ok(DatosIntento(null, null, 0, 0))
        }
    }

    /**
     * Guarda un intento regular del jurado.
     * - VALIDO: lleva minutos, segundos (0 a 59) y milésimas (0 a 999); el tiempo debe ser mayor que cero.
     * - NO_TERMINO: el intento no cuenta.
     * - DESCALIFICADO: lleva el motivo y el intento se anula.
     * Cada equipo tiene como máximo [Categoria.intentosMaximos] intentos regulares.
     */
    suspend fun registrarIntento(
        categoria: ClaveCategoria,
        equipoId: Long,
        resultado: ResultadoIntento,
        minutos: Int = 0,
        segundos: Int = 0,
        milesimas: Int = 0,
        pista: Int? = null,
        motivo: String? = null,
        penalizaciones: Int = 0,
    ): Resultado<Intento> = tx.ejecutar {
        soloTiempo(categoria)?.let { return@ejecutar it }
        val info = catalogo.categoria(categoria)
        if (info.cerrada) {
            return@ejecutar falla(Codigo.CONFLICTO, "La categoría ya está cerrada")
        }
        val equipo = equipoAceptado(categoria, equipoId) ?: return@ejecutar falla(
            Codigo.VALIDACION, "El equipo no está aceptado en ${info.nombre}", "equipoId",
        )
        val regulares = intentos.listarPorEquipo(categoria, equipo.id).filter { it.numero <= info.intentosMaximos }
        if (regulares.size >= info.intentosMaximos) {
            return@ejecutar falla(Codigo.CONFLICTO, "El equipo ya usó sus ${info.intentosMaximos} intentos")
        }
        if (pista != null && pista !in 1..info.canchas) {
            return@ejecutar falla(Codigo.VALIDACION, "Esa pista no existe en ${info.nombre}", "pista")
        }
        val datos = when (val r = datosDelIntento(resultado, minutos, segundos, milesimas, motivo, penalizaciones)) {
            is Resultado.Ok -> r.valor
            is Resultado.Falla -> return@ejecutar r
        }
        // Toma el primer número libre: si se anuló el intento 1, el siguiente vuelve a ser el 1.
        val numero = (1..info.intentosMaximos).first { n -> regulares.none { it.numero == n } }
        guardar(categoria, equipo, numero, pista, resultado, datos, esExtra = false)
    }

    /**
     * Carrera extra entre equipos que siguen empatados después del mejor y el segundo mejor tiempo.
     * Solo la puede correr un equipo con empate pendiente, cuando todos los empatados ya usaron
     * sus intentos regulares y a él le toca esa ronda. Gana el menor tiempo.
     */
    suspend fun registrarCarreraExtra(
        categoria: ClaveCategoria,
        equipoId: Long,
        resultado: ResultadoIntento,
        minutos: Int = 0,
        segundos: Int = 0,
        milesimas: Int = 0,
        pista: Int? = null,
        motivo: String? = null,
        penalizaciones: Int = 0,
    ): Resultado<Intento> = tx.ejecutar {
        soloTiempo(categoria)?.let { return@ejecutar it }
        val info = catalogo.categoria(categoria)
        if (info.cerrada) {
            return@ejecutar falla(Codigo.CONFLICTO, "La categoría ya está cerrada")
        }
        val equipo = equipoAceptado(categoria, equipoId) ?: return@ejecutar falla(
            Codigo.VALIDACION, "El equipo no está aceptado en ${info.nombre}", "equipoId",
        )
        if (equipo.id !in pendientesDeCarreraExtra(tabla(categoria), info.intentosMaximos)) {
            return@ejecutar falla(Codigo.CONFLICTO, "Ese equipo no tiene una carrera extra pendiente")
        }
        if (pista != null && pista !in 1..info.canchas) {
            return@ejecutar falla(Codigo.VALIDACION, "Esa pista no existe en ${info.nombre}", "pista")
        }
        val datos = when (val r = datosDelIntento(resultado, minutos, segundos, milesimas, motivo, penalizaciones)) {
            is Resultado.Ok -> r.valor
            is Resultado.Falla -> return@ejecutar r
        }
        val previos = intentos.listarPorEquipo(categoria, equipo.id)
        val numero = maxOf(previos.maxOfOrNull { it.numero } ?: 0, info.intentosMaximos) + 1
        guardar(categoria, equipo, numero, pista, resultado, datos, esExtra = true)
    }

    private suspend fun guardar(
        categoria: ClaveCategoria,
        equipo: Equipo,
        numero: Int,
        pista: Int?,
        resultado: ResultadoIntento,
        datos: DatosIntento,
        esExtra: Boolean,
    ): Resultado<Intento> {
        val intento = Intento(
            categoria = categoria,
            equipoId = equipo.id,
            numero = numero,
            pista = pista,
            tiempoMs = datos.tiempoMs,
            resultado = resultado,
            motivo = datos.motivo,
            capturadoEn = reloj.ahora(),
            penalizaciones = datos.penalizaciones,
            penalizacionMs = datos.penalizacionMs,
        )
        val id = intentos.insertar(intento)
        auditoria?.registrar(
            if (esExtra) Acciones.CARRERA_EXTRA_REGISTRADA else Acciones.INTENTO_REGISTRADO,
            "intento", id,
            "${equipo.folio} ${equipo.nombre} · ${if (esExtra) "carrera extra" else "intento $numero"} · ${describir(intento)}",
            intento.categoria,
        )
        return Resultado.Ok(intento.copy(id = id))
    }

    /** Texto corto del resultado de un intento, para la bitácora. */
    private fun describir(i: Intento): String = when (i.resultado) {
        ResultadoIntento.VALIDO -> {
            val bruto = Tiempo.formatear(i.tiempoMs ?: 0)
            if (i.penalizaciones > 0) {
                "${Tiempo.formatear(i.tiempoEfectivoMs ?: 0)} efectivo ($bruto + ${i.penalizaciones} pen.)"
            } else {
                bruto
            }
        }
        ResultadoIntento.NO_TERMINO -> "no terminó"
        ResultadoIntento.DESCALIFICADO -> "descalificado: ${i.motivo ?: "sin motivo"}"
    }

    /** Borra un intento mal capturado. No se puede con la categoría cerrada. */
    suspend fun anularIntento(categoria: ClaveCategoria, intentoId: Long): Resultado<Unit> = tx.ejecutar {
        soloTiempo(categoria)?.let { return@ejecutar it }
        if (catalogo.categoria(categoria).cerrada) {
            return@ejecutar falla(Codigo.CONFLICTO, "La categoría ya está cerrada")
        }
        val todos = intentos.listar(categoria)
        val intento = todos.firstOrNull { it.id == intentoId }
            ?: return@ejecutar falla(Codigo.NO_ENCONTRADO, "No existe ese intento", "intentoId")
        val maxIntentos = catalogo.categoria(categoria).intentosMaximos
        val tieneExtras = todos.any { it.equipoId == intento.equipoId && it.numero > maxIntentos }
        if (intento.numero <= maxIntentos && tieneExtras) {
            return@ejecutar falla(Codigo.CONFLICTO, "Primero anula las carreras extra de ese equipo")
        }
        intentos.eliminar(intentoId)
        val dueno = equipos.obtener(intento.equipoId)
        auditoria?.registrar(
            Acciones.INTENTO_ANULADO, "intento", intentoId,
            "${dueno?.folio.orEmpty()} ${dueno?.nombre ?: "equipo ${intento.equipoId}"} · " +
                "${if (intento.numero > maxIntentos) "carrera extra" else "intento ${intento.numero}"} · ${describir(intento)}",
            intento.categoria,
        )
        Resultado.Ok(Unit)
    }

    private suspend fun equipoAceptado(categoria: ClaveCategoria, equipoId: Long): Equipo? =
        equipos.obtener(equipoId)?.takeIf { it.estado == EstadoEquipo.ACEPTADO && it.robot.categoria == categoria }

    private suspend fun tabla(categoria: ClaveCategoria): List<FilaPosicion> {
        val aceptados = equipos.listar(EstadoEquipo.ACEPTADO, categoria).map { it.id }.toSet()
        val delJurado = intentos.listar(categoria).filter { it.equipoId in aceptados }
        return Ranking.calcular(delJurado, catalogo.categoria(categoria).intentosMaximos)
    }

    /**
     * Equipos a los que les toca correr carrera extra ahora: están empatados, todos los empatados
     * ya usaron sus intentos regulares, y todavía no corren la ronda de extras que sigue.
     */
    private fun pendientesDeCarreraExtra(filas: List<FilaPosicion>, maxIntentos: Int): Set<Long> {
        val pendientes = mutableSetOf<Long>()
        for ((_, grupo) in filas.filter { it.empate }.groupBy { it.lugar }) {
            val regularesCompletos = grupo.all { f -> f.intentos.count { it.numero <= maxIntentos } >= maxIntentos }
            if (!regularesCompletos) continue
            val extras = grupo.associate { f -> f.equipoId to f.intentos.count { it.numero > maxIntentos } }
            val minimo = extras.values.min()
            grupo.filter { extras.getValue(it.equipoId) == minimo }.forEach { pendientes += it.equipoId }
        }
        return pendientes
    }

    /** Todos los equipos aceptados con sus intentos, en orden de salida si ya se sorteó. */
    suspend fun avance(categoria: ClaveCategoria): List<AvanceEquipo> {
        val info = catalogo.categoria(categoria)
        if (info.metodo != Metodo.RANKING_TIEMPO) return emptyList()
        val aceptados = equipos.listar(EstadoEquipo.ACEPTADO, categoria).sortedBy { it.id }
        val todos = intentos.listar(categoria).groupBy { it.equipoId }
        val pendientes = pendientesDeCarreraExtra(tabla(categoria), info.intentosMaximos)
        val orden = (ordenSalida(categoria) as? Resultado.Ok)?.valor.orEmpty().associateBy { it.equipo.id }
        return aceptados.map { e ->
            val suyos = todos[e.id].orEmpty().sortedBy { it.numero }
            AvanceEquipo(
                equipo = e,
                intentos = suyos,
                restantes = (info.intentosMaximos - suyos.count { it.numero <= info.intentosMaximos }).coerceAtLeast(0),
                carreraExtraPendiente = e.id in pendientes,
                turno = orden[e.id]?.turno,
                pista = orden[e.id]?.pista,
            )
        }.sortedWith(compareBy({ it.turno ?: Int.MAX_VALUE }, { it.pista ?: Int.MAX_VALUE }, { it.equipo.id }))
    }

    /** La tabla de posiciones, con los equipos aceptados de la categoría que ya corrieron. */
    suspend fun posiciones(categoria: ClaveCategoria): List<PosicionVista> {
        if (catalogo.categoria(categoria).metodo != Metodo.RANKING_TIEMPO) return emptyList()
        val porId = equipos.listar(EstadoEquipo.ACEPTADO, categoria).associateBy { it.id }
        return tabla(categoria).map { PosicionVista(it, porId.getValue(it.equipoId)) }
    }

    /**
     * Cierra la categoría: ya no se aceptan intentos y queda definido el podio.
     * No se puede cerrar si hay un empate que afecta el podio (lugares 1 a 3).
     */
    suspend fun cerrar(categoria: ClaveCategoria): Resultado<Categoria> {
        soloTiempo(categoria)?.let { return it }
        if (tabla(categoria).any { it.empate && it.lugar <= 3 }) {
            return falla(Codigo.CONFLICTO, "Hay un empate por el podio: corre la carrera extra antes de cerrar")
        }
        val cerrada = catalogo.categoria(categoria).copy(cerrada = true)
        catalogo.guardarCategoria(cerrada)
        auditoria?.registrar(Acciones.CATEGORIA_CERRADA, "categoria", null, cerrada.nombre, categoria)
        return Resultado.Ok(cerrada)
    }

    /** Vuelve a abrir una categoría cerrada, por ejemplo para corregir un intento. */
    suspend fun reabrir(categoria: ClaveCategoria): Resultado<Categoria> {
        soloTiempo(categoria)?.let { return it }
        val abierta = catalogo.categoria(categoria).copy(cerrada = false)
        catalogo.guardarCategoria(abierta)
        auditoria?.registrar(Acciones.CATEGORIA_REABIERTA, "categoria", null, abierta.nombre, categoria)
        return Resultado.Ok(abierta)
    }

    /** Los tres mejores lugares (si hay empate en el tercero, entran todos). Solo con la categoría cerrada. */
    suspend fun podio(categoria: ClaveCategoria): Resultado<List<PosicionVista>> {
        soloTiempo(categoria)?.let { return it }
        if (!catalogo.categoria(categoria).cerrada) {
            return falla(Codigo.CONFLICTO, "El podio se define al cerrar la categoría")
        }
        return Resultado.Ok(posiciones(categoria).filter { !it.fila.sinTiempo && it.fila.lugar <= 3 })
    }

    /** Todos los intentos de todas las categorías. */
    suspend fun intentosTodas(): List<Intento> {
        val todas = mutableListOf<Intento>()
        catalogo.categorias().forEach { categoria ->
            todas.addAll(intentos.listar(categoria.clave))
        }
        return todas
    }
}
