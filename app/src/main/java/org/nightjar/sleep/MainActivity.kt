package org.nightjar.sleep

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
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
        setShowWhenLocked(ringing)
        setTurnScreenOn(ringing)
        if (ringing) getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
    }
}
