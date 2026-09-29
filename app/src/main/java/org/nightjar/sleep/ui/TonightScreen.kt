// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.analysis.*
import org.nightjar.sleep.tracking.TrackingForegroundService

@Composable fun TonightScreen(app: AppContainer, start: () -> Unit) {
    val tracking by app.runtime.tracking.collectAsStateWithLifecycle()
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val sessions by app.repository.sessions.collectAsStateWithLifecycle(emptyList())
    val plan by app.alarms.plan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tracking.running) { while (tracking.running) { now = System.currentTimeMillis(); delay(1000) } }
    val active = sessions.firstOrNull { it.endTime == null }
    val recovering = active != null && !tracking.running && !tracking.starting
    Page(if (tracking.running) "Rest easy." else "Make room for rest.",
        if (tracking.running) "Your night is being saved on this phone." else "A quieter night. A gentler morning.") {
        Panel {
            val moon = MaterialTheme.colorScheme.primary
            val background = MaterialTheme.colorScheme.surfaceContainer
            Canvas(Modifier.fillMaxWidth().height(74.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(moon, 30.dp.toPx(), center)
                drawCircle(background, 26.dp.toPx(), center + Offset(14.dp.toPx(), -9.dp.toPx()))
                drawCircle(moon.copy(alpha = .65f), 2.dp.toPx(), center + Offset(53.dp.toPx(), -18.dp.toPx()))
                drawCircle(moon.copy(alpha = .4f), 1.5.dp.toPx(), center + Offset(-65.dp.toPx(), 5.dp.toPx()))
            }
            if (tracking.running) {
                Text(durationLabel(now - tracking.startTime), style = MaterialTheme.typography.displayMedium)
                Detail("Tracking with", if (tracking.mode == SensingMode.MICROPHONE) "Microphone" else "Motion")
                Detail("Saved intervals", tracking.epochs.toString())
                Detail("Current estimate", when (tracking.phase) {
                    SleepPhase.UNKNOWN -> "Collecting data"
                    SleepPhase.AWAKE -> "Awake"
                    SleepPhase.LIGHT -> "Light sleep"
                    SleepPhase.DEEP -> "Deep sleep"
                })
                LinearProgressIndicator(progress = { tracking.intensity }, modifier = Modifier.fillMaxWidth())
                Text("Live activity • ${tracking.samples} samples this interval", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { context.startService(Intent(context, TrackingForegroundService::class.java).setAction(TrackingForegroundService.STOP)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("End session") }
            } else if (recovering) {
                Text("A saved night needs attention", style = MaterialTheme.typography.titleLarge)
                Text("Tracking stopped after ${clockTime(active!!.startTime)}. Resume to keep this session; any gap will be marked as missing data.")
                Button(onClick = {
                    app.settings.update { it.copy(mode = SensingMode.valueOf(active!!.mode)) }
                    start()
                }, modifier = Modifier.fillMaxWidth()) { Text("Resume session") }
                OutlinedButton(onClick = { scope.launch {
                    runCatching { app.repository.finish(active!!.id, System.currentTimeMillis(), true) }
                        .onSuccess { app.runtime.destination.value = "journal" }.onFailure { app.runtime.notice.value = it.message }
                } }, modifier = Modifier.fillMaxWidth()) { Text("Finish saved session") }
            } else {
                Text("Tonight starts here", style = MaterialTheme.typography.titleLarge)
                Text(if (settings.mode == SensingMode.ACCELEROMETER)
                    "Place your phone on the mattress beside you, on a firm, uncovered surface."
                    else "Place your phone on a nearby nightstand with the microphone unobstructed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = start, enabled = !tracking.starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text(if (tracking.starting) "Starting…" else "Start sleeping")
                }
                Text(if (settings.mode == SensingMode.ACCELEROMETER) "Motion sensing • audio stays off"
                    else if (settings.recordNoise) "Microphone • local event clips enabled" else "Microphone • intensity only",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        tracking.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Panel("Wake-up") {
            if (plan?.enabled == true) {
                Text(clockTime(plan!!.targetTime), style = MaterialTheme.typography.headlineLarge)
                Text(if (plan!!.windowMinutes == 0) "Fixed alarm • ${dateLabel(plan!!.targetTime)}"
                    else "Smart window from ${clockTime(plan!!.targetTime - plan!!.windowMinutes * 60_000L)} • ${dateLabel(plan!!.targetTime)}")
            } else Text("No alarm set")
            TextButton(onClick = { app.runtime.destination.value = "alarm" }) { Text(if (plan?.enabled == true) "Adjust alarm" else "Set a wake-up alarm") }
        }
        Text("Sleep phases are estimates from motion or sound. They cannot measure brain activity or diagnose sleep conditions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sessions.firstOrNull { it.endTime != null }?.let { last ->
            Panel("Your last night") {
                Detail(dateLabel(last.startTime), durationLabel(last.sleepMs))
                Detail("Quality estimate", last.score?.let { "$it / 100" } ?: "Not enough data")
                TextButton(onClick = { app.runtime.destination.value = "journal" }) { Text("Open journal") }
            }
        }
    }
}
