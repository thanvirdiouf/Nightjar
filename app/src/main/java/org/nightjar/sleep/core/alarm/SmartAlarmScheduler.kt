package org.nightjar.sleep.core.alarm

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.analysis.SleepPhase
import java.util.UUID

class SmartAlarmScheduler(
    private val context: Context,
    private val settings: SettingsStore,
    private val runtime: RuntimeState
) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("nightjar-alarm", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(readPlan())
    private val requested = mutableSetOf<String>()
    val plan = mutable.asStateFlow()
    init { Notifications.channels(context) }
    private fun readPlan(): AlarmPlan? {
        val token = preferences.getString("token", null) ?: return null
        return AlarmPlan(token, preferences.getLong("target", 0), preferences.getInt("window", 30),
            preferences.getBoolean("enabled", false))
    }
    fun hasExactAccess(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    @Synchronized fun schedule(): AlarmPlan = scheduleAt(AlarmPolicy.nextTarget(System.currentTimeMillis(),
        settings.current.alarmHour, settings.current.alarmMinute), settings.current.wakeWindowMinutes)
    @Synchronized fun scheduleAt(target: Long, windowMinutes: Int): AlarmPlan {
        check(hasExactAccess()) { "Allow exact alarms in Android settings before setting a wake-up alarm." }
        require(target > System.currentTimeMillis()) { "Choose a wake-up time in the future." }
        val next = AlarmPlan(UUID.randomUUID().toString(), target, windowMinutes.coerceIn(0, 60))
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(target, Notifications.open(context, "alarm", 42)), pending(next))
        preferences.edit().putString("token", next.token).putLong("target", target).putInt("window", next.windowMinutes)
            .putBoolean("enabled", true).commit()
        requested.clear()
        mutable.value = next
        return next
    }
    private fun pending(plan: AlarmPlan) = PendingIntent.getBroadcast(context, 401,
        Intent(context, AlarmReceiver::class.java).setAction(DEADLINE).putExtra("token", plan.token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun cancel() {
        mutable.value?.let { manager.cancel(pending(it)) }
        preferences.edit().putBoolean("enabled", false).commit()
        requested.clear()
        mutable.value = mutable.value?.copy(enabled = false)
    }
    fun evaluateEarly(phase: SleepPhase, sampleAgeMs: Long) {
        val current = mutable.value ?: return
        if (AlarmPolicy.mayWakeEarly(current, System.currentTimeMillis(), phase, sampleAgeMs)) requestFire(current.token)
    }
    @Synchronized fun fireDeadline(token: String) {
        val current = mutable.value ?: return
        if (current.token == token && current.enabled && System.currentTimeMillis() >= current.targetTime) requestFire(token)
    }
    @Synchronized fun requestFire(token: String): Boolean {
        val current = mutable.value ?: return false
        if (!current.enabled || current.token != token || token in requested) return false
        requested.add(token)
        return try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmPlaybackService::class.java)
                .setAction(AlarmPlaybackService.PLAY).putExtra("token", token).putExtra("target", current.targetTime))
            true
        } catch (e: Exception) {
            requested.remove(token)
            runtime.notice.value = e.message ?: "The alarm could not start."
            false
        }
    }
    /** The independent deadline remains armed until actual playback starts. */
    @Synchronized fun confirmPlaybackStarted(token: String): Boolean {
        val current = mutable.value ?: return false
        if (!current.enabled || current.token != token) return false
        manager.cancel(pending(current))
        preferences.edit().putBoolean("enabled", false).commit()
        mutable.value = current.copy(enabled = false)
        requested.remove(token)
        return true
    }
    @Synchronized fun playbackFailed(token: String) { requested.remove(token) }
    @Synchronized fun reconcile(fromBoot: Boolean = false, clockChanged: Boolean = false) {
        val current = mutable.value ?: return
        if (!current.enabled) return
        if (!hasExactAccess()) {
            runtime.notice.value = "Exact alarm access was removed. Please set your wake-up alarm again."
            cancel()
        } else if (clockChanged) {
            schedule()
        } else if (current.targetTime > System.currentTimeMillis()) {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(current.targetTime, Notifications.open(context, "alarm", 42)), pending(current))
        } else if (fromBoot) {
            cancel()
            runtime.notice.value = "The wake-up time passed while the device was unavailable."
        }
    }
    companion object { const val DEADLINE = "org.nightjar.sleep.ALARM_DEADLINE" }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.orgApp().alarms.fireDeadline(intent.getStringExtra("token") ?: return)
    }
}
private fun Context.orgApp() = (applicationContext as org.nightjar.sleep.NightjarApplication).container

class AlarmReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        context.orgApp().alarms.reconcile(fromBoot = boot, clockChanged = !boot)
    }
}
