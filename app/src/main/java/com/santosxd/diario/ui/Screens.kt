package com.santosxd.diario.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santosxd.diario.MainActivity
import com.santosxd.diario.data.EntryWithActivities
import com.santosxd.diario.data.Repo
import com.santosxd.diario.security.Prefs
import com.santosxd.diario.update.Updater
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var editing by remember { mutableStateOf<EntryWithActivities?>(null) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }

    LaunchedEffect(Unit) {
        if (prefs.updateChecks) {
            val r = withContext(Dispatchers.IO) { Updater.fetchLatest() }
            if (r != null && Updater.isNewer(r)) update = r
        }
    }

    BackHandler(enabled = editing != null) { editing = null }

    val current = editing
    if (current != null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = { editing = null }) { Text("← Cancelar") }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                EntryForm(repo, current) { editing = null }
            }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(
                        "Hoy" to "✎",
                        "Historial" to "☰",
                        "Calendario" to "▦",
                        "Resumen" to "◔",
                        "Más" to "⚙"
                    ).forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(item.second) },
                            label = { Text(item.first, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> TodayScreen(repo)
                    1 -> HistoryScreen(repo) { editing = it }
                    2 -> CalendarScreen(repo) { editing = it }
                    3 -> StatsScreen(repo)
                    else -> MoreScreen(repo, prefs, activity, theme, onTheme)
                }
            }
        }
    }

    val found = update
    if (found != null) UpdateDialog(found) { update = null }
}

@Composable
fun TodayScreen(repo: Repo) {
    var formKey by remember { mutableIntStateOf(0) }
    key(formKey) {
        EntryForm(repo, null) { formKey++ }
    }
}

