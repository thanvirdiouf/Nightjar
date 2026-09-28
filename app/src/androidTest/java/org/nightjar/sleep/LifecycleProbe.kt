package org.nightjar.sleep

import android.app.Activity
import android.app.AlarmManager
import androidx.test.runner.AndroidJUnitRunner
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import org.nightjar.sleep.core.analysis.SensingMode
import org.nightjar.sleep.tracking.TrackingForegroundService

/** Explicitly invoked lifecycle probe, packaged only in the test APK. */
class LifecycleProbe : AndroidJUnitRunner() {
    private lateinit var arguments: Bundle
    override fun onCreate(arguments: Bundle?) {
        this.arguments = arguments ?: Bundle()
        super.onCreate(arguments)
    }
    override fun onStart() {
        if (!arguments.containsKey("operation")) { super.onStart(); return }
        val result = Bundle()
        try {
            val application = targetContext.applicationContext as NightjarApplication
            val app = application.container
            val probePreferences = targetContext.getSharedPreferences("nightjar-lifecycle-probe", 0)
            fun saveSettings() { check(probePreferences.edit().putString("settings", app.settings.current.toJson().toString()).commit()) }
            fun restoreSettings() {
                probePreferences.getString("settings", null)?.let {
                    app.settings.restore(org.nightjar.sleep.core.AppSettings.fromJson(org.json.JSONObject(it)))
                }
                probePreferences.edit().clear().commit()
            }
            when (arguments.getString("operation")) {
                "arm-reboot" -> {
                    check(!app.runtime.tracking.value.running && runBlocking { app.repository.dao.activeSession() } == null)
                    val plan = app.alarms.scheduleAt(System.currentTimeMillis() + 10 * 60_000, 0)
                    result.putString("target", plan.targetTime.toString())
                    result.putString("token", plan.token)
                }
                "verify-alarm" -> {
                    val expected = arguments.getString("target")!!.toLong()
                    val plan = app.alarms.plan.value ?: error("No alarm persisted.")
                    check(plan.enabled && plan.targetTime == expected) { "Alarm plan changed or was disabled." }
                    check(targetContext.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime == expected) {
                        "The exact alarm was not registered with Android."
                    }
                    app.alarms.cancel()
                    result.putString("verified", "persisted and scheduled")
                }
                "arm-local-time" -> {
                    saveSettings()
                    val local = java.time.ZonedDateTime.now().plusHours(2)
                    app.settings.update { it.copy(alarmHour = local.hour, alarmMinute = local.minute) }
                    val plan = app.alarms.schedule()
                    result.putString("target", plan.targetTime.toString())
                    result.putString("token", plan.token)
                }
                "verify-local-time" -> {
                    val expected = org.nightjar.sleep.core.alarm.AlarmPolicy.nextTarget(System.currentTimeMillis(),
                        app.settings.current.alarmHour, app.settings.current.alarmMinute)
                    val plan = app.alarms.plan.value ?: error("No alarm after timezone change.")
                    check(plan.enabled && plan.targetTime == expected) { "Alarm was not reconciled to the selected local time." }
                    check(plan.token != arguments.getString("oldToken")) { "The time-change receiver did not reschedule the alarm." }
                    check(targetContext.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime == expected)
                    app.alarms.cancel()
                    restoreSettings()
                    result.putString("verified", "local time reconciled")
                }
                "prepare-tracking" -> {
                    saveSettings()
                    check(runBlocking { app.repository.dao.activeSession() } == null)
                    app.alarms.cancel()
                    startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    runOnMainSync {
                        app.settings.update { it.copy(mode = SensingMode.ACCELEROMETER, recordNoise = false) }
                        ContextCompat.startForegroundService(targetContext, Intent(targetContext, TrackingForegroundService::class.java)
                            .setAction(TrackingForegroundService.START))
                    }
                    waitUntil(40_000) { app.runtime.tracking.value.epochs >= 1 }
                    result.putString("session", app.runtime.tracking.value.sessionId.toString())
                }
                "verify-recovery" -> {
                    val id = arguments.getString("session")!!.toLong()
                    val saved = runBlocking { app.repository.dao.activeSession() } ?: error("No recoverable session.")
                    check(saved.id == id && saved.endTime == null)
                    val oldCount = runBlocking { app.repository.dao.epochs(id).size }
                    startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    runOnMainSync {
                        ContextCompat.startForegroundService(targetContext, Intent(targetContext, TrackingForegroundService::class.java)
                            .setAction(TrackingForegroundService.START))
                    }
                    waitUntil(40_000) { app.runtime.tracking.value.running && app.runtime.tracking.value.epochs >= oldCount + 2 }
                    runOnMainSync {
                        targetContext.startService(Intent(targetContext, TrackingForegroundService::class.java).setAction(TrackingForegroundService.STOP))
                    }
                    waitUntil(10_000) { !app.runtime.tracking.value.running }
                    runBlocking {
                        val epochs = app.repository.dao.epochs(id)
                        check(epochs.any { it.phase == "UNKNOWN" && it.durationMs > 1_000 }) { "Interruption was not marked unknown." }
                        check(app.repository.dao.session(id)?.endTime != null)
                        app.repository.deleteSession(id)
                    }
                    restoreSettings()
                    result.putString("verified", "session resumed with unknown gap and ended")
                }
                "cleanup" -> { app.alarms.cancel(); restoreSettings() }
                else -> error("Unknown lifecycle operation")
            }
            result.putString("stream", "Lifecycle probe passed: ${arguments.getString("operation")}\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "Lifecycle probe FAILED: ${e.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun waitUntil(timeout: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        check(predicate()) { "Timed out after ${timeout}ms." }
    }
}
