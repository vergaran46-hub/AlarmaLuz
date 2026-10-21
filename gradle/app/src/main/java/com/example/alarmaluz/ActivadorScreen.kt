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
    val estado = datoEnVivo(Repo.refAlarma("estado"), Estado())
    val codigo = datoEnVivo(Repo.refAlarma("codigo"), "")
    val miembros = listaEnVivo(Repo.refAlarma("miembros"), Miembro::class.java)
    val conexiones = listaEnVivo(Repo.refAlarma("conexiones"), Conexion::class.java).map { it.second }
    val enLinea = datoEnVivo(Repo.conectado, false)
    val t = ahora(5_000)
    Latido(enLinea)
    // Usuarios distintos con un celular Monitoreo en línea
    val usuariosEnLinea = conexiones.filter { it.rol == "monitoreo" && enLineaReciente(it, t) }.map { it.email }.distinct().size

    // Luz: sensor real; si el celular no tiene sensor, se simula con un slider
    val sensor = luzDelSensor()
    var simulada by remember { mutableFloatStateOf(0f) }
    val luz = (sensor ?: simulada).roundToInt()

    // Cada vez que hay internet, avisa "estoy conectado" (y Firebase avisa solo si se corta)
    LaunchedEffect(enLinea) {
        if (enLinea) {
            Repo.registrarDispositivo()
            Repo.actualizar(Repo.refAlarma("estado"), mapOf("admin" to Repo.email))
            Repo.asegurarCodigo()   // reintenta crear el código cada vez que vuelve internet
        } else Repo.descartarPendientes()   // sin internet: no dejar alarmas en cola
    }

    var ultimaEnviada by remember { mutableIntStateOf(-100) }
    val estabaOscuro = remember { mutableMapOf<String, Boolean>() }   // por cada usuario
    LaunchedEffect(luz, miembros, enLinea) {
        // Solo envía si la luz cambió 3 lux o más: menos datos y menos batería
        if (abs(luz - ultimaEnviada) >= 3) {
            Repo.actualizar(Repo.refAlarma("estado"), mapOf("luz" to luz))
            Repo.marcarDato()
            ultimaEnviada = luz
        }
        // "Amanecer" de CADA usuario: la luz pasa de estar bajo SU umbral a estar sobre él
        // Margen (histéresis): para "volver a oscuro" la luz debe bajar 20 % bajo el umbral.
        // Así, si la luz oscila justo en el límite (ej. 98-103 lux), no suena una y otra vez.
        miembros.forEach { (id, m) ->
            val margen = maxOf(10, m.umbral / 5)
            when {
                luz < m.umbral - margen -> estabaOscuro[id] = true
                luz >= m.umbral -> {
                    // Sin internet no se activa: evita que quede en cola y suene tarde al volver la conexión
                    if (estabaOscuro[id] == true && estado.armada && !m.sonando && enLinea) {
                        Repo.actualizar(Repo.refAlarma("miembros/$id"),
                            mapOf("sonando" to true, "sonoEn" to System.currentTimeMillis(), "luzAlSonar" to luz))
                    }
                    estabaOscuro[id] = false
                }
            }
        }
    }

    Pantalla(nav, "activador", pestanasActivador) {
        Encabezado("Modo de este celular · ${Repo.email}", "Activador (admin)") { if (!enLinea) ChipConexion(false, "") }

        // Código para que otros usuarios se unan a esta alarma
        Tarjeta {
            Text("Código de esta alarma", color = C.Gris)
            Text(codigo.ifEmpty { "Generando…" }, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = C.Ambar, letterSpacing = 6.sp)
            Text("Compártelo: cada usuario Monitoreo lo escribe una vez para unirse", color = C.Gris, fontSize = 12.sp)
            Repo.errorCodigo?.let {
                Text("No se pudo crear el código: $it. Revisa las reglas de Firebase.", color = C.Rojo, fontSize = 12.sp)
            }
        }

        // Medidor circular de luz
        Tarjeta {
            Text("Luz detectada ahora", color = C.Gris, modifier = Modifier.align(Alignment.CenterHorizontally))
            Box(Modifier.size(200.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (luz / 500f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(), color = C.Ambar, trackColor = C.Pista, strokeWidth = 12.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$luz", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Text("lux", color = C.Gris)
                }
            }
            val umbrales = miembros.map { it.second.umbral }
            Text(
                if (umbrales.isEmpty()) "Aún no hay usuarios unidos"
                else "Cada usuario despierta con su propia luz: entre ${umbrales.min()} y ${umbrales.max()} lux",
                color = C.Gris, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            val sonando = miembros.count { it.second.sonando }
            if (sonando > 0 && enLinea) Chip("SONANDO para $sonando usuario(s)", C.Rojo, C.AmbarFondo)
            if (sensor == null) {
                Text("Este celular no tiene sensor de luz: simula la luz", color = C.Ambar, fontSize = 12.sp)
                Slider(value = simulada, onValueChange = { simulada = it }, valueRange = 0f..500f)
            }
        }

        // Switch "Alarma armada": envía el dato a todos los celulares Monitoreo (criterio 2.1.1.2)
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alarma armada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Sonará en los celulares Monitoreo", color = C.Gris, fontSize = 12.sp)
                }
                Switch(checked = estado.armada, onCheckedChange = { on ->
                    Repo.actualizar(Repo.refAlarma("estado"), mapOf("armada" to on))
                    if (!on) miembros.forEach { (id, _) ->   // desarmar: apaga y deja registro a cada usuario
                        Repo.actualizar(Repo.refAlarma("miembros/$id"), mapOf("sonando" to false))
                        Repo.agregarEvento(id, Evento(tipo = "desarmada", sonoEn = System.currentTimeMillis()))
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
                Text(
                    if (!enLinea) "● Sin conexión — reconectando…"
                    else "● Hay $usuariosEnLinea ${if (usuariosEnLinea == 1) "usuario conectado" else "usuarios conectados"}",
                    color = if (enLinea) C.Verde else C.Ambar, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Ver", color = C.Verde)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Verde)
            }
        }

        // Cambiar este celular a Monitoreo: el admin queda como usuario de su propia alarma
        OutlinedButton(onClick = {
            Repo.rol = "monitoreo"
            Repo.actualizar(Repo.refAlarma("miembros/${Repo.uid}"), mapOf("email" to Repo.email))
            alCambiarRol()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Cambiar a modo Monitoreo", color = C.Texto)
        }
    }
}

@Composable
fun ConexionesScreen(nav: NavController, alSalir: () -> Unit) {
    val ctx = LocalContext.current
    val conexiones = listaEnVivo(Repo.refAlarma("conexiones"), Conexion::class.java).map { it.second }
    val miembros = listaEnVivo(Repo.refAlarma("miembros"), Miembro::class.java).map { it.second }
    val enLinea = datoEnVivo(Repo.conectado, false)
    val t = ahora()
    val monitores = conexiones.filter { it.rol == "monitoreo" }.sortedByDescending { enLineaReciente(it, t) }
    val ultimoEnvio = conexiones.filter { it.rol == "activador" }.maxOfOrNull { it.ultimoDato } ?: 0L

    Pantalla(nav, "conexiones", pestanasActivador) {
        Text("Conexiones", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Chip("● ${monitores.filter { enLineaReciente(it, t) }.map { it.email }.distinct().size} usuarios conectados · ${miembros.size} unidos",
            C.Verde, C.VerdeFondo)

        Text("USUARIOS MONITOREO", color = C.Gris, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Tarjeta {
            if (monitores.isEmpty()) Text("Aún no hay usuarios. Comparte el código de la alarma.", color = C.Gris)
            monitores.forEach { m ->
                val umbral = miembros.firstOrNull { it.email == m.email }?.umbral
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = C.AmbarFondo, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(m.email.take(1).uppercase(), color = C.Ambar, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(m.email, fontWeight = FontWeight.Bold)
                        Text("${m.modelo}${umbral?.let { " · despierta con $it lux" } ?: ""}", color = C.Gris, fontSize = 12.sp)
                        Text("Último dato hace ${duracion(t - m.ultimoDato)}", color = C.Gris, fontSize = 12.sp)
                    }
                    val on = enLineaReciente(m, t)
                    Text(if (on) "En línea" else "Desconectado", color = if (on) C.Verde else C.Gris,
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
