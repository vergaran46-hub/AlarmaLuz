package com.example.alarmaluz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.roundToInt

// Ciudades disponibles para el pronóstico de luz (sin pedir permiso de ubicación: mínimo privilegio)
private val ciudades = listOf(
    Triple("Copiapó", -27.37, -70.33), Triple("Vallenar", -28.58, -70.76), Triple("La Serena", -29.90, -71.25),
    Triple("Antofagasta", -23.65, -70.40), Triple("Santiago", -33.45, -70.67), Triple("Valparaíso", -33.05, -71.62),
    Triple("Concepción", -36.83, -73.05), Triple("Puerto Montt", -41.47, -72.94)
)
private val sonidos = listOf("Alarma", "Notificación", "Tono")

@Composable
fun ConfigScreen(nav: NavController, alSalir: () -> Unit, alCambiarRol: () -> Unit) {
    val config = datoEnVivo(Repo.ref("config"), Config())
    // Valor local del slider; se guarda en Firebase solo al soltarlo
    var umbral by remember(config.umbral) { mutableFloatStateOf(config.umbral.toFloat()) }
    var estimada by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(config.umbral, config.lat, config.lon) { estimada = Sol.horaEstimada(config.lat, config.lon, config.umbral) }
    fun guardar(campo: String, valor: Any) = Repo.actualizar("config", mapOf(campo to valor))

    Pantalla(nav, "config", pestanasMonitoreo) {
        Text("Configuración", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

        // Umbral de luz (slider)
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Despertar con", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${umbral.roundToInt()} lux", color = C.Ambar, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = umbral, onValueChange = { umbral = it }, valueRange = 10f..500f,
                onValueChangeFinished = { guardar("umbral", umbral.roundToInt()) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Poca luz", color = C.Gris, fontSize = 12.sp); Text("Mucha luz", color = C.Gris, fontSize = 12.sp)
            }
            Surface(color = C.AmbarFondo, shape = RoundedCornerShape(14.dp)) {
                val e = estimada
                Text(
                    if (e == null) "No se pudo estimar la hora con este umbral"
                    else "Con ${config.umbral} lux, tu alarma sonará entre las ${hora(e - 600_000)} y ${hora(e + 600_000)} según la luz solar de tu zona.",
                    color = C.Texto, fontSize = 13.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Opciones
        Tarjeta {
            Menu("Ubicación", config.ciudad, ciudades.map { it.first }) { nombre ->
                val c = ciudades.first { it.first == nombre }
                Repo.actualizar("config", mapOf("ciudad" to c.first, "lat" to c.second, "lon" to c.third))
            }
            Interruptor("Vibración", config.vibracion) { guardar("vibracion", it) }
            Interruptor("Linterna parpadea", config.linterna) { guardar("linterna", it) }
            Menu("Sonido", config.sonido, sonidos) { guardar("sonido", it) }
        }

        // Rol de este celular (radio buttons)
        Tarjeta {
            Text("Este celular funciona como", fontWeight = FontWeight.Bold)
            Row {
                listOf("activador" to "Activador", "monitoreo" to "Monitoreo").forEach { (valor, texto) ->
                    Row(Modifier.weight(1f).clickable { if (valor != Repo.rol) { Repo.rol = valor; alCambiarRol() } },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = Repo.rol == valor, onClick = { if (valor != Repo.rol) { Repo.rol = valor; alCambiarRol() } })
                        Text(texto)
                    }
                }
            }
        }

        Text("Tu configuración se guarda en tu cuenta", color = C.Gris, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        OutlinedButton(onClick = { Repo.cerrarSesion(); alSalir() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Cerrar sesión", color = C.Texto, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Interruptor(texto: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}

// Fila con un menú desplegable de opciones
@Composable
fun Menu(texto: String, actual: String, opciones: List<String>, alElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { abierto = true }) { Text("$actual ▾") }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { abierto = false; alElegir(o) }) }
            }
        }
    }
}

// Primera vez en este celular: elegir cómo se usará
@Composable
fun RolScreen(alElegir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("¿Cómo usarás este celular?", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Puedes cambiarlo después en Configuración.", color = C.Gris)
        OpcionRol("Activador", "Queda junto a la ventana y mide la luz.") { Repo.rol = "activador"; alElegir() }
        OpcionRol("Monitoreo", "Lo tienes tú: muestra la luz y aquí suena la alarma.") { Repo.rol = "monitoreo"; alElegir() }
    }
}

@Composable
private fun OpcionRol(titulo: String, texto: String, alTocar: () -> Unit) {
    Tarjeta(Modifier.clickable(onClick = alTocar)) {
        Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ambar)
        Text(texto, color = C.Gris)
    }
}
