package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.AdminPerfil
import com.utc.guerrarobots.domain.model.AdminsLista
import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.JuradoPerfil
import com.utc.guerrarobots.domain.model.JuradosLista
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
 * Cada admin y cada jurado tiene su PIN individual, asociado a su identificador.
 */
class AccesoService(private val store: PinStore) {

    var rolActual: Rol = Rol.PARTICIPANTE
        private set

    /** Nombre del administrador o jurado con sesión activa. */
    var nombreActual: String? = null
        private set

    /** Correo/identificador de la sesión activa. */
    var correoActual: String? = null
        private set

    /** Quién inició sesión con Google, o null si entró con PIN o es participante. */
    var usuarioActual: Usuario? = null
        private set

    /** Abre la sesión de alguien que ya se identificó (por ejemplo con Google) y tiene [rol]. */
    fun entrarComo(usuario: Usuario, rol: Rol) {
        usuarioActual = usuario
        correoActual = usuario.correo
        rolActual = rol
        if (rol == Rol.ADMIN) {
            nombreActual = usuario.nombre?.ifBlank { "Admin" } ?: "Admin"
        } else if (rol == Rol.JURADO) {
            nombreActual = usuario.nombre ?: "Jurado"
        }
    }

    /** Dice si el [correo] ya tiene PIN definido para [rol]. */
    fun pinConfigurado(correo: String, rol: Rol): Boolean = store.obtenerHash(correo, rol) != null

    /**
     * Define o cambia el PIN de [correo] para [rol] (ADMIN o JURADO). Los dos PIN deben ser
     * distintos para una misma persona. El primer PIN no pide sesión; los demás cambios los hace
     * solo quien ya entró como admin (con Google o con su PIN).
     */
    fun configurarPin(correo: String, rol: Rol, pin: String): Resultado<Unit> {
        if (rol != Rol.ADMIN && rol != Rol.JURADO) {
            return falla(Codigo.VALIDACION, "Ese rol no usa PIN", "rol")
        }
        if (!pin.matches(Regex("\\d{4,8}"))) {
            return falla(Codigo.VALIDACION, "El PIN debe tener de 4 a 8 dígitos", "pin")
        }
        val esPrimeraVez = !pinConfigurado(correo, rol)
        if (!esPrimeraVez && rolActual != Rol.ADMIN && correoActual != correo) {
            return falla(Codigo.NO_PERMITIDO, "Solo el admin puede cambiar el PIN de otra persona")
        }
        val otro = if (rol == Rol.ADMIN) Rol.JURADO else Rol.ADMIN
        val hashOtro = store.obtenerHash(correo, otro)
        if (hashOtro != null && verificar(pin, hashOtro)) {
            return falla(Codigo.VALIDACION, "El PIN de admin y el de jurado deben ser distintos", "pin")
        }
        store.guardarHash(correo, rol, crearHash(pin))
        return Resultado.Ok(Unit)
    }

    /**
     * Entra como [rol] con [pin].
     * Primero busca en las listas oficiales (AdminsLista / JuradosLista) por PIN;
     * si no encuentra, busca en PinStore por correo + rol.
     * Devuelve true si entró correctamente.
     */
    fun entrar(correo: String, rol: Rol, pin: String): Boolean {
        if (rol == Rol.PARTICIPANTE) {
            usuarioActual = null
            correoActual = null
            nombreActual = null
            rolActual = Rol.PARTICIPANTE
            return true
        }

        // Primero: buscar en listas oficiales por PIN (funciona sin correo)
        if (rol == Rol.ADMIN) {
            val admin = AdminsLista.buscarPorPin(pin)
            if (admin != null) {
                usuarioActual = Usuario(admin.id, admin.nombre, admin.nombre)
                correoActual = admin.id
                nombreActual = admin.nombre
                rolActual = Rol.ADMIN
                return true
            }
        }
        if (rol == Rol.JURADO) {
            val jurado = JuradosLista.buscarPorPin(pin)
            if (jurado != null) {
                usuarioActual = Usuario(jurado.id, jurado.nombre, jurado.nombre)
                correoActual = jurado.id
                nombreActual = jurado.nombre
                rolActual = Rol.JURADO
                return true
            }
        }

        // Segundo: buscar en PinStore por correo + rol
        val guardado = store.obtenerHash(correo, rol)
        if (guardado != null && verificar(pin, guardado)) {
            usuarioActual = Usuario(correo, correo, correo)
            correoActual = correo
            nombreActual = correo
            rolActual = rol
            return true
        }

        return false
    }

    /** Entra como [rol] con [pin] sin necesidad de correo (usa listas oficiales). */
    fun entrarPorPin(rol: Rol, pin: String): Boolean {
        return entrar("", rol, pin)
    }

    fun salir() {
        usuarioActual = null
        correoActual = null
        nombreActual = null
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
