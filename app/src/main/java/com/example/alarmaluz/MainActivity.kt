package com.example.alarmaluz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Una sola Activity; las pantallas son destinos de Navigation Compose
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LuzAlarmaTheme {
                Box(Modifier.fillMaxSize().background(C.Fondo)) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val nav = rememberNavController()
    // Si ya hay sesión iniciada, salta el login
    val inicio = if (Repo.usuario != null) "inicio" else "login"

    NavHost(navController = nav, startDestination = inicio) {
        composable("login") {
            LoginScreen(alIngresar = {
                // popUpTo: borra el login del historial, así "atrás" no vuelve a él
                nav.navigate("inicio") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("inicio") { InicioTemporal(nav) }
    }
}

// Pantalla provisoria para probar el login. En la parte 2 se reemplaza.
@Composable
fun InicioTemporal(nav: NavController) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sesión iniciada como ${Repo.usuario?.email}")
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            Repo.cerrarSesion()
            nav.navigate("login") { popUpTo("inicio") { inclusive = true } }
        }) { Text("Cerrar sesión") }
    }
}
