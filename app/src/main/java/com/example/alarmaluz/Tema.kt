package com.example.alarmaluz

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores del mockup: un solo lugar para cambiarlos
object C {
    val Fondo = Color(0xFF0F1A2B)
    val Tarjeta = Color(0xFF182538)
    val Borde = Color(0xFF34465F)
    val Ambar = Color(0xFFF5B547)
    val Verde = Color(0xFF6FD3A1)
    val VerdeFondo = Color(0xFF173A33)
    val Rojo = Color(0xFFE5604F)
    val Texto = Color(0xFFF3EFE6)
    val Gris = Color(0xFFA9B4C6)
}

// Tema Material 3 oscuro con nuestros colores (criterio 2.1.1.2: Material Design)
@Composable
fun LuzAlarmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Ambar, onPrimary = Color(0xFF1A1306),
            background = C.Fondo, surface = C.Tarjeta,
            onBackground = C.Texto, onSurface = C.Texto,
            outline = C.Borde, error = C.Rojo
        ),
        content = content
    )
}
