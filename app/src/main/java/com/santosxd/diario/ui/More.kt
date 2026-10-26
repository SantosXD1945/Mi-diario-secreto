package com.santosxd.diario.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.santosxd.diario.BuildConfig
import com.santosxd.diario.MainActivity
import com.santosxd.diario.data.ActivityEntity
import com.santosxd.diario.data.Repo
import com.santosxd.diario.reminder.Reminder
import com.santosxd.diario.security.Crypto
import com.santosxd.diario.security.Prefs
import com.santosxd.diario.update.Updater
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    repo: Repo,
    prefs: Prefs,
    activity: MainActivity,
    theme: String,
    onTheme: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activities by repo.activities.collectAsState(initial = emptyList())
    var hasPin by remember { mutableStateOf(prefs.hasPin()) }
    var bio by remember { mutableStateOf(prefs.biometric) }
    var tree by remember { mutableStateOf(prefs.backupTree) }
    var dlg by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreJson by remember { mutableStateOf<String?>(null) }
    var reminder by remember { mutableStateOf(prefs.reminderEnabled) }
    var rHour by remember { mutableIntStateOf(prefs.reminderHour) }
    var rMinute by remember { mutableIntStateOf(prefs.reminderMinute) }
    var updates by remember { mutableStateOf(prefs.updateChecks) }
    var found by remember { mutableStateOf<Updater.Release?>(null) }
    var renaming by remember { mutableStateOf<ActivityEntity?>(null) }
    var deleting by remember { mutableStateOf<ActivityEntity?>(null) }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

    fun enableReminder() {
        prefs.reminderEnabled = true
        reminder = true
        Reminder.schedule(ctx, prefs.reminderHour, prefs.reminderMinute)
    }

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableReminder() else toast("Sin permiso de notificaciones no puedo avisarte")
    }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.backupTree = uri.toString()
            tree = uri.toString()
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            dlg = "restorepass"
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val text = repo.exportCsv()
                        ctx.contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                toast(if (ok) "Archivo CSV guardado" else "No se pudo guardar el CSV")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { opt ->
                FilterChip(
                    selected = theme == opt.first,
                    onClick = { onTheme(opt.first) },
                    label = { Text(opt.second) }
                )
            }
        }

        Text("Actividades", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { dlg = "activities" }) { Text("Renombrar o borrar actividades") }

        Text("Recordatorio diario", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Avisarme cada día")
            Switch(checked = reminder, onCheckedChange = { on ->
                if (!on) {
                    prefs.reminderEnabled = false
                    reminder = false
                    Reminder.cancel(ctx)
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    activity.suppressLock = true
                    askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enableReminder()
                }
            })
        }
        OutlinedButton(onClick = {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, rHour)
                set(Calendar.MINUTE, rMinute)
            }
            pickTime(ctx, c.timeInMillis) { ms ->
                val n = Calendar.getInstance().apply { timeInMillis = ms }
                rHour = n.get(Calendar.HOUR_OF_DAY)
                rMinute = n.get(Calendar.MINUTE)
                prefs.reminderHour = rHour
                prefs.reminderMinute = rMinute
                if (prefs.reminderEnabled) Reminder.schedule(ctx, rHour, rMinute)
            }
        }) { Text("Hora: %02d:%02d".format(rHour, rMinute)) }

        Text("Seguridad", style = MaterialTheme.typography.titleMedium)
        if (!hasPin) {
            Button(onClick = { dlg = "setpin" }) { Text("Crear PIN") }
        } else {
            if (canBiometric(ctx)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Desbloquear con huella")
                    Switch(checked = bio, onCheckedChange = {
                        prefs.biometric = it
                        bio = it
                    })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dlg = "setpin" }) { Text("Cambiar PIN") }
                OutlinedButton(onClick = { dlg = "removepin" }) { Text("Quitar PIN") }
            }
        }
        Text(
            "El PIN bloquea la app. Los datos del diario no están cifrados dentro del teléfono: " +
                "los protege el sistema Android. Las copias de seguridad sí van cifradas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Copia de seguridad", style = MaterialTheme.typography.titleMedium)
        Text(
            if (tree == null) "Aún no has elegido una carpeta." else "Carpeta elegida.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {
            activity.suppressLock = true
            pickTree.launch(null)
        }) { Text("Elegir carpeta") }
        Button(onClick = {
            if (tree == null) toast("Elige una carpeta primero") else dlg = "backup"
        }) { Text("Hacer copia ahora") }
        OutlinedButton(onClick = {
            activity.suppressLock = true
            pickFile.launch(arrayOf("*/*"))
        }) { Text("Restaurar copia") }
        Text(
            "Cada copia es un archivo nuevo con fecha y la app nunca borra las anteriores. " +
                "Si olvidas la contraseña de una copia, no se puede abrir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Exportar", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            activity.suppressLock = true
            createCsv.launch("diario.csv")
        }) { Text("Exportar a CSV") }
        Text(
            "El archivo CSV NO va cifrado: cualquiera que lo tenga podrá leer tu diario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        Text(
            "Versión instalada: ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Avisarme de versiones nuevas")
            Switch(checked = updates, onCheckedChange = {
                prefs.updateChecks = it
                updates = it
                Updater.schedule(ctx, it)
            })
        }
        OutlinedButton(onClick = {
            scope.launch {
                val r = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                if (r == null) {
                    toast("No se pudo comprobar. Revisa tu conexión.")
                } else if (Updater.isNewer(r)) {
                    found = r
                } else {
                    toast("Ya tienes la última versión")
                }
            }
        }) { Text("Buscar ahora") }
        Text(
            "Solo consulta la página de versiones del proyecto en GitHub. " +
                "No envía nada de tu diario.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val release = found
    if (release != null) UpdateDialog(release) { found = null }

    when (dlg) {
        "setpin" -> SetPinDialog(onDismiss = { dlg = null }) { pin ->
            prefs.setPin(pin)
            hasPin = true
            dlg = null
            toast("PIN guardado")
        }

        "removepin" -> VerifyPinDialog(prefs, onDismiss = { dlg = null }) {
            prefs.clearPin()
            hasPin = false
            bio = false
            dlg = null
            toast("PIN quitado")
        }

        "activities" -> AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("Actividades") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    activities.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(a.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { renaming = a }) { Text("Renombrar") }
                            TextButton(onClick = { deleting = a }) { Text("Borrar") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dlg = null }) { Text("Cerrar") } }
        )

        "backup" -> PassDialog("Contraseña para cifrar la copia", confirm = true, onDismiss = { dlg = null }) { pass ->
            dlg = null
            val treeText = tree
            if (treeText == null) {
                toast("Elige una carpeta primero")
                return@PassDialog
            }
            val treeUri = Uri.parse(treeText)
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    try {
                        val json = repo.exportJson()
                        val bytes = Crypto.encrypt(json.toByteArray(Charsets.UTF_8), pass)
                        writeBackup(ctx, treeUri, bytes)
                    } catch (e: Exception) {
                        null
                    }
                }
                toast(if (name != null) "Copia guardada: $name" else "No se pudo guardar la copia")
            }
        }

        "restorepass" -> PassDialog("Contraseña de la copia", confirm = false, onDismiss = { dlg = null }) { pass ->
            dlg = null
            val uri = restoreUri
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    try {
                        val bytes = uri?.let { ctx.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }
                        bytes?.let { Crypto.decrypt(it, pass)?.toString(Charsets.UTF_8) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (json == null) {
                    toast("Contraseña incorrecta o archivo dañado")
                } else {
                    restoreJson = json
                    dlg = "restoreconfirm"
                }
            }
        }

        "restoreconfirm" -> AlertDialog(
            onDismissRequest = { dlg = null },
            title = { Text("¿Reemplazar tus datos?") },
            text = { Text("Se borrará lo que hay ahora en la app y se cargará la copia.") },
            confirmButton = {
                TextButton(onClick = {
                    val json = restoreJson
                    dlg = null
                    scope.launch {
                        try {
                            if (json != null) repo.importJson(json)
                            toast("Copia restaurada")
                        } catch (e: Exception) {
                            toast("La copia no es válida")
                        }
                        restoreJson = null
                    }
                }) { Text("Reemplazar") }
            },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text("Cancelar") } }
        )
    }

    val toRename = renaming
    if (toRename != null) {
        var name by remember(toRename.id) { mutableStateOf(toRename.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar actividad") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nombre") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = repo.renameActivity(toRename.id, name)
                            if (!ok) toast("Ya existe una actividad con ese nombre")
                        }
                        renaming = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar \"${toDelete.name}\"?") },
            text = { Text("Se quitará de todas tus entradas. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteActivity(toDelete.id) }
                    deleting = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

private fun writeBackup(ctx: Context, treeUri: Uri, bytes: ByteArray): String? {
    val dir = DocumentFile.fromTreeUri(ctx, treeUri) ?: return null
    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
    val name = "diario-$stamp.dsb"
    val file = dir.createFile("application/octet-stream", name) ?: return null
    val out = ctx.contentResolver.openOutputStream(file.uri) ?: return null
    out.use { it.write(bytes) }
    return file.name ?: name
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length in 4..6 && a.all { it.isDigit() } && a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it.take(6) },
                    label = { Text("PIN de 4 a 6 números") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = b,
                    onValueChange = { b = it.take(6) },
                    label = { Text("Repite el PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSet(a) }, enabled = valid) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun VerifyPinDialog(prefs: Prefs, onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribe tu PIN actual") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.take(6) },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text("PIN incorrecto") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (prefs.verify(pin)) onOk() else error = true }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PassDialog(
    title: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onOk: (CharArray) -> Unit
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = if (confirm) a.length >= 8 && a == b else a.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (confirm) {
                    OutlinedTextField(
                        value = b,
                        onValueChange = { b = it },
                        label = { Text("Repite la contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Text(
                        "Mínimo 8 caracteres. Si la olvidas, esta copia no se puede abrir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(a.toCharArray()) }, enabled = valid) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
