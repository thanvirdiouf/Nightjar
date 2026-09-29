// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.nightjar.sleep.ui.NightjarApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        route(intent)
        setContent { NightjarApp(app) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }
    private fun route(intent: Intent?) {
        intent?.getStringExtra("screen")?.let { screen ->
            if (screen in setOf("tonight", "journal", "trends", "alarm", "sounds", "settings")) app.runtime.destination.value = screen
        }
    }
    fun showAlarmOnLockScreen(ringing: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(ringing)
            setTurnScreenOn(ringing)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (ringing) window.addFlags(flags) else window.clearFlags(flags)
        }
        if (ringing) getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
    }
}
