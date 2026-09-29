// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.ui

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.analysis.*
import org.nightjar.sleep.core.storage.*

@Composable fun JournalScreen(app: AppContainer) {
    val sessions by app.repository.sessions.collectAsStateWithLifecycle(emptyList())
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    if (selected != null) {
        BackHandler { selected = null }
        SessionReport(app, selected!!) { selected = null }
        return
    }
    Page("Your sleep journal.", "A little perspective on each night.") {
        if (sessions.isEmpty()) Panel("Your first night is ahead") {
            Text("Start tracking on Tonight. When you end the session, your saved activity and sleep estimates will appear here.")
        }
        sessions.forEach { session ->
            Card(onClick = { selected = session.id }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(dateLabel(session.startTime), style = MaterialTheme.typography.titleLarge)
                    Detail("${clockTime(session.startTime)} → ${session.endTime?.let(::clockTime) ?: "In progress"}",
                        session.score?.let { "$it / 100" } ?: "—")
                    Text(if (session.endTime == null) "Tracking session" else "${durationLabel(session.sleepMs)} estimated sleep",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (session.status == "INTERRUPTED") Text("Interrupted session", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable private fun SessionReport(app: AppContainer, id: Long, back: () -> Unit) {
    val session by remember(id) { app.repository.dao.observeSession(id) }.collectAsStateWithLifecycle(null)
    val epochs by remember(id) { app.repository.dao.observeEpochs(id) }.collectAsStateWithLifecycle(emptyList())
    val noise by remember(id) { app.repository.dao.observeNoise(id) }.collectAsStateWithLifecycle(emptyList())
    val tags by remember(id) { app.repository.dao.observeTags(id) }.collectAsStateWithLifecycle(emptyList())
    val current = session
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var note by rememberSaveable(id) { mutableStateOf("") }
    var tagText by rememberSaveable(id) { mutableStateOf("") }
    var noteLoaded by rememberSaveable(id) { mutableStateOf(false) }
    var tagsLoaded by rememberSaveable(id) { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(current?.id) { if (!noteLoaded && current != null) { note = current.note; noteLoaded = true } }
    LaunchedEffect(tags) { if (!tagsLoaded && tags.isNotEmpty()) { tagText = tags.joinToString(", ") { it.tag }; tagsLoaded = true } }
    DisposableEffect(id) { onDispose { player?.release() } }
    if (current == null) { Page("Your night") { Text("Loading…") }; return }
    val end = current.endTime ?: epochs.lastOrNull()?.let { it.startTime + it.durationMs } ?: current.startTime
    val metrics = remember(current, epochs) { SleepQualityCalculator.calculate(current.startTime, end, epochs, AnalysisConfig(current.lowThreshold, current.highThreshold)) }
    Page(dateLabel(current.startTime), "${clockTime(current.startTime)} → ${clockTime(end)}") {
        TextButton(onClick = back) { Text("← All nights") }
        Panel("Quality estimate") {
            Text(metrics.score?.toString() ?: "—", style = MaterialTheme.typography.displayLarge)
            Text(if (metrics.score == null) "At least three minutes and 50% data coverage are needed for a score."
                else "Out of 100 • an activity-based estimate", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Detail("Time in bed", durationLabel(end - current.startTime))
            Detail("Estimated sleep", durationLabel(metrics.sleepMs))
            Detail("Time to fall asleep", durationLabel(metrics.latencyMs))
            Detail("Awake in bed", durationLabel(metrics.awakeMs))
            Detail("Missing data", durationLabel(metrics.unknownMs))
            Detail("Awakenings", metrics.awakenings.toString())
            Detail("Light / deep estimates", "%.0f%% / %.0f%%".format(if (metrics.sleepMs > 0) 100 - metrics.deepPercent else 0f, metrics.deepPercent))
        }
        Panel("Your night's rhythm") {
            Hypnogram(epochs, current.startTime, end)
            Text("Awake • Light • Deep • Missing", style = MaterialTheme.typography.labelMedium)
            Text("Stages are unvalidated estimates from stillness or sound, not measured sleep stages.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel("Noise events • ${noise.size}") {
            if (noise.isEmpty()) Text("No events recorded. Optional local clips are available in microphone mode.")
            noise.forEach { event ->
                Detail(clockTime(event.timestamp), "${event.durationMs / 1000}s • ${event.type.lowercase().replace('_', ' ')}")
                val file = app.repository.clipFile(event.clipPath)
                if (file != null) TextButton(onClick = {
                    player?.release(); player = null
                    if (playingId == event.id) playingId = null else {
                        runCatching {
                            val media = MediaPlayer()
                            player = media
                            media.setDataSource(file.absolutePath)
                            media.setOnCompletionListener { playingId = null; it.release(); if (player === it) player = null }
                            media.setOnErrorListener { p, _, _ -> playingId = null; p.release(); if (player === p) player = null; true }
                            media.prepare()
                            media.start()
                            playingId = event.id
                        }.onFailure { playingId = null; app.runtime.notice.value = "The recording could not be played." }
                    }
                }) { Text(if (playingId == event.id) "Stop clip" else "Play clip") }
                else Text("Clip unavailable or expired", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (current.endTime != null) Panel("How did your night feel?") {
            OutlinedTextField(value = note, onValueChange = { note = it.take(2000) }, label = { Text("Sleep notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = tagText, onValueChange = { tagText = it.take(500); tagsLoaded = true }, label = { Text("Tags, separated by commas") },
                placeholder = { Text("caffeine, exercise, stress") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }))
            Button(onClick = { focusManager.clearFocus(); keyboard?.hide(); scope.launch {
                runCatching { app.repository.saveJournal(id, note, tagText.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()) }
                    .onSuccess { app.runtime.notice.value = "Notes saved." }.onFailure { app.runtime.notice.value = it.message }
            } }) { Text("Save notes") }
        }
        Panel("How the score works") {
            Text("60% sleep efficiency + 25% progress toward 7½ hours + 15% continuity. Unknown intervals lower coverage and never count as sleep. An awakening needs at least one minute of awake estimates after sleep onset.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (current.endTime != null) TextButton(onClick = { deleteConfirm = true }) { Text("Delete this night", color = MaterialTheme.colorScheme.error) }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false },
        title = { Text("Delete this night?") }, text = { Text("This removes the session, activity, notes and audio clips from this phone.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            runCatching { app.repository.deleteSession(id) }.onSuccess { deleteConfirm = false; back() }.onFailure { app.runtime.notice.value = it.message }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Keep night") } })
}

@Composable private fun Hypnogram(epochs: List<Epoch>, start: Long, end: Long) {
    val colors = mapOf("AWAKE" to Color(0xFFE9BFA2), "LIGHT" to Color(0xFFBDA6E6), "DEEP" to Color(0xFF7773BC), "UNKNOWN" to Color(0xFF596174))
    val description = epochs.groupBy { it.phase }.entries.joinToString { (phase, rows) -> "$phase ${durationLabel(rows.sumOf { it.durationMs })}" }
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Sleep phase estimates. $description" }) {
        val span = (end - start).coerceAtLeast(1).toFloat()
        val band = size.height / 4
        for (i in 1..3) drawLine(Color.Gray.copy(alpha = .2f), Offset(0f, i * band), Offset(size.width, i * band))
        var previous: Offset? = null
        epochs.forEach { epoch ->
            val left = ((epoch.startTime - start) / span * size.width).coerceIn(0f, size.width)
            val right = ((epoch.startTime + epoch.durationMs - start) / span * size.width).coerceIn(left, size.width)
            val y = when (epoch.phase) { "AWAKE" -> band * .5f; "LIGHT" -> band * 1.5f; "DEEP" -> band * 2.5f; else -> band * 3.5f }
            val color = colors[epoch.phase] ?: colors.getValue("UNKNOWN")
            drawRect(color.copy(alpha = .18f), Offset(left, y), Size((right-left).coerceAtLeast(1f), size.height-y))
            previous?.let { drawLine(color, Offset(left, it.y), Offset(left, y), 2.dp.toPx()) }
            drawLine(color, Offset(left, y), Offset(right, y), 2.dp.toPx())
            previous = Offset(right, y)
        }
    }
}
