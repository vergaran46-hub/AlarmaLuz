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

/*  ESTRUCTURA EN FIREBASE
    codigos/{CODIGO}            = uid del admin (para unirse con el código)
    alarmas/{uidAdmin}/
        codigo                  código de 6 letras de esta alarma
        estado                  luz, armada, admin  (lo escribe el Activador del admin)
        miembros/{uid}          email, umbral, sonando, sonoEn, luzAlSonar (uno por usuario Monitoreo)
        conexiones/{celular}    rol, modelo, email, online, ultimoDato
        historial/{uid}/{id}    alarmas de cada usuario
    usuarios/{uid}/config       preferencias personales (vibración, linterna, sonido, ciudad)
*/
data class Estado(val luz: Int = 0, val armada: Boolean = true, val admin: String = "")
data class Miembro(val email: String = "", val umbral: Int = 100, val sonando: Boolean = false,
                   val sonoEn: Long = 0, val luzAlSonar: Int = 0)
data class Config(val vibracion: Boolean = true, val linterna: Boolean = true, val sonido: String = "Alarma",
                  val ciudad: String = "Copiapó", val lat: Double = -27.37, val lon: Double = -70.33)
data class Conexion(val rol: String = "", val modelo: String = "", val email: String = "",
                    val online: Boolean = false, val ultimoDato: Long = 0)
data class Evento(val tipo: String = "sono", val sonoEn: Long = 0, val apagadaEn: Long = 0, val lux: Int = 0)

// Todo lo de Firebase vive aquí. Las pantallas solo llaman a estas funciones.
object Repo {
    private val auth = FirebaseAuth.getInstance()
    // Caché local: si se corta internet, guarda los cambios y los sube al volver
    private val db = FirebaseDatabase.getInstance().apply { setPersistenceEnabled(true) }
    private lateinit var prefs: SharedPreferences

    fun iniciar(ctx: Context) { prefs = ctx.getSharedPreferences("alarmaluz", Context.MODE_PRIVATE) }

    val usuario get() = auth.currentUser
    val uid get() = usuario?.uid ?: ""
    val email get() = usuario?.email ?: ""

    // Rol de ESTE celular ("activador" o "monitoreo")
    var rol: String?
        get() = prefs.getString("rol", null)
        set(v) = prefs.edit().putString("rol", v).apply()

    // Alarma a la que está unido este celular (= uid del admin). El Activador usa la suya propia.
    var alarma: String?
        get() = prefs.getString("alarma", null)
        set(v) = prefs.edit().putString("alarma", v).apply()

    private val idDispositivo: String
        get() = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }

    fun refAlarma(ruta: String): DatabaseReference = db.getReference("alarmas/$alarma/$ruta")
    fun refUsuario(ruta: String): DatabaseReference = db.getReference("usuarios/$uid/$ruta")
    val conectado: DatabaseReference get() = db.getReference(".info/connected")

    // ---------- Autenticación ----------
    fun login(email: String, clave: String, crear: Boolean, listo: (String?) -> Unit) {
        val tarea = if (crear) auth.createUserWithEmailAndPassword(email, clave)
                    else auth.signInWithEmailAndPassword(email, clave)
        tarea.addOnCompleteListener { t -> listo(if (t.isSuccessful) null else mensajeError(t.exception)) }
    }

    fun cerrarSesion() {
        desconectar()
        rol = null; alarma = null
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

    // ---------- Roles y código de la alarma ----------
    // El admin: su alarma es la de su propia cuenta.
    fun serActivador() {
        if (alarma != null && alarma != uid) desconectar()   // deja la alarma de otro admin
        rol = "activador"; alarma = uid
        asegurarCodigo()
    }

    // Si la alarma aún no tiene código, crea uno de 6 letras. Si Firebase lo rechaza, guarda el motivo.
    var errorCodigo by mutableStateOf<String?>(null)
    fun asegurarCodigo() {
        errorCodigo = null
        refAlarma("codigo").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (s.exists()) return
                val codigo = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
                db.getReference("codigos/$codigo").setValue(uid)
                    .addOnSuccessListener { refAlarma("codigo").setValue(codigo) }
                    .addOnFailureListener { errorCodigo = it.message }
            }
            override fun onCancelled(e: DatabaseError) { errorCodigo = e.message }
        })
    }

    // Usuario Monitoreo: busca el código y se agrega como miembro de esa alarma
    fun unirse(codigo: String, listo: (String?) -> Unit) {
        db.getReference("codigos/${codigo.trim().uppercase()}").get()
            .addOnSuccessListener { s ->
                val admin = s.getValue(String::class.java) ?: return@addOnSuccessListener listo("Ese código no existe")
                alarma = admin; rol = "monitoreo"
                refAlarma("miembros/$uid").updateChildren(mapOf("email" to email))
                    .addOnSuccessListener { listo(null) }
                    .addOnFailureListener { listo("No se pudo unir: ${it.message}") }
            }
            .addOnFailureListener { listo("Sin conexión o código inválido") }
    }

    fun salirDeAlarma() { desconectar(); alarma = null }

    // Borra lo que quedó en cola sin enviar (ej. una alarma activada justo al perder internet),
    // para que no llegue tarde cuando vuelva la conexión.
    fun descartarPendientes() = db.purgeOutstandingWrites()

    // ---------- Escrituras ----------
    fun actualizar(ref: DatabaseReference, cambios: Map<String, Any>) { ref.updateChildren(cambios) }
    fun agregarEvento(uidMiembro: String, e: Evento) { refAlarma("historial/$uidMiembro").push().setValue(e) }

    // Marca este celular como conectado. Si se corta la conexión, Firebase lo marca offline solo.
    fun registrarDispositivo() {
        val r = rol ?: return
        if (alarma == null) return
        val c = refAlarma("conexiones/$idDispositivo")
        c.onDisconnect().updateChildren(mapOf("online" to false))
        c.setValue(Conexion(r, "${Build.MANUFACTURER} ${Build.MODEL}", email, true, System.currentTimeMillis()))
    }
    fun marcarDato() { if (alarma != null) actualizar(refAlarma("conexiones/$idDispositivo"), mapOf("ultimoDato" to System.currentTimeMillis())) }
    fun desconectar() { if (usuario != null && alarma != null) actualizar(refAlarma("conexiones/$idDispositivo"), mapOf("online" to false)) }
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

// Igual, pero para una lista. Devuelve pares (clave, valor): la clave es el id en Firebase.
@Composable
fun <T : Any> listaEnVivo(ref: DatabaseReference, clase: Class<T>): List<Pair<String, T>> {
    var lista by remember(ref.toString()) { mutableStateOf(emptyList<Pair<String, T>>()) }
    DisposableEffect(ref.toString()) {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                lista = s.children.mapNotNull { h -> h.getValue(clase)?.let { (h.key ?: "") to it } }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        onDispose { ref.removeEventListener(l) }
    }
    return lista
}
