package com.example.alarmaluz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Una sola Activity; las pantallas son destinos de Navigation Compose
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.iniciar(this)
        setContent {
            LuzAlarmaTheme {
                Box(Modifier.fillMaxSize().background(C.Fondo)) { App() }
            }
        }
    }
}

// A dónde ir: sin sesión -> login; sin rol en este celular -> elegir rol; si no, la pantalla de su rol
fun destino(): String = when {
    Repo.usuario == null -> "login"
    Repo.rol == null -> "rol"
    else -> Repo.rol!!
}

// Navega borrando el historial (para que "atrás" no vuelva al login o a otro rol)
fun NavController.irLimpio(ruta: String) = navigate(ruta) { popUpTo(graph.id) { inclusive = true } }

@Composable
fun App() {
    val nav = rememberNavController()
    val inicio = remember { destino() }

    NavHost(navController = nav, startDestination = inicio) {
        composable("login") { LoginScreen(alIngresar = { nav.irLimpio(destino()) }) }
        composable("rol") { RolScreen(alElegir = { nav.irLimpio(destino()) }) }
        // Celular Activador
        composable("activador") { ActivadorScreen(nav, alCambiarRol = { Repo.registrarDispositivo(); nav.irLimpio(destino()) }) }
        composable("conexiones") { ConexionesScreen(nav, alSalir = { nav.irLimpio("login") }) }
        // Celular Monitoreo
        composable("monitoreo") { MonitoreoScreen(nav) }
        composable("historial") { HistorialScreen(nav) }
        composable("config") {
            ConfigScreen(nav,
                alSalir = { nav.irLimpio("login") },
                alCambiarRol = { Repo.registrarDispositivo(); nav.irLimpio(destino()) })
        }
    }
}
