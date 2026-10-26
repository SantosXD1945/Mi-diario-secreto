package com.santosxd.diario.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.santosxd.diario.update.Updater

@Composable
fun UpdateDialog(release: Updater.Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hay una versión nueva") },
        text = {
            Text(
                "Se descargará el archivo de instalación desde tu navegador. " +
                    "Ábrelo para actualizar: tus datos se conservan."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)))
                onDismiss()
            }) { Text("Descargar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Luego") } }
    )
}
