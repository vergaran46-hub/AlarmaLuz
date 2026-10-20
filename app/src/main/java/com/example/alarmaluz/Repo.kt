package com.example.alarmaluz

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.*
import java.util.UUID

// ---------- Datos que se guardan en Firebase (todos con valor por defecto) ----------
data class Estado(val luz: Int = 0, val armada: Boolean = true, val sonando: Boolean = false,
                  val sonoEn: Long = 0, val luzAlSonar: Int = 0)
data class Config(val umbral: Int = 100, val vibracion: Boolean = true, val linterna: Boolean = true,
                  val sonido: String = "Alarma", val ciudad: String = "Copiapó",
                  val lat: Double = -27.37, val lon: Double = -70.33)
data class Conexion(val rol: String = "", val modelo: String = "", val online: Boolean = false,
                    val ultimoDato: Long = 0)
data class Evento(val tipo: String = "sono", val sonoEn: Long = 0, val apagadaEn: Long = 0, val lux: Int = 0)

// Todo lo de Firebase vive aquí. Las pantallas solo llaman a estas funciones.
object Repo {
    private val auth = FirebaseAuth.getInstance()
    // Caché local: si se corta internet, guarda los cambios y los sube al volver
    private val db = FirebaseDatabase.getInstance().apply { setPersistenceEnabled(true) }
    private lateinit var prefs: SharedPreferences

    // Se llama una vez al abrir la app (MainActivity)
    fun iniciar(ctx: Context) { prefs = ctx.getSharedPreferences("alarmaluz", Context.MODE_PRIVATE) }

    val usuario get() = auth.currentUser

    // Rol de ESTE celular ("activador" o "monitoreo"), guardado solo en el teléfono
    var rol: String?
        get() = prefs.getString("rol", null)
        set(v) = prefs.edit().putString("rol", v).apply()

    // Id único de este celular (para la lista de conexiones)
    private val idDispositivo: String
        get() = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }

    // Cada usuario solo usa su rama: usuarios/{uid}/... (las reglas de Firebase lo obligan)
    fun ref(ruta: String): DatabaseReference = db.getReference("usuarios/${usuario?.uid}/$ruta")
    val conectado: DatabaseReference get() = db.getReference(".info/connected")

    // ---------- Autenticación ----------
    fun login(email: String, clave: String, crear: Boolean, listo: (String?) -> Unit) {
        val tarea = if (crear) auth.createUserWithEmailAndPassword(email, clave)
                    else auth.signInWithEmailAndPassword(email, clave)
        tarea.addOnCompleteListener { t -> listo(if (t.isSuccessful) null else mensajeError(t.exception)) }
    }

    fun cerrarSesion() {
        desconectar()
        rol = null
        auth.signOut()
    }

    private fun mensajeError(e: Exception?) = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres"
        is FirebaseAuthUserCollisionException -> "Ese correo ya tiene una cuenta"
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        is FirebaseNetworkException -> "Sin conexión a internet"
        else -> "Error: ${e?.message}"
    }

    // ---------- Escrituras ----------
    fun actualizar(ruta: String, cambios: Map<String, Any>) { ref(ruta).updateChildren(cambios) }
    fun agregarEvento(e: Evento) { ref("historial").push().setValue(e) }

    // Marca este celular como conectado. Si se corta la conexión, Firebase lo marca offline solo.
    fun registrarDispositivo() {
        val r = rol ?: return
        val c = ref("conexiones/$idDispositivo")
        c.onDisconnect().updateChildren(mapOf("online" to false))
        c.setValue(Conexion(r, "${Build.MANUFACTURER} ${Build.MODEL}", true, System.currentTimeMillis()))
    }
    fun marcarDato() = actualizar("conexiones/$idDispositivo", mapOf("ultimoDato" to System.currentTimeMillis()))
    fun desconectar() { if (usuario != null) actualizar("conexiones/$idDispositivo", mapOf("online" to false)) }
}

// ---------- Lecturas en tiempo real para Compose ----------
// Devuelve el valor de Firebase y redibuja la pantalla cada vez que cambia.
@Composable
fun <T : Any> datoEnVivo(ref: DatabaseReference, inicial: T): T {
    var valor by remember(ref.toString()) { mutableStateOf(inicial) }
    @Suppress("UNCHECKED_CAST")
    val clase = inicial::class.javaObjectType as Class<T>
    DisposableEffect(ref.toString()) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { valor = s.getValue(clase) ?: inicial }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }
    return valor
}

// Igual, pero para una lista (historial, conexiones)
@Composable
fun <T : Any> listaEnVivo(ref: DatabaseReference, clase: Class<T>): List<T> {
    var lista by remember(ref.toString()) { mutableStateOf(emptyList<T>()) }
    DisposableEffect(ref.toString()) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { lista = s.children.mapNotNull { it.getValue(clase) } }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }
    return lista
}
