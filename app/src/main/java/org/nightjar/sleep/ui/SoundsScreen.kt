package org.nightjar.sleep.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.audio.*

@Composable fun SoundsScreen(app: AppContainer) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val sound by app.runtime.sound.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Page("Drift off.", "Original ambient sounds, available offline.") {
        if (sound.playing) Panel("Now playing") {
            Text(sound.name, style = MaterialTheme.typography.headlineSmall)
            Text(sound.endsAt?.let { "Timer ends at ${clockTime(it)}" } ?: "No timer set")
            Button(onClick = { context.startService(Intent(context, SoundPlaybackService::class.java).setAction(SoundPlaybackService.STOP)) }) {
                Icon(Icons.Outlined.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop playback")
            }
        }
        sound.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Panel("Sleep timer") {
            Text(if (settings.soundTimerMinutes == 0) "Play until stopped" else "${settings.soundTimerMinutes} minutes")
            Slider(value = settings.soundTimerMinutes.toFloat(), onValueChange = { value -> app.settings.update { it.copy(soundTimerMinutes = value.toInt()) } },
                valueRange = 0f..180f, steps = 35)
            Text("Timer changes apply to the next sound you start.", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("Stop when sleep is estimated", "After three consecutive asleep intervals during tracking.",
                settings.stopSoundsOnSleep) { checked -> app.settings.update { it.copy(stopSoundsOnSleep = checked) } }
        }
        SynthAudio.ambientSounds.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { name ->
                    FilledTonalButton(onClick = {
                        runCatching { ContextCompat.startForegroundService(context,
                            Intent(context, SoundPlaybackService::class.java).setAction(SoundPlaybackService.PLAY).putExtra("name", name)) }
                            .onFailure { app.runtime.notice.value = it.message }
                    }, modifier = Modifier.weight(1f).height(90.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.PlayArrow, null); Text(name) }
                    }
                }
            }
        }
        Text("Ambient playback can affect microphone measurements. Use motion mode for quieter estimates while sounds are playing.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
