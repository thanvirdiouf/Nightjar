// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nightjar.sleep.MainActivity
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.analysis.SensingMode
import org.nightjar.sleep.tracking.TrackingForegroundService

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0C1F7), onPrimary = Color(0xFF29223F),
    secondary = Color(0xFFE9BFA2), background = Color(0xFF101522),
    surface = Color(0xFF101522), surfaceContainer = Color(0xFF1B2232),
    surfaceContainerHigh = Color(0xFF242C3E), onSurface = Color(0xFFF0EEF7),
    onSurfaceVariant = Color(0xFFB8BDCD), outline = Color(0xFF555C70))
private val LightColors = lightColorScheme(
    primary = Color(0xFF63508B), secondary = Color(0xFF82553A),
    background = Color(0xFFF7F5FB), surface = Color(0xFFF7F5FB),
    surfaceContainer = Color(0xFFEEEBF5), surfaceContainerHigh = Color(0xFFE7E1F0))

@Composable fun NightjarApp(app: AppContainer) {
    val settings by app.settings.flow.collectAsStateWithLifecycle()
    val page by app.runtime.destination.collectAsStateWithLifecycle()
    val notice by app.runtime.notice.collectAsStateWithLifecycle()
    val alarm by app.runtime.alarm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbars = remember { SnackbarHostState() }
    var microphoneDisclosure by rememberSaveable { mutableStateOf(false) }
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    fun startService() {
        runCatching { ContextCompat.startForegroundService(context,
            Intent(context, TrackingForegroundService::class.java).setAction(TrackingForegroundService.START)) }
            .onFailure { app.runtime.notice.value = it.message ?: "Tracking could not start." }
    }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val microphoneNeeded = app.settings.current.mode == SensingMode.MICROPHONE
        if (microphoneNeeded && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            app.runtime.notice.value = "Microphone permission was not granted. Choose motion mode or allow the microphone in Android settings."
        } else if (pendingStart) startService()
        pendingStart = false
    }
    fun permissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (settings.mode == SensingMode.MICROPHONE && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isEmpty()) startService() else { pendingStart = true; requestPermissions.launch(permissions.toTypedArray()) }
    }
    val dark = settings.theme == "DARK" || (settings.theme == "SYSTEM" && isSystemInDarkTheme())
    SideEffect {
        (context as? MainActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(0xFF1B2232.toInt()) else SystemBarStyle.light(0xFFF7F5FB.toInt(), 0xFFF7F5FB.toInt()))
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        LaunchedEffect(page) { focusManager.clearFocus(); keyboard?.hide() }
        LaunchedEffect(notice) { notice?.let { snackbars.showSnackbar(it); app.runtime.notice.value = null } }
        LaunchedEffect(alarm.ringing) { (context as? MainActivity)?.showAlarmOnLockScreen(alarm.ringing) }
        val destinations = listOf("tonight" to Icons.Outlined.Bedtime, "journal" to Icons.AutoMirrored.Outlined.MenuBook,
            "trends" to Icons.Outlined.BarChart, "sounds" to Icons.Outlined.GraphicEq)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    destinations.forEach { (id, icon) ->
                        NavigationBarItem(selected = page == id, onClick = { app.runtime.destination.value = id },
                            icon = { Icon(icon, null) }, label = { Text(id.replaceFirstChar { it.uppercase() }) })
                    }
                }
            },
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("NIGHTJAR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text("Your night, kept private", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { app.runtime.destination.value = "alarm" }) { Icon(Icons.Outlined.Alarm, "Alarm") }
                        IconButton(onClick = { app.runtime.destination.value = "settings" }) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                when (page) {
                    "tonight" -> TonightScreen(app) {
                        if (app.settings.current.mode == SensingMode.MICROPHONE) microphoneDisclosure = true else permissionsAndStart()
                    }
                    "journal" -> JournalScreen(app)
                    "trends" -> TrendsScreen(app)
                    "alarm" -> AlarmScreen(app)
                    "sounds" -> SoundsScreen(app)
                    "settings" -> SettingsScreen(app)
                    "licenses" -> LicensesScreen(app)
                    else -> TonightScreen(app) { permissionsAndStart() }
                }
            }
        }
        if (microphoneDisclosure) AlertDialog(
            onDismissRequest = { microphoneDisclosure = false },
            title = { Text("Microphone tracking") },
            text = { Text(if (settings.recordNoise)
                "Nightjar measures sound intensity on this phone and saves short clips around possible noise events. Clips can contain speech or other private sounds. They stay on your phone and are deleted after ${settings.retentionDays} days. Make sure people nearby agree to recording."
                else "Nightjar measures sound intensity while you track. Audio buffers are discarded after measurement; no audio clips are saved. Everything stays on your phone.") },
            confirmButton = { TextButton(onClick = { microphoneDisclosure = false; permissionsAndStart() }) { Text("Start microphone tracking") } },
            dismissButton = { TextButton(onClick = { microphoneDisclosure = false }) { Text("Cancel") } })
        if (alarm.ringing) AlarmRingingDialog(app)
    }
}
