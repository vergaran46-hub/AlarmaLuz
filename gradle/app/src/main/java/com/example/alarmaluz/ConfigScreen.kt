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
    val config = datoEnVivo(Repo.refUsuario("config"), Config())                 // preferencias personales
    val yo = datoEnVivo(Repo.refAlarma("miembros/${Repo.uid}"), Miembro())      // mi umbral en esta alarma
    val estado = datoEnVivo(Repo.refAlarma("estado"), Estado())
    // Valor local del slider; se guarda en Firebase solo al soltarlo
    var umbral by remember(yo.umbral) { mutableFloatStateOf(yo.umbral.toFloat()) }
    var estimada by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(yo.umbral, config.lat, config.lon) { estimada = Sol.horaEstimada(config.lat, config.lon, yo.umbral) }
    fun guardar(campo: String, valor: Any) = Repo.actualizar(Repo.refUsuario("config"), mapOf(campo to valor))

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
                onValueChangeFinished = {   // el umbral va en la alarma: el Activador lo usa para hacerme sonar
                    Repo.actualizar(Repo.refAlarma("miembros/${Repo.uid}"), mapOf("umbral" to umbral.roundToInt()))
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Poca luz", color = C.Gris, fontSize = 12.sp); Text("Mucha luz", color = C.Gris, fontSize = 12.sp)
            }
            Surface(color = C.AmbarFondo, shape = RoundedCornerShape(14.dp)) {
                val e = estimada
                Text(
                    if (e == null) "No se pudo estimar la hora con este umbral"
                    else "Con ${yo.umbral} lux, tu alarma sonará entre las ${hora(e - 600_000)} y ${hora(e + 600_000)} según la luz solar de tu zona.",
                    color = C.Texto, fontSize = 13.sp, modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Opciones
        Tarjeta {
            Menu("Ubicación", config.ciudad, ciudades.map { it.first }) { nombre ->
                val c = ciudades.first { it.first == nombre }
                Repo.actualizar(Repo.refUsuario("config"), mapOf("ciudad" to c.first, "lat" to c.second, "lon" to c.third))
            }
            Interruptor("Vibración", config.vibracion) { guardar("vibracion", it) }
            Interruptor("Linterna parpadea", config.linterna) { guardar("linterna", it) }
            Menu("Sonido", config.sonido, sonidos) { guardar("sonido", it) }
        }

        // Alarma a la que estoy unido
        Tarjeta {
            Text("Alarma", fontWeight = FontWeight.Bold)
            Text("Unido a la alarma de ${estado.admin.ifEmpty { "…" }}", color = C.Gris)
            TextButton(onClick = { Repo.salirDeAlarma(); alCambiarRol() }) { Text("Salir de esta alarma / unirme a otra") }
        }

        // Rol de este celular (radio buttons)
        Tarjeta {
            Text("Este celular funciona como", fontWeight = FontWeight.Bold)
            Row {
                listOf("activador" to "Activador (admin)", "monitoreo" to "Monitoreo").forEach { (valor, texto) ->
                    val elegir = { if (valor == "activador" && Repo.rol != valor) { Repo.serActivador(); alCambiarRol() } }
                    Row(Modifier.weight(1f).clickable { elegir() }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = Repo.rol == valor, onClick = { elegir() })
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
        OpcionRol("Activador (admin)", "Queda junto a la ventana, mide la luz y ve quién está conectado. Crea un código para que otros se unan.") {
            Repo.serActivador(); alElegir()
        }
        OpcionRol("Monitoreo", "Lo tienes tú: te muestra la luz y aquí suena tu alarma. Te unes con el código del admin.") {
            Repo.rol = "monitoreo"; alElegir()
        }
    }
}

@Composable
private fun OpcionRol(titulo: String, texto: String, alTocar: () -> Unit) {
    Tarjeta(Modifier.clickable(onClick = alTocar)) {
        Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ambar)
        Text(texto, color = C.Gris)
    }
}

// Monitoreo sin alarma: escribe el código que muestra el Activador del admin
@Composable
fun UnirseScreen(alUnirse: () -> Unit, alVolver: () -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Únete a una alarma", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Escribe el código de 6 letras que aparece en el celular Activador del admin.", color = C.Gris)
        OutlinedTextField(
            value = codigo, onValueChange = { codigo = it.uppercase().take(6) },
            label = { Text("Código de la alarma") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = C.Rojo) }
        Button(
            onClick = {
                cargando = true
                Repo.unirse(codigo) { msg -> cargando = false; if (msg == null) alUnirse() else error = msg }
            },
            enabled = codigo.length == 6 && !cargando, shape = RoundedCornerShape(27.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text(if (cargando) "Uniendo…" else "Unirme", fontWeight = FontWeight.Bold) }
        TextButton(onClick = alVolver, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Volver a elegir el modo de este celular", color = C.Gris)
        }
    }
}
