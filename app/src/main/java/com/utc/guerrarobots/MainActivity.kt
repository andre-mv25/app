package com.utc.guerrarobots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.utc.guerrarobots.ui.GuerraRobotsNavegacion
import com.utc.guerrarobots.ui.theme.GuerraRobotsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contenedor = (application as GuerraRobotsApp).contenedor
        enableEdgeToEdge()
        setContent {
            GuerraRobotsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GuerraRobotsNavegacion(contenedor, Modifier.padding(innerPadding))
                }
            }
        }
    }
}
