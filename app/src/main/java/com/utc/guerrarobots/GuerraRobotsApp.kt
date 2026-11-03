package com.utc.guerrarobots

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.utc.guerrarobots.di.AppContainer

class GuerraRobotsApp : Application() {
    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(FirebaseFirestore.getInstance())
    }
}
