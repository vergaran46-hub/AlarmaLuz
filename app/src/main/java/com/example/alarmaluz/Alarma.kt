package com.example.alarmaluz

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// Sonido, vibración y linterna del celular Monitoreo
object Alarma {
    private var tono: Ringtone? = null

    fun sonar(ctx: Context, cfg: Config) {
        val tipo = when (cfg.sonido) {
            "Notificación" -> RingtoneManager.TYPE_NOTIFICATION
            "Tono" -> RingtoneManager.TYPE_RINGTONE
            else -> RingtoneManager.TYPE_ALARM
        }
        val uri = RingtoneManager.getDefaultUri(tipo) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        tono = RingtoneManager.getRingtone(ctx, uri)?.apply {
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        // Patrón: 0 ms espera, 600 vibra, 400 pausa; el 0 final = repetir
        if (cfg.vibracion) vibrador(ctx).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    fun parar(ctx: Context) {
        tono?.stop(); tono = null
        vibrador(ctx).cancel()
        linterna(ctx, false)
    }

    // Prende/apaga el flash. setTorchMode no necesita permiso de cámara.
    fun linterna(ctx: Context, prender: Boolean) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, prender)
        } catch (e: Exception) { /* sin flash (ej. emulador): se ignora */ }
    }

    private fun vibrador(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
}

// Estima a qué hora habrá suficiente luz, con la radiación solar pronosticada (API gratis Open-Meteo, HTTPS)
object Sol {
    // Aproximación: lux dentro de una pieza junto a la ventana por cada W/m² de radiación solar exterior
    private const val LUX_POR_WATT = 12.0

    suspend fun horaEstimada(lat: Double, lon: Double, umbral: Int): Long? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                      "&hourly=shortwave_radiation&timeformat=unixtime&forecast_days=2"
            val horas = JSONObject(URL(url).readText()).getJSONObject("hourly")
            val t = horas.getJSONArray("time")
            val rad = horas.getJSONArray("shortwave_radiation")
            val ahora = System.currentTimeMillis()
            for (i in 1 until t.length()) {
                val l0 = rad.optDouble(i - 1, 0.0) * LUX_POR_WATT
                val l1 = rad.optDouble(i, 0.0) * LUX_POR_WATT
                if (l0 < umbral && l1 >= umbral) {            // aquí la luz cruza el umbral (amanecer)
                    val t0 = t.getLong(i - 1) * 1000
                    val t1 = t.getLong(i) * 1000
                    val cruce = t0 + ((umbral - l0) / (l1 - l0) * (t1 - t0)).toLong()  // interpolación lineal
                    if (cruce > ahora) return@withContext cruce
                }
            }
            null
        } catch (e: Exception) { null }   // sin internet o error de la API
    }
}
