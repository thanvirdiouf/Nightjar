package org.nightjar.sleep.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.storage.DataTransfer

@Composable fun DataSettings(app: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracking by app.runtime.tracking.collectAsStateWithLifecycle()
    val alarm by app.alarms.plan.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val transfer = remember { DataTransfer(context, app.database, app.settings) }
    fun export(uri: Uri?, kind: String) {
        if (uri == null) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("The chosen file could not be opened.")
                    stream.use {
                        when (kind) { "csv" -> transfer.exportCsv(it); "json" -> transfer.exportJson(it); else -> transfer.backup(it) }
                    }
                }
                app.runtime.notice.value = "File saved."
            } catch (e: Exception) { app.runtime.notice.value = e.message ?: "Export failed." }
            finally { busy = false }
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { export(it, "csv") }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { export(it, "json") }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { export(it, "zip") }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    Panel("Your data") {
        Text("CSV summarizes nights. JSON includes activity intervals and notes. A local ZIP backup also includes settings and any retained audio clips.",
            style = MaterialTheme.typography.bodySmall)
        val enabled = !busy && !tracking.running && !tracking.starting
        if (!enabled) Text(if (busy) "Working with your file…" else "End the current session to export or restore.")
        OutlinedButton(onClick = { csv.launch("nightjar-sessions.csv") }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
        OutlinedButton(onClick = { json.launch("nightjar-sessions.json") }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Export JSON") }
        OutlinedButton(onClick = { zip.launch("nightjar-backup.zip") }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Save local backup") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = enabled && alarm?.enabled != true, modifier = Modifier.fillMaxWidth()) { Text("Restore local backup") }
        if (alarm?.enabled == true) Text("Cancel the armed alarm before restoring settings.", style = MaterialTheme.typography.bodySmall)
        Text("Choose a location on this device for an offline backup. Exported files contain personal sleep data and are not encrypted.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    restoreUri?.let { uri ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Restore this backup?") },
            text = { Text("New nights and recordings will be added. Nights with matching start time, end time and mode are skipped. Preferences are restored; microphone clip recording stays off until you enable it again. Alarms and external audio-file access are not imported.") },
            confirmButton = { TextButton(onClick = {
                restoreUri = null; busy = true
                scope.launch {
                    try {
                        check(app.alarms.plan.value?.enabled != true && !app.runtime.alarm.value.ringing) { "Dismiss or cancel the alarm before restoring." }
                        val result = withContext(Dispatchers.IO) {
                            val input = context.contentResolver.openInputStream(uri) ?: error("The backup could not be opened.")
                            input.use { transfer.restore(it) }
                        }
                        app.runtime.notice.value = "Restored ${result.imported} nights; skipped ${result.skipped} existing nights."
                    } catch (e: Exception) { app.runtime.notice.value = e.message ?: "The backup could not be restored." }
                    finally { busy = false }
                }
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } })
    }
}
