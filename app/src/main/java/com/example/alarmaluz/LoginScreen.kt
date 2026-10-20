package com.example.alarmaluz

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(alIngresar: () -> Unit) {
    // Estado de la pantalla: al cambiar, Compose redibuja
    var email by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    // Valida los campos antes de llamar a Firebase
    fun enviar(crear: Boolean) {
        error = when {
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo válido"
            clave.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
            else -> null
        }
        if (error != null) return
        cargando = true
        Repo.login(email.trim(), clave, crear) { msg ->
            cargando = false
            if (msg == null) alIngresar() else error = msg
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(60.dp))
        Text("☀", fontSize = 56.sp, color = C.Ambar, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("LuzAlarma", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Despierta con la luz del día", color = C.Gris,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Correo electrónico") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = clave, onValueChange = { clave = it },
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),  // oculta la contraseña
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = C.Rojo) }

        Button(
            onClick = { enviar(crear = false) }, enabled = !cargando,
            shape = RoundedCornerShape(27.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (cargando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        TextButton(onClick = { enviar(crear = true) }, enabled = !cargando,
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("¿No tienes cuenta? Crear cuenta", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = C.Verde, modifier = Modifier.size(14.dp))
            Text("  Conexión cifrada · Firebase Authentication", color = C.Gris, fontSize = 12.sp)
        }
    }
}
