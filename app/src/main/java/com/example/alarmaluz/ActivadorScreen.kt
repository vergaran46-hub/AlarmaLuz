package com.example.alarmaluz

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlin.math.abs
import kotlin.math.roundToInt

val pestanasActivador = listOf(
    Pestana("activador", "Inicio", Icons.Default.Home),
    Pestana("conexiones", "Conexiones", Icons.Default.Person)
)

// Lee el sensor de luz del celular (lux). null = todavía no hay lectura o no tiene sensor.
@Composable
fun luzDelSensor(): Float? {
    val ctx = LocalContext.current
    var lux by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { lux = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, precision: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm.unregisterListener(l) }   // deja de leer al salir (ahorra batería)
    }
    return lux
}

@Composable
fun ActivadorScreen(nav: NavController, alCambiarRol: () -> Unit) {
    val estado = datoEnVivo(Repo.ref("estado"), Estado())
    val config = datoEnVivo(Repo.ref("config"), Config())
    val conexiones = listaEnVivo(Repo.ref("conexiones"), Conexion::class.java)
    val enLinea = datoEnVivo(Repo.conectado, false)
    val monitores = conexiones.count { it.rol == "monitoreo" && it.online }

    // Luz: sensor real; si el celular no tiene sensor, se simula con un slider
    val sensor = luzDelSensor()
    var simulada by remember { mutableFloatStateOf(0f) }
    val luz = (sensor ?: simulada).roundToInt()

    // Cada vez que hay internet, avisa "estoy conectado" (y Firebase avisa solo si se corta)
    LaunchedEffect(enLinea) { if (enLinea) Repo.registrarDispositivo() }

    var ultimaEnviada by remember { mutableIntStateOf(-100) }
    var estabaOscuro by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(luz, config.umbral) {
        // Solo envía si la luz cambió 3 lux o más: menos datos y menos batería
        if (abs(luz - ultimaEnviada) >= 3) {
            Repo.actualizar("estado", mapOf("luz" to luz))
            Repo.marcarDato()
            ultimaEnviada = luz
        }
        // "Amanecer": la luz pasa de estar bajo el umbral a estar sobre él -> suena la alarma
        val oscuro = luz < config.umbral
        if (estabaOscuro == true && !oscuro && estado.armada && !estado.sonando) {
            Repo.actualizar("estado", mapOf("sonando" to true, "sonoEn" to System.currentTimeMillis(), "luzAlSonar" to luz))
        }
        estabaOscuro = oscuro
    }

    Pantalla(nav, "activador", pestanasActivador) {
        Encabezado("Modo de este celular", "Activador") { if (!enLinea) ChipConexion(false, "") }

        // Medidor circular de luz
        Tarjeta {
            Text("Luz detectada ahora", color = C.Gris, modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(Modifier.size(200.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (luz.toFloat() / config.umbral).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(), color = C.Ambar, trackColor = C.Pista, strokeWidth = 12.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$luz", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Text("lux", color = C.Gris)
                }
            }
            Text("La alarma suena a los ${config.umbral} lux", color = C.Gris, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            if (estado.sonando) Chip("SONANDO en Monitoreo", C.Rojo, C.AmbarFondo)
            if (sensor == null) {
                Text("Este celular no tiene sensor de luz: simula la luz", color = C.Ambar, fontSize = 12.sp)
                Slider(value = simulada, onValueChange = { simulada = it }, valueRange = 0f..500f)
            }
        }

        // Switch "Alarma armada": envía el dato al celular Monitoreo (criterio 2.1.1.2)
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alarma armada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Sonará en los celulares Monitoreo", color = C.Gris, fontSize = 12.sp)
                }
                Switch(checked = estado.armada, onCheckedChange = { on ->
                    if (on) Repo.actualizar("estado", mapOf("armada" to true))
                    else {
                        Repo.actualizar("estado", mapOf("armada" to false, "sonando" to false))
                        Repo.agregarEvento(Evento(tipo = "desarmada", sonoEn = System.currentTimeMillis()))
                    }
                })
            }
        }

        // Aviso de conectados: lleva a la pantalla Conexiones
        Surface(
            color = C.VerdeFondo, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().clickable { nav.navigate("conexiones") { launchSingleTop = true } }
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("● Hay $monitores ${if (monitores == 1) "usuario conectado" else "usuarios conectados"}",
                    color = C.Verde, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Ver", color = C.Verde)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Verde)
            }
        }

        // Cambiar este celular al otro modo
        OutlinedButton(onClick = { Repo.rol = "monitoreo"; alCambiarRol() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cambiar a modo Monitoreo", color = C.Texto)
        }
    }
}

@Composable
fun ConexionesScreen(nav: NavController, alSalir: () -> Unit) {
    val ctx = LocalContext.current
    val conexiones = listaEnVivo(Repo.ref("conexiones"), Conexion::class.java)
    val enLinea = datoEnVivo(Repo.conectado, false)
    val t = ahora()
    val monitores = conexiones.filter { it.rol == "monitoreo" }
    val ultimoEnvio = conexiones.filter { it.rol == "activador" }.maxOfOrNull { it.ultimoDato } ?: 0L

    Pantalla(nav, "conexiones", pestanasActivador) {
        Text("Conexiones", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Chip("● ${monitores.count { it.online }} conectados a esta alarma", C.Verde, C.VerdeFondo)

        Text("MONITOREO", color = C.Gris, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Tarjeta {
            if (monitores.isEmpty()) Text("Aún no hay celulares Monitoreo. Inicia sesión en otro celular y elige ese rol.", color = C.Gris)
            monitores.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = C.AmbarFondo, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(m.modelo.take(1).uppercase(), color = C.Ambar, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(m.modelo, fontWeight = FontWeight.Bold)
                        Text("Último dato hace ${duracion(t - m.ultimoDato)}", color = C.Gris, fontSize = 12.sp)
                    }
                    Text(if (m.online) "En línea" else "Desconectado", color = if (m.online) C.Verde else C.Gris,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("ESTADO DEL ENVÍO", color = C.Gris, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Tarjeta {
            Fila("Última lectura enviada", if (ultimoEnvio > 0) "hace ${duracion(t - ultimoEnvio)}" else "—")
            Fila("Red", tipoRed(ctx))
            Fila("Conexión", if (enLinea) "Cifrada (TLS)" else "Sin conexión", if (enLinea) C.Verde else C.Rojo)
        }

        // Salir (el Activador no tiene pantalla de Configuración)
        TextButton(onClick = { Repo.cerrarSesion(); alSalir() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cerrar sesión", color = C.Gris)
        }
    }
}

@Composable
fun Fila(izq: String, der: String, color: androidx.compose.ui.graphics.Color = C.Texto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(izq, color = C.Gris); Text(der, color = color, fontWeight = FontWeight.Bold)
    }
}
