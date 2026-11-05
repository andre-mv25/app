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

    /** Quién inició sesión con Google, o null si entró con PIN o es participante. */
    var usuarioActual: Usuario? = null
        private set

    /** Abre la sesión de alguien que ya se identificó (por ejemplo con Google) y tiene [rol]. */
    fun entrarComo(usuario: Usuario, rol: Rol) {
        usuarioActual = usuario
        rolActual = rol
    }

    /** Hay un solo PIN, el mismo para admin y jurado. Se guarda con la llave del admin. */
    fun pinConfigurado(): Boolean = store.obtenerHash(Rol.ADMIN) != null

    /**
     * Define o cambia el PIN de acceso. La primera vez no pide sesión; después solo lo puede cambiar
     * quien ya entró como admin (con Google o con el PIN). Si se perdió el PIN, el admin entra con
     * Google y define uno nuevo.
     */
    fun configurarPin(pin: String): Resultado<Unit> {
        if (!pin.matches(Regex("\\d{4,8}"))) {
            return falla(Codigo.VALIDACION, "El PIN debe tener de 4 a 8 dígitos", "pin")
        }
        if (pinConfigurado() && rolActual != Rol.ADMIN) {
            return falla(Codigo.NO_PERMITIDO, "Solo el admin puede cambiar el PIN")
        }
        store.guardarHash(Rol.ADMIN, crearHash(pin))
        return Resultado.Ok(Unit)
    }

    /** Entra como [rol] con el PIN de acceso. Devuelve false si el PIN no existe o no coincide. */
    fun entrar(rol: Rol, pin: String): Boolean {
        if (rol == Rol.PARTICIPANTE) {
            usuarioActual = null
            rolActual = Rol.PARTICIPANTE
            return true
        }
        val guardado = store.obtenerHash(Rol.ADMIN) ?: return false
        if (!verificar(pin, guardado)) return false
        usuarioActual = null
        rolActual = rol
        return true
    }

    fun salir() {
        usuarioActual = null
        rolActual = Rol.PARTICIPANTE
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
