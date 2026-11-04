package com.utc.guerrarobots

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.utc.guerrarobots.data.firebase.GoogleAuthFirebase
import com.utc.guerrarobots.di.AppContainer

class GuerraRobotsApp : Application() {
    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(
            db = FirebaseFirestore.getInstance(),
            inicioSesion = GoogleAuthFirebase(
                auth = FirebaseAuth.getInstance(),
                webClientId = getString(R.string.default_web_client_id),
            ),
        )
    }
}
