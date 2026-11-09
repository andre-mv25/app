package com.utc.guerrarobots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.utc.guerrarobots.ui.GuerraRobotsNavegacion
import com.utc.guerrarobots.ui.theme.FondoRobots
import com.utc.guerrarobots.ui.theme.GuerraRobotsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contenedor = (application as GuerraRobotsApp).contenedor
        // Fondo siempre oscuro: los íconos de la barra de estado y de navegación van en claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GuerraRobotsTheme {
                FondoRobots {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        // Sin esto el texto salía negro: con fondo transparente Scaffold no sabe qué color de texto usar.
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) { innerPadding ->
                        GuerraRobotsNavegacion(contenedor, Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}