/** Formulario para crear una entrada (initial = null) o editar una existente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryForm(repo: Repo, initial: EntryWithActivities?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activities by repo.activities.collectAsState(initial = emptyList())
    var mood by remember { mutableStateOf<Int?>(initial?.entry?.mood) }
    var selected by remember {
        mutableStateOf<Set<Long>>(initial?.activities?.map { it.id }?.toSet() ?: emptySet())
    }
    var note by remember { mutableStateOf(initial?.entry?.note ?: "") }
    var time by remember {
        mutableLongStateOf(initial?.entry?.createdAt ?: System.currentTimeMillis())
    }
    var showNew by remember { mutableStateOf(false) }
    val dateText = remember(time) {
        SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale("es")).format(Date(time))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (initial == null) "¿Cómo te sientes?" else "Editar entrada",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Moods.all.forEachIndexed { i, m ->
                MoodButton(m, picked = mood == i) { mood = i }
            }
        }

        Text(dateText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pickDate(ctx, time) { time = it } }) { Text("Cambiar fecha") }
            TextButton(onClick = { pickTime(ctx, time) { time = it } }) { Text("Cambiar hora") }
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
                    if (initial == null) {
                        repo.saveEntry(m, note, selected, time)
                    } else {
                        repo.updateEntry(initial.entry.id, m, note, selected, time)
                    }
                    Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            enabled = mood != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (initial == null) "Guardar entrada" else "Guardar cambios")
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
fun HistoryScreen(repo: Repo, onEdit: (EntryWithActivities) -> Unit) {
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
                EntryCard(e, onEdit = { onEdit(e) }, onDelete = { toDelete = e.entry.id })
            }
        }
    }

    val id = toDelete
    if (id != null) {
        DeleteDialog(
            onConfirm = {
                scope.launch { repo.delete(id) }
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Borrar esta entrada?") },
        text = { Text("No se puede deshacer.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EntryCard(e: EntryWithActivities, onEdit: () -> Unit, onDelete: () -> Unit) {
    val mood = Moods.all[e.entry.mood.coerceIn(0, Moods.all.lastIndex)]
    val date = remember(e.entry.createdAt) {
        SimpleDateFormat("EEE d MMM, HH:mm", Locale("es")).format(Date(e.entry.createdAt))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
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
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Borrar") }
            }
        }
    }
}

@Composable
fun CalendarScreen(repo: Repo, onEdit: (EntryWithActivities) -> Unit) {
    val scope = rememberCoroutineScope()
    val entries by repo.entries.collectAsState(initial = emptyList())
    var month by remember { mutableLongStateOf(firstOfMonth(System.currentTimeMillis())) }
    var selectedKey by remember { mutableIntStateOf(dayKey(System.currentTimeMillis())) }
    var toDelete by remember { mutableStateOf<Long?>(null) }
    val byDay = remember(entries) { entries.groupBy { dayKey(it.entry.createdAt) } }

    val cal = Calendar.getInstance().apply { timeInMillis = month }
    val year = cal.get(Calendar.YEAR)
    val mon = cal.get(Calendar.MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Semana que empieza en lunes: lunes = 0. El calendario está en el día 1.
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val title = SimpleDateFormat("MMMM yyyy", Locale("es")).format(Date(month))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { month = shiftMonth(month, -1) }) { Text("◀") }
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { month = shiftMonth(month, 1) }) { Text("▶") }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val d = week * 7 + col + 1 - offset
                    if (d < 1 || d > daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val key = year * 10000 + (mon + 1) * 100 + d
                        val list = byDay[key]
                        val bg = if (list != null) {
                            val avg = list.map { it.entry.mood }.average().roundToInt().coerceIn(0, 4)
                            Moods.all[avg].color
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .then(
                                    if (key == selectedKey) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onBackground,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                d.toString(),
                                color = if (list != null) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val dayEntries = (byDay[selectedKey] ?: emptyList()).sortedByDescending { it.entry.createdAt }
        if (dayEntries.isEmpty()) {
            Text("Sin entradas este día", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            dayEntries.forEach { e ->
                EntryCard(e, onEdit = { onEdit(e) }, onDelete = { toDelete = e.entry.id })
            }
        }
    }

    val id = toDelete
    if (id != null) {
        DeleteDialog(
            onConfirm = {
                scope.launch { repo.delete(id) }
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
fun StatsScreen(repo: Repo) {
    val entries by repo.entries.collectAsState(initial = emptyList())
    var range by remember { mutableIntStateOf(30) }
    val filtered = remember(entries, range) {
        if (range == 0) {
            entries
        } else {
            val limit = System.currentTimeMillis() - range * 86_400_000L
            entries.filter { it.entry.createdAt >= limit }
        }
    }
    val byDay = remember(entries) { entries.groupBy { dayKey(it.entry.createdAt) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Resumen", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "7 días", 30 to "30 días", 0 to "Todo").forEach { (value, label) ->
                FilterChip(
                    selected = range == value,
                    onClick = { range = value },
                    label = { Text(label) }
                )
            }
        }

        if (filtered.isEmpty()) {
            Text(
                "Aún no hay datos en este periodo",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val avg = filtered.map { it.entry.mood + 1 }.average()
            val days = streak(byDay.keys)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Entradas: ${filtered.size}")
                    Text("Ánimo medio: ${"%.1f".format(avg)} de 5")
                    Text("Racha actual: $days ${if (days == 1) "día" else "días"}")
                }
            }

            Text("Cómo te has sentido", style = MaterialTheme.typography.titleMedium)
            MoodDistribution(filtered)

            Text("Últimos 14 días", style = MaterialTheme.typography.titleMedium)
            Last14Days(byDay)

            Text("Ánimo medio por actividad", style = MaterialTheme.typography.titleMedium)
            ByActivity(filtered)
        }
    }
}

@Composable
private fun MoodDistribution(filtered: List<EntryWithActivities>) {
    val counts = IntArray(5)
    filtered.forEach { counts[it.entry.mood.coerceIn(0, 4)]++ }
    val max = counts.max().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until 5) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Moods.all[i].label,
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                Box(Modifier.weight(1f)) {
                    if (counts[i] > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(counts[i].toFloat() / max)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Moods.all[i].color)
                        )
                    }
                }
                Text(
                    counts[i].toString(),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Last14Days(byDay: Map<Int, List<EntryWithActivities>>) {
    val days = remember {
        (13 downTo 0).map { k ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -k) }.timeInMillis
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(110.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { ms ->
            val list = byDay[dayKey(ms)]
            val dayNum = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH)
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (list != null) {
                    val avg = list.map { it.entry.mood }.average()
                    val color = Moods.all[avg.roundToInt().coerceIn(0, 4)].color
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .fillMaxWidth()
                            .height(((avg.toFloat() + 1f) / 5f * 80f).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
                Text(
                    dayNum.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ByActivity(filtered: List<EntryWithActivities>) {
    val stats = remember(filtered) {
        val map = HashMap<String, MutableList<Int>>()
        for (e in filtered) {
            for (a in e.activities) {
                map.getOrPut(a.name) { mutableListOf() }.add(e.entry.mood)
            }
        }
        map.entries
            .filter { it.value.size >= 2 }
            .map { Triple(it.key, it.value.average(), it.value.size) }
            .sortedByDescending { it.second }
            .take(10)
    }
    if (stats.isEmpty()) {
        Text(
            "Necesitas al menos 2 entradas con la misma actividad.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.forEach { (name, avg, n) ->
                val color = Moods.all[avg.roundToInt().coerceIn(0, 4)].color
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        modifier = Modifier.width(96.dp),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(((avg.toFloat() + 1f) / 5f).coerceIn(0f, 1f))
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color)
                        )
                    }
                    Text(
                        "${"%.1f".format(avg + 1)} ($n)",
                        modifier = Modifier.width(64.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
