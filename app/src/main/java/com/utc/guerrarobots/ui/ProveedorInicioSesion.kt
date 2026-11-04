package com.utc.guerrarobots.ui

import android.content.Context
import com.utc.guerrarobots.domain.model.Usuario

/** El usuario cerró la ventana de Google sin elegir cuenta. No es un error: no se muestra nada. */
class SesionCancelada : Exception()

/**
 * Cómo se identifica una persona. La pantalla solo conoce esta interfaz: quién la implementa
 * (Google con Firebase Auth hoy) se decide en AppContainer.
 */
interface ProveedorInicioSesion {
    /** Abre el selector de cuenta y devuelve a quien entró. Lanza [SesionCancelada] si cancela. */
    suspend fun iniciar(contexto: Context): Usuario

    suspend fun cerrar(contexto: Context)
}
