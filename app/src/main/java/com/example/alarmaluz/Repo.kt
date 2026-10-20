package com.example.alarmaluz

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

// Todo lo de Firebase vive aquí. Las pantallas solo llaman a estas funciones.
object Repo {
    private val auth = FirebaseAuth.getInstance()

    // Usuario con sesión iniciada (null si no hay nadie)
    val usuario get() = auth.currentUser

    // Ingresa o crea cuenta. Firebase cifra la contraseña y viaja por TLS.
    // listo(null) = todo bien; listo("mensaje") = hubo un error
    fun login(email: String, clave: String, crear: Boolean, listo: (String?) -> Unit) {
        val tarea = if (crear) auth.createUserWithEmailAndPassword(email, clave)
                    else auth.signInWithEmailAndPassword(email, clave)
        tarea.addOnCompleteListener { t ->
            listo(if (t.isSuccessful) null else mensajeError(t.exception))
        }
    }

    fun cerrarSesion() = auth.signOut()

    // Traduce los errores de Firebase a mensajes claros para el usuario
    private fun mensajeError(e: Exception?) = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres"
        is FirebaseAuthUserCollisionException -> "Ese correo ya tiene una cuenta"
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        is FirebaseNetworkException -> "Sin conexión a internet"
        else -> "Error: ${e?.message}"
    }
}
