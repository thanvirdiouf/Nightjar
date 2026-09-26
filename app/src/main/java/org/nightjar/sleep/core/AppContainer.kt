package org.nightjar.sleep.core

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.nightjar.sleep.core.alarm.SmartAlarmScheduler
import org.nightjar.sleep.core.analysis.*
import org.nightjar.sleep.core.storage.*

data class TrackingState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val sessionId: Long? = null,
    val startTime: Long = 0,
    val mode: SensingMode = SensingMode.ACCELEROMETER,
    val intensity: Float = 0f,
    val phase: SleepPhase = SleepPhase.UNKNOWN,
    val epochs: Int = 0,
    val asleepEpochs: Int = 0,
    val samples: Int = 0,
    val error: String? = null
)
data class SoundState(val playing: Boolean = false, val name: String = "", val endsAt: Long? = null, val error: String? = null)
data class AlarmState(val ringing: Boolean = false, val playing: Boolean = false, val targetTime: Long = 0, val error: String? = null)

class RuntimeState {
    val tracking = MutableStateFlow(TrackingState())
    val alarm = MutableStateFlow(AlarmState())
    val sound = MutableStateFlow(SoundState())
    val notice = MutableStateFlow<String?>(null)
    val destination = MutableStateFlow("tonight")
}

class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = SleepDatabase.create(context)
    val settings = SettingsStore(context)
    val runtime = RuntimeState()
    val repository = SleepRepository(context, database)
    val alarms = SmartAlarmScheduler(context, settings, runtime)
}
