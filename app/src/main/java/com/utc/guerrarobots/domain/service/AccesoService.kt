package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.model.Usuario
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.PinStore
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Acceso local por PIN. Cualquiera puede registrar su equipo (PARTICIPANTE); capturar
 * resultados exige el PIN del JURADO y configurar el torneo exige el del ADMIN.
 * Es una compuerta para la app, no seguridad de servidor: el PIN se guarda con sal y hash.
 */
class AccesoService(private val store: PinStore) {

    var rolActual: Rol = Rol.PARTICIPANTE
        private set

    /** Nombre del administrador con sesión activa. */
    var nombreAdminActual: String? = null
        private set

    /** Quién inició sesión con Google, o null si entró con PIN o es participante. */
    var usuarioActual: Usuario? = null
        private set

    /** Abre la sesión de alguien que ya se identificó (por ejemplo con Google) y tiene [rol]. */
    fun entrarComo(usuario: Usuario, rol: Rol) {
        usuarioActual = usuario
        rolActual = rol
        if (rol == Rol.ADMIN && nombreAdminActual == null) {
            nombreAdminActual = usuario.nombre?.ifBlank { "Admin Google" } ?: "Admin Google"
        }
    }

    /** Entra como un administrador de la lista oficial usando su PIN de 7 dígitos. */
    fun entrarComoAdmin(admin: com.utc.guerrarobots.domain.model.AdminPerfil, pin: String): Boolean {
        if (admin.pin == pin.trim()) {
            usuarioActual = null
            rolActual = Rol.ADMIN
            nombreAdminActual = admin.nombre
            return true
        }
        return false
    }

    /** El admin y el jurado tienen cada uno su PIN. Dice si el de [rol] ya está definido. */
    fun pinConfigurado(rol: Rol = Rol.ADMIN): Boolean = store.obtenerHash(rol) != null || rol == Rol.ADMIN

    /**
     * Define o cambia el PIN de [rol] (ADMIN o JURADO). Los dos PIN deben ser distintos. El primer
     * PIN del admin no pide sesión; todos los demás cambios los hace solo quien ya entró como admin
     * (con Google o con su PIN). Si se perdió un PIN, el admin entra con Google y define uno nuevo.
     */
    fun configurarPin(pin: String, rol: Rol = Rol.ADMIN): Resultado<Unit> {
        if (rol != Rol.ADMIN && rol != Rol.JURADO) {
            return falla(Codigo.VALIDACION, "Ese rol no usa PIN")
        }
        if (!pin.matches(Regex("\\d{4,8}"))) {
            return falla(Codigo.VALIDACION, "El PIN debe tener de 4 a 8 dígitos", "pin")
        }
        val primerPinDelAdmin = rol == Rol.ADMIN && !pinConfigurado(Rol.ADMIN)
        if (!primerPinDelAdmin && rolActual != Rol.ADMIN) {
            return falla(Codigo.NO_PERMITIDO, "Solo el admin puede cambiar los PIN")
        }
        val otro = if (rol == Rol.ADMIN) Rol.JURADO else Rol.ADMIN
        val hashOtro = store.obtenerHash(otro)
        if (hashOtro != null && verificar(pin, hashOtro)) {
            return falla(Codigo.VALIDACION, "El PIN del admin y el del jurado deben ser distintos", "pin")
        }
        store.guardarHash(rol, crearHash(pin))
        return Resultado.Ok(Unit)
    }

    /** Entra como [rol] con su PIN. Devuelve false si ese rol no tiene PIN o no coincide. */
    fun entrar(rol: Rol, pin: String): Boolean {
        if (rol == Rol.PARTICIPANTE) {
            usuarioActual = null
            rolActual = Rol.PARTICIPANTE
            nombreAdminActual = null
            return true
        }
        if (rol == Rol.ADMIN) {
            val adminEncontrado = com.utc.guerrarobots.domain.model.AdminsLista.buscarPorPin(pin)
            if (adminEncontrado != null) {
                usuarioActual = null
                rolActual = Rol.ADMIN
                nombreAdminActual = adminEncontrado.nombre
                return true
            }
        }
        val guardado = store.obtenerHash(rol)
        if (guardado != null && verificar(pin, guardado)) {
            usuarioActual = null
            rolActual = rol
            if (rol == Rol.ADMIN && nombreAdminActual == null) {
                nombreAdminActual = "Admin General"
            }
            return true
        }
        return false
    }

    fun salir() {
        usuarioActual = null
        rolActual = Rol.PARTICIPANTE
        nombreAdminActual = null
    }

    /** El admin puede todo; el jurado puede lo del jurado y lo del participante. */
    fun puede(requerido: Rol): Boolean = rolActual.ordinal >= requerido.ordinal

    private fun crearHash(pin: String): String {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        return "$sal$${sha256("$sal:$pin")}"
    }

    private fun verificar(pin: String, guardado: String): Boolean {
        val partes = guardado.split('$')
        if (partes.size != 2) return false
        val esperado = sha256("${partes[0]}:$pin")
        return partes[1].equals(esperado, ignoreCase = true)
    }

    private fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
