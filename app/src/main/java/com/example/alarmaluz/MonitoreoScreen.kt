package com.example.alarmaluz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

val pestanasMonitoreo = listOf(
    Pestana("monitoreo", "Inicio", Icons.Default.Home),
    Pestana("historial", "Historial", Icons.Default.DateRange),
    Pestana("config", "Configuración", Icons.Default.Settings)
)

@Composable
fun MonitoreoScreen(nav: NavController) {
    val ctx = LocalContext.current
    val estado = datoEnVivo(Repo.ref("estado"), Estado())
    val config = datoEnVivo(Repo.ref("config"), Config())
    val conexiones = listaEnVivo(Repo.ref("conexiones"), Conexion::class.java)
    val historial = listaEnVivo(Repo.ref("historial"), Evento::class.java)
    val enLinea = datoEnVivo(Repo.conectado, false)
    val activadorOnline = conexiones.any { it.rol == "activador" && it.online }
    val t = ahora(30_000)

    LaunchedEffect(enLinea) { if (enLinea) Repo.registrarDispositivo() }
    LaunchedEffect(estado) { Repo.marcarDato() }   // "último dato recibido" para la pantalla Conexiones

    // Mantiene la pantalla encendida mientras se monitorea
    val vista = LocalView.current
    DisposableEffect(Unit) { vista.keepScreenOn = true; onDispose { vista.keepScreenOn = false } }

    // ALARMA: cuando el Activador marca "sonando", este celular suena, vibra y parpadea la linterna
    LaunchedEffect(estado.sonando) {
        if (!estado.sonando) return@LaunchedEffect
        Alarma.sonar(ctx, config)
        try {
            var prendida = false
            while (true) {
                if (config.linterna) { prendida = !prendida; Alarma.linterna(ctx, prendida) }
                delay(400)
            }
        } finally { Alarma.parar(ctx) }   // se ejecuta al apagar (sonando = false) o al salir
    }

    // Hora estimada según el pronóstico solar (Open-Meteo); se recalcula cada hora
    var estimada by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(config.lat, config.lon, config.umbral, t / 3_600_000) {
        estimada = Sol.horaEstimada(config.lat, config.lon, config.umbral)
    }

    fun apagar() {
        if (!estado.sonando) return
        Repo.agregarEvento(Evento("sono", estado.sonoEn, System.currentTimeMillis(), estado.luzAlSonar))
        Repo.actualizar("estado", mapOf("sonando" to false))   // se apaga en todos los celulares
    }

    Pantalla(nav, "monitoreo", pestanasMonitoreo) {
        Encabezado("Buenos días", "Hola, ${Repo.usuario?.email?.substringBefore("@")}") {
            when {
                !enLinea -> ChipConexion(false, "")                       // este celular sin internet
                activadorOnline -> ChipConexion(true, "Activador en línea")
                else -> Chip("● Activador desconectado", C.Ambar, C.AmbarFondo)
            }
        }

        // Luz actual
        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Luz actual en tu pieza", color = C.Gris, modifier = Modifier.weight(1f))
                when {
                    estado.sonando -> Chip("SONANDO", C.Rojo, C.AmbarFondo)
                    estado.armada -> Chip("ARMADA", C.Ambar, C.AmbarFondo)
                    else -> Chip("DESARMADA", C.Gris, C.Pista)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${estado.luz}", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                Text("  lux", fontSize = 20.sp, color = C.Gris, modifier = Modifier.padding(bottom = 12.dp))
            }
            LinearProgressIndicator(
                progress = { (estado.luz.toFloat() / config.umbral).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp), color = C.Ambar, trackColor = C.Pista
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", color = C.Gris, fontSize = 12.sp)
                Text("Suena a los ${config.umbral} lux", color = C.Gris, fontSize = 12.sp)
            }
        }

        // Hora estimada y cuenta regresiva
        Tarjeta {
            val e = estimada
            if (e == null) Text("Sin estimación (revisa internet o baja el umbral)", color = C.Gris, fontSize = 13.sp)
            else {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Sonará aprox.", color = C.Gris, fontSize = 12.sp)
                        Text(hora(e), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Faltan", color = C.Gris, fontSize = 12.sp)
                        Text(duracion(e - t), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text("Estimado con la luz solar pronosticada para ${config.ciudad}", color = C.Gris, fontSize = 12.sp)
            }
        }

        // Últimos despertares
        val ultimos = historial.filter { it.tipo == "sono" }.sortedByDescending { it.sonoEn }.take(2)
        if (ultimos.isNotEmpty()) Tarjeta {
            Text("Últimos despertares", fontWeight = FontWeight.Bold)
            ultimos.forEach { ev ->
                val d = ev.apagadaEn - ev.sonoEn
                Fila("${dia(ev.sonoEn)} · sonó ${hora(ev.sonoEn)}", duracion(d), if (d <= 180_000) C.Verde else C.Ambar)
            }
        }

        // Botón grande para apagar (envía la orden al otro celular)
        Button(
            onClick = { apagar() }, enabled = estado.sonando,
            colors = ButtonDefaults.buttonColors(containerColor = C.Rojo, contentColor = C.Texto,
                disabledContainerColor = C.Tarjeta, disabledContentColor = C.Gris),
            shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Icon(Icons.Default.Close, null)
            Text("  Apagar alarma", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)   // FilterChip
@Composable
fun HistorialScreen(nav: NavController) {
    val historial = listaEnVivo(Repo.ref("historial"), Evento::class.java)
    var mes by remember { mutableStateOf(false) }   // false = esta semana, true = este mes
    val desde = System.currentTimeMillis() - (if (mes) 30L else 7L) * 86_400_000
    val lista = historial.filter { it.sonoEn >= desde }.sortedByDescending { it.sonoEn }
    val tiempos = lista.filter { it.tipo == "sono" }.map { it.apagadaEn - it.sonoEn }

    Pantalla(nav, "historial", pestanasMonitoreo) {
        Text("Historial de alarmas", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !mes, onClick = { mes = false }, label = { Text("Esta semana") })
            FilterChip(selected = mes, onClick = { mes = true }, label = { Text("Este mes") })
        }

        // Resumen
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Resumen("Promedio", if (tiempos.isEmpty()) "—" else duracion(tiempos.average().toLong()), "en despertar", Modifier.weight(1f))
            Resumen("Mejor", tiempos.minOrNull()?.let { duracion(it) } ?: "—", "tiempo", Modifier.weight(1f))
            Resumen("Alarmas", "${tiempos.size}", "sonaron", Modifier.weight(1f))
        }

        Tarjeta {
            if (lista.isEmpty()) Text("Todavía no hay alarmas en este período", color = C.Gris)
            lista.forEach { ev ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(dia(ev.sonoEn), fontWeight = FontWeight.Bold)
                        Text(
                            if (ev.tipo == "sono") "Sonó ${hora(ev.sonoEn)} · ${ev.lux} lux · apagada ${hora(ev.apagadaEn)}"
                            else "Alarma desarmada desde el Activador",
                            color = C.Gris, fontSize = 12.sp
                        )
                    }
                    val d = ev.apagadaEn - ev.sonoEn
                    if (ev.tipo == "sono") Text(duracion(d), fontWeight = FontWeight.ExtraBold, color = if (d <= 180_000) C.Verde else C.Ambar)
                    else Text("No sonó", color = C.Gris, fontSize = 13.sp)
                }
            }
        }
        Text("Guardado en tu cuenta · se sincroniza entre celulares", color = C.Gris, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun Resumen(titulo: String, valor: String, sub: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = C.Tarjeta)) {
        Column(Modifier.padding(12.dp)) {
            Text(titulo, color = C.Gris, fontSize = 11.sp)
            Text(valor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = C.Gris, fontSize = 11.sp)
        }
    }
}
