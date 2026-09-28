package org.nightjar.sleep.ui

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nightjar.sleep.core.audio.AlarmTonePreview
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.alarm.AlarmPlaybackService
import org.nightjar.sleep.core.audio.SynthAudio

@Composable fun AlarmScreen(app: AppContainer) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val plan by app.alarms.plan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember(context, scope) { AlarmTonePreview(context, scope) { app.runtime.notice.value = it } }
    val previewTone by preview.playingTone.collectAsStateWithLifecycle()
    val alarm by app.runtime.alarm.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, lifecycle) {
        lifecycle.addObserver(preview)
        onDispose { lifecycle.removeObserver(preview); preview.stop() }
    }
    LaunchedEffect(alarm.ringing) { if (alarm.ringing) preview.stop() }
    fun selectTone(tone: String) {
        app.settings.update { it.copy(alarmTone = tone, alarmToneUri = "") }
        if (!app.runtime.alarm.value.ringing) preview.play(tone, app.settings.current.alarmVolume)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) app.runtime.notice.value = "Enable notifications in Android settings to see alarm controls."
    }
    fun schedule() {
        runCatching { app.alarms.schedule() }.onSuccess { app.runtime.notice.value = "Alarm set for ${fullDate(it.targetTime)}." }
            .onFailure { app.runtime.notice.value = it.message }
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled())
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.settings.update { it.copy(alarmToneUri = uri.toString()) }
        }.onFailure { app.runtime.notice.value = "This audio file could not be retained. Choose a local file." }
    }
    Page("Wake gently.", "An early wake window, with a firm deadline.") {
        Panel {
            Text("%02d:%02d".format(settings.alarmHour, settings.alarmMinute), style = MaterialTheme.typography.displayLarge)
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> app.settings.update { it.copy(alarmHour = hour, alarmMinute = minute) } },
                    settings.alarmHour, settings.alarmMinute, true).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("Choose wake time") }
            Text("Early wake window: ${settings.wakeWindowMinutes} minutes")
            Slider(value = settings.wakeWindowMinutes.toFloat(), onValueChange = { value -> app.settings.update { it.copy(wakeWindowMinutes = value.toInt()) } },
                valueRange = 0f..60f, steps = 11)
            Text(if (settings.wakeWindowMinutes == 0) "The alarm will ring at your chosen time."
                else "While tracking, a fresh light-sleep or awake estimate can start the alarm within this window. The exact deadline also works without tracking.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (app.alarms.hasExactAccess()) Button(onClick = ::schedule, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (plan?.enabled == true) "Update alarm" else "Set alarm")
            } else Button(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) {
                Text("Allow exact alarms")
            }
            if (plan?.enabled == true) {
                Text("Armed for ${fullDate(plan!!.targetTime)} • ${plan!!.windowMinutes} minute window", color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { app.alarms.cancel() }) { Text("Cancel alarm") }
                Text("Changes above take effect when you tap Update alarm.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Panel("Your wake-up sound") {
            Text("Tap a tone to select it and hear a 5-second preview.", style = MaterialTheme.typography.bodySmall)
            SynthAudio.alarmTones.forEach { tone ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = settings.alarmTone == tone && settings.alarmToneUri.isBlank(),
                        onClick = { selectTone(tone) })
                    TextButton(onClick = { selectTone(tone) }) { Text(tone) }
                }
            }
            previewTone?.let { tone ->
                Text("Previewing $tone", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { preview.stop() }) { Text("Stop preview") }
            }
            OutlinedButton(onClick = { preview.stop(); audioPicker.launch(arrayOf("audio/*")) }) {
                Text(if (settings.alarmToneUri.isBlank()) "Choose local audio file" else "Change selected audio file")
            }
            if (settings.alarmToneUri.isNotBlank()) Text("Local audio file selected", style = MaterialTheme.typography.bodySmall)
            Text("Fade in over ${settings.rampSeconds} seconds")
            Slider(value = settings.rampSeconds.toFloat(), onValueChange = { value -> app.settings.update { it.copy(rampSeconds = value.toInt()) } },
                valueRange = 5f..120f, steps = 22)
            Text("App volume: ${(settings.alarmVolume * 100).toInt()}%")
            Slider(value = settings.alarmVolume, onValueChange = { value -> app.settings.update { it.copy(alarmVolume = value) } }, valueRange = .1f..1f)
            Text("Uses Android's alarm volume. Check that volume and Do Not Disturb settings before relying on an alarm.", style = MaterialTheme.typography.bodySmall)
            Text("Snooze: ${settings.snoozeMinutes} minutes")
            Slider(value = settings.snoozeMinutes.toFloat(), onValueChange = { value -> app.settings.update { it.copy(snoozeMinutes = value.toInt()) } },
                valueRange = 1f..30f, steps = 28)
        }
        if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            Panel("Show alarms on the lock screen") {
                Text("Android controls whether Nightjar can open over the lock screen. Alarm audio and notification controls remain available.")
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }) {
                    Text("Open alarm display settings")
                }
            }
        }
    }
}

@Composable fun AlarmRingingDialog(app: AppContainer) {
    val context = LocalContext.current
    val state by app.runtime.alarm.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Good morning.") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(clockTime(System.currentTimeMillis()), style = MaterialTheme.typography.displayMedium)
            Text(if (state.playing) "Take your time. Your night is saved." else "Starting your wake-up sound…")
        } },
        confirmButton = { Button(onClick = { context.startService(Intent(context, AlarmPlaybackService::class.java).setAction(AlarmPlaybackService.STOP)) }) { Text("Dismiss") } },
        dismissButton = { OutlinedButton(onClick = { context.startService(Intent(context, AlarmPlaybackService::class.java).setAction(AlarmPlaybackService.SNOOZE)) }) {
            Text("Snooze ${app.settings.current.snoozeMinutes} min")
        } })
}
