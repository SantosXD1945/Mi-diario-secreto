package com.santosxd.diario.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.santosxd.diario.security.Prefs
import kotlinx.coroutines.delay

private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

fun canBiometric(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear diario")
        .setNegativeButtonText("Usar PIN")
        .setAllowedAuthenticators(AUTH)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(prefs: Prefs, activity: FragmentActivity, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var waitMs by remember { mutableStateOf(prefs.lockedForMs()) }
    val bioOk = remember { prefs.biometric && canBiometric(activity) }

    LaunchedEffect(waitMs) {
        if (waitMs > 0) {
            delay(1000)
            waitMs = prefs.lockedForMs()
        }
    }
    LaunchedEffect(Unit) {
        if (bioOk) showBiometric(activity, onUnlocked)
    }

    fun press(d: String) {
        if (waitMs > 0 || pin.length >= prefs.pinLength) return
        pin += d
        if (pin.length == prefs.pinLength) {
            if (prefs.verify(pin)) {
                onUnlocked()
            } else {
                pin = ""
                waitMs = prefs.lockedForMs()
                msg = if (waitMs > 0) "Demasiados intentos" else "PIN incorrecto"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mi diario secreto", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(prefs.pinLength) { i ->
                Spacer(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (waitMs > 0) "Espera ${waitMs / 1000 + 1} s" else msg,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(24.dp)
        )
        Spacer(Modifier.height(12.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { d -> KeyButton(d) { press(d) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (bioOk) {
                KeyButton("Huella") { showBiometric(activity, onUnlocked) }
            } else {
                Spacer(Modifier.size(72.dp))
            }
            KeyButton("0") { press("0") }
            KeyButton("⌫") { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, fontSize = if (label.length > 1) 12.sp else 24.sp)
    }
}
