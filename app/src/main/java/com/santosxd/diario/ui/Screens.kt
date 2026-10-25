package com.santosxd.diario.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santosxd.diario.MainActivity
import com.santosxd.diario.data.EntryWithActivities
import com.santosxd.diario.data.Repo
import com.santosxd.diario.security.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class MoodInfo(val label: String, val glyph: String, val color: Color)

/** Cinco niveles de ánimo. Los colores son los mismos en tema claro y oscuro. */
object Moods {
    val all = listOf(
        MoodInfo("Muy mal", "●", Color(0xFF5165D8)),
        MoodInfo("Mal", "▲", Color(0xFF586DEE)),
        MoodInfo("Regular", "■", Color(0xFF816DF4)),
        MoodInfo("Bien", "◆", Color(0xFFA26CF5)),
        MoodInfo("Muy bien", "★", Color(0xFFD35AE9))
    )
}

@Composable
fun MainScreen(
    repo: Repo,
    prefs: Prefs,
    activity: MainActivity,
    theme: String,
    onTheme: (String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Hoy" to "✎", "Historial" to "☰", "Más" to "⚙").forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(item.second) },
                        label = { Text(item.first) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(repo)
                1 -> HistoryScreen(repo)
                else -> MoreScreen(repo, prefs, activity, theme, onTheme)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(repo: Repo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activities by repo.activities.collectAsState(initial = emptyList())
    var mood by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var note by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("¿Cómo te sientes?", style = MaterialTheme.typography.headlineSmall)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Moods.all.forEachIndexed { i, m ->
                MoodButton(m, picked = mood == i) { mood = i }
            }
        }

        Text("¿Qué hiciste?", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activities.forEach { a ->
                FilterChip(
                    selected = a.id in selected,
                    onClick = {
                        selected = if (a.id in selected) selected - a.id else selected + a.id
                    },
                    label = { Text(a.name) }
                )
            }
            AssistChip(onClick = { showNew = true }, label = { Text("+ Nueva") })
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Nota (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = {
                val m = mood ?: return@Button
                scope.launch {
                    repo.saveEntry(m, note, selected)
                    mood = null
                    selected = emptySet()
                    note = ""
                    Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = mood != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar entrada")
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Nueva actividad") },
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
                        scope.launch { repo.addActivity(name) }
                        showNew = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Añadir") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MoodButton(m: MoodInfo, picked: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(m.color)
                .then(
                    if (picked) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(m.glyph, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            m.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun HistoryScreen(repo: Repo) {
    val scope = rememberCoroutineScope()
    val entries by repo.entries.collectAsState(initial = emptyList())
    var toDelete by remember { mutableStateOf<Long?>(null) }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aún no hay entradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.entry.id }) { e ->
                EntryCard(e) { toDelete = e.entry.id }
            }
        }
    }

    val id = toDelete
    if (id != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar esta entrada?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.delete(id) }
                    toDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun EntryCard(e: EntryWithActivities, onDelete: () -> Unit) {
    val mood = Moods.all[e.entry.mood.coerceIn(0, Moods.all.lastIndex)]
    val date = remember(e.entry.createdAt) {
        SimpleDateFormat("EEE d MMM, HH:mm", Locale("es")).format(Date(e.entry.createdAt))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(mood.color),
                contentAlignment = Alignment.Center
            ) {
                Text(mood.glyph, color = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mood.label, fontWeight = FontWeight.Bold)
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (e.activities.isNotEmpty()) {
                    Text(e.activities.joinToString(" · ") { it.name })
                }
                if (e.entry.note.isNotBlank()) {
                    Text(e.entry.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onDelete) { Text("Borrar") }
        }
    }
}
