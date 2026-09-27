package org.nightjar.sleep.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.analysis.SensingMode

@Composable fun SettingsScreen(app: AppContainer) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val tracking by app.runtime.tracking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var recordingConsent by remember { mutableStateOf(false) }
    Page("Make it yours.", "Everything stays on this phone.") {
        Panel("Sensing") {
            listOf(SensingMode.ACCELEROMETER to "Motion on the mattress", SensingMode.MICROPHONE to "Sound from the nightstand").forEach { (mode, label) ->
                Row {
                    RadioButton(selected = settings.mode == mode, enabled = !tracking.running && !tracking.starting,
                        onClick = { app.settings.update { it.copy(mode = mode) } })
                    TextButton(enabled = !tracking.running && !tracking.starting,
                        onClick = { app.settings.update { it.copy(mode = mode) } }) { Text(label) }
                }
            }
            if (tracking.running) Text("End this session before changing its sensing mode.")
            Text("Tracking stores one activity summary about every 30 seconds. No raw motion samples are retained.", style = MaterialTheme.typography.bodySmall)
        }
        Panel("Noise recording") {
            SettingSwitch("Save candidate noise clips", "Microphone mode only. Short clips stay local and may contain private sounds.",
                settings.recordNoise, enabled = !tracking.running && !tracking.starting) { enabled ->
                if (enabled) recordingConsent = true else app.settings.update { it.copy(recordNoise = false) }
            }
            Text("Keep clips for ${settings.retentionDays} days")
            Slider(value = settings.retentionDays.toFloat(), onValueChange = { value -> app.settings.update { it.copy(retentionDays = value.toInt()) } },
                valueRange = 1f..90f, steps = 88)
            Text("Expired clips are removed when the app opens or tracking starts. Event timestamps remain in your reports.", style = MaterialTheme.typography.bodySmall)
        }
        Panel("Appearance") {
            listOf("DARK", "LIGHT", "SYSTEM").forEach { theme ->
                Row {
                    RadioButton(selected = settings.theme == theme, onClick = { app.settings.update { it.copy(theme = theme) } })
                    TextButton(onClick = { app.settings.update { it.copy(theme = theme) } }) { Text(theme.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
        Panel("Sensor calibration") {
            Text("Adjust sensitivity for your phone and placement. Higher thresholds classify more activity as sleep. Changes apply to the next new session.", style = MaterialTheme.typography.bodySmall)
            Text("Quiet threshold: %.2f".format(settings.lowThreshold))
            Slider(value = settings.lowThreshold, onValueChange = { value ->
                app.settings.update { it.copy(lowThreshold = value, highThreshold = maxOf(it.highThreshold, value + .02f)) }
            }, valueRange = .01f..0.6f)
            Text("Awake threshold: %.2f".format(settings.highThreshold))
            Slider(value = settings.highThreshold, onValueChange = { value -> app.settings.update { it.copy(highThreshold = value) } },
                valueRange = (settings.lowThreshold + .02f)..1f)
            OutlinedButton(onClick = { app.settings.update { it.copy(lowThreshold = .08f, highThreshold = .35f) } }) { Text("Restore default sensitivity") }
        }
        DataSettings(app)
        Panel("Android permissions") {
            Text("Notification, microphone, battery, and alarm permissions are controlled by Android. Restrictive battery settings can interrupt tracking.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app settings") }
        }
        Panel("About Nightjar") {
            Text("Original, open-source sleep tracking. Apache License 2.0. No accounts, ads, internet permission, or cloud uploads.")
            Text("Estimates are based on movement or sound and are not a medical measurement. Room stores your nights locally; generated ambient audio is original to Nightjar.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (recordingConsent) AlertDialog(onDismissRequest = { recordingConsent = false },
        title = { Text("Save short local recordings?") },
        text = { Text("While microphone tracking is active, Nightjar can save a few seconds around candidate noise events. Clips may contain speech. Tell people nearby and get their agreement. Clips stay on this phone until their retention period ends or you delete the night.") },
        confirmButton = { TextButton(onClick = { app.settings.update { it.copy(recordNoise = true) }; recordingConsent = false }) { Text("Enable local clips") } },
        dismissButton = { TextButton(onClick = { recordingConsent = false }) { Text("Cancel") } })
}
