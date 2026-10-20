package com.example.alarmaluz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Piezas de interfaz que se repiten en varias pantallas

data class Pestana(val ruta: String, val texto: String, val icono: ImageVector)

// Estructura común: contenido con scroll + barra de navegación inferior (Material 3)
@Composable
fun Pantalla(nav: NavController, actual: String, pestanas: List<Pestana>, contenido: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = C.Fondo,
        bottomBar = {
            NavigationBar(containerColor = C.Barra) {
                pestanas.forEach { p ->
                    NavigationBarItem(
                        selected = p.ruta == actual,
                        onClick = {
                            // popUpTo: vuelve al inicio de la pestaña sin apilar pantallas repetidas
                            if (p.ruta != actual) nav.navigate(p.ruta) { popUpTo(pestanas[0].ruta); launchSingleTop = true }
                        },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.texto) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Ambar, selectedTextColor = C.Ambar, indicatorColor = C.Tarjeta,
                            unselectedIconColor = C.Gris, unselectedTextColor = C.Gris
                        )
                    )
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            contenido()
            Spacer(Modifier.height(12.dp))
        }
    }
}

// Tarjeta del mockup: fondo azul, esquinas de 20 dp
@Composable
fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = C.Tarjeta)
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido) }
}

// Etiqueta pequeña de color (ARMADA, En línea, etc.)
@Composable
fun Chip(texto: String, color: Color, fondo: Color) {
    Surface(color = fondo, shape = RoundedCornerShape(16.dp)) {
        Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

// Título de pantalla con subtítulo y un chip opcional a la derecha
@Composable
fun Encabezado(sub: String, titulo: String, chip: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(sub, color = C.Gris, fontSize = 13.sp)
            Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        chip()
    }
}

// Chip que avisa si se perdió internet (criterio de estabilidad)
@Composable
fun ChipConexion(enLinea: Boolean, textoOk: String) =
    if (enLinea) Chip("● $textoOk", C.Verde, C.VerdeFondo) else Chip("● Sin conexión", C.Rojo, Color(0xFF3A1D1D))

// Hora actual que se actualiza sola (para "hace 5 s", "faltan 2 h")
@Composable
fun ahora(cadaMs: Long = 1000): Long {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cadaMs) { while (true) { t = System.currentTimeMillis(); delay(cadaMs) } }
    return t
}

// ---------- Formatos ----------
private val ES = Locale("es", "CL")
fun hora(ms: Long): String = SimpleDateFormat("HH:mm", ES).format(Date(ms))
fun dia(ms: Long): String = SimpleDateFormat("EEE d MMM", ES).format(Date(ms)).replaceFirstChar { it.uppercase() }

// 125000 ms -> "2 min" ; 40000 -> "40 s" ; 9000000 -> "2 h 30 min"
fun duracion(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}

// Tipo de red actual (permiso normal ACCESS_NETWORK_STATE)
fun tipoRed(ctx: Context): String {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        else -> "Otra red"
    }
}
