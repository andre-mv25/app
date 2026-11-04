package com.utc.guerrarobots.domain.service

import com.utc.guerrarobots.domain.model.Codigo
import com.utc.guerrarobots.domain.model.Resultado
import com.utc.guerrarobots.domain.model.Rol
import com.utc.guerrarobots.domain.model.Usuario
import com.utc.guerrarobots.domain.model.falla
import com.utc.guerrarobots.domain.repository.AutorizacionRepository

/**
 * Inicio de sesión de jurado y admin con una cuenta ya identificada (Google). Quien puede entrar
 * y con qué rol lo decide la lista de autorizados, no la cuenta: tener un correo de Google no basta.
 */
class SesionService(
    private val autorizados: AutorizacionRepository,
    private val acceso: AccesoService,
) {
    /** Abre la sesión si el correo está autorizado. Devuelve el rol que le toca. */
    suspend fun entrar(usuario: Usuario): Resultado<Rol> {
        val correo = usuario.correo.trim().lowercase()
        val rol = autorizados.rolDe(correo)
        if (rol == null || rol == Rol.PARTICIPANTE) {
            return falla(Codigo.NO_PERMITIDO, "El correo $correo no está autorizado como jurado ni admin")
        }
        acceso.entrarComo(usuario.copy(correo = correo), rol)
        return Resultado.Ok(rol)
    }
}
