// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nightjar.sleep.core.AppContainer

@Composable fun LicensesScreen(app: AppContainer) {
    val context = LocalContext.current
    val documents = listOf("Nightjar" to "GPL-3.0.txt", "Third-party" to "THIRD_PARTY_NOTICES.txt", "Apache 2.0" to "Apache-2.0.txt")
    var selected by remember { mutableIntStateOf(0) }
    val text by produceState("Loading license…", selected) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("licenses/" + documents[selected].second).bufferedReader().use { it.readText() }
        }
    }
    BackHandler { app.runtime.destination.value = "settings" }
    Page("Open-source licenses", "Nightjar • GPL version 3 or later") {
        TextButton(onClick = { app.runtime.destination.value = "settings" }) { Text("Back to settings") }
        Text("Copyright © 2026 Nightjar contributors. You may redistribute and modify Nightjar under GPLv3 or later. Provided without warranty.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            documents.forEachIndexed { index, entry ->
                FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(entry.first) })
            }
        }
        SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
    }
}
