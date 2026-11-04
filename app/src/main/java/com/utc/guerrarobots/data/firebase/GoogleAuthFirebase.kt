package com.utc.guerrarobots.data.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.utc.guerrarobots.domain.model.Usuario
import com.utc.guerrarobots.ui.ProveedorInicioSesion
import com.utc.guerrarobots.ui.SesionCancelada
import kotlinx.coroutines.tasks.await

/**
 * Inicio de sesión con Google (Credential Manager) y Firebase Auth. [webClientId] es el
 * "default_web_client_id" que genera el plugin de google-services a partir de google-services.json.
 */
class GoogleAuthFirebase(
    private val auth: FirebaseAuth,
    private val webClientId: String,
) : ProveedorInicioSesion {

    override suspend fun iniciar(contexto: Context): Usuario {
        val opcion = GetSignInWithGoogleOption.Builder(webClientId).build()
        val peticion = GetCredentialRequest.Builder().addCredentialOption(opcion).build()
        val respuesta = try {
            CredentialManager.create(contexto).getCredential(contexto, peticion)
        } catch (e: GetCredentialCancellationException) {
            throw SesionCancelada()
        } catch (e: GetCredentialException) {
            throw IllegalStateException("No se pudo iniciar con Google: ${e.message ?: e.type}", e)
        }

        val credencial = respuesta.credential
        if (credencial !is CustomCredential || credencial.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Google no devolvió una cuenta válida")
        }
        val token = GoogleIdTokenCredential.createFrom(credencial.data).idToken
        val usuario = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
            ?: throw IllegalStateException("Firebase no devolvió el usuario")
        val correo = usuario.email ?: throw IllegalStateException("La cuenta de Google no tiene correo")
        return Usuario(uid = usuario.uid, correo = correo, nombre = usuario.displayName)
    }

    override suspend fun cerrar(contexto: Context) {
        auth.signOut()
        try {
            CredentialManager.create(contexto).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Si no se pudo limpiar el estado de Google, la sesión de Firebase ya está cerrada.
        }
    }
}
