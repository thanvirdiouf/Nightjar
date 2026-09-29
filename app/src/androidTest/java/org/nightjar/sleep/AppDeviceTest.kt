// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.nightjar.sleep.core.analysis.SleepPhase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.storage.SleepDatabase
import org.nightjar.sleep.core.alarm.AlarmPlaybackService
import org.nightjar.sleep.core.analysis.SensingMode
import org.nightjar.sleep.core.audio.SoundPlaybackService
import org.nightjar.sleep.tracking.TrackingForegroundService

@RunWith(AndroidJUnit4::class)
class AppDeviceTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.app
    private var savedSettings: AppSettings? = null
    private val createdSessions = mutableListOf<Long>()
    @Before fun setup() {
        savedSettings = app.settings.current
        assertFalse("A user tracking session must not be running during tests", app.runtime.tracking.value.running)
        assertTrue("Cancel user alarms before testing", app.alarms.plan.value?.enabled != true)
        compose.runOnIdle { app.runtime.destination.value = "tonight" }
    }
    @After fun cleanup() {
        compose.runOnIdle {
            compose.activity.startService(Intent(compose.activity, TrackingForegroundService::class.java).setAction(TrackingForegroundService.STOP))
            compose.activity.startService(Intent(compose.activity, AlarmPlaybackService::class.java).setAction(AlarmPlaybackService.STOP))
            compose.activity.startService(Intent(compose.activity, SoundPlaybackService::class.java).setAction(SoundPlaybackService.STOP))
            app.alarms.cancel()
            app.runtime.notice.value = null
            savedSettings?.let { app.settings.restore(it) }
            app.runtime.destination.value = "tonight"
        }
        waitFor(10_000) { !app.runtime.tracking.value.running && !app.runtime.tracking.value.starting }
        runBlocking { createdSessions.forEach { id ->
            app.repository.finish(id, System.currentTimeMillis(), true)
            app.repository.deleteSession(id)
        } }
    }
    private fun waitFor(timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("Condition did not become true in ${timeout}ms", condition())
    }
    @Test fun trackingUiPersistsRealSensorEpochAndEndsCleanly() {
        compose.runOnIdle { app.settings.update { it.copy(mode = SensingMode.ACCELEROMETER) } }
        compose.onNodeWithText("Start sleeping").performScrollTo().performClick()
        waitFor { app.runtime.tracking.value.running }
        val id = app.runtime.tracking.value.sessionId!!
        createdSessions += id
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFor(40_000) { app.runtime.tracking.value.epochs > 0 }
        val epochs = runBlocking { app.repository.dao.epochs(id) }
        assertTrue(epochs.isNotEmpty())
        assertTrue("Real accelerometer samples must reach storage", epochs.first().sampleCount >= 3)
        assertNotEquals("UNKNOWN", epochs.first().phase)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("End session").performScrollTo().performClick()
        waitFor { !app.runtime.tracking.value.running }
        val session = runBlocking { app.repository.dao.session(id)!! }
        assertNotNull(session.endTime)
        assertEquals("COMPLETE", session.status)
        val reopened = SleepDatabase.create(compose.activity)
        try { assertEquals(session, runBlocking { reopened.dao().session(id) }) } finally { reopened.close() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Your sleep journal.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your sleep journal.").assertIsDisplayed()
        runBlocking {
            app.repository.saveJournal(id, "A test note", setOf("exercise"))
            assertEquals("A test note", app.repository.dao.session(id)!!.note)
            assertTrue(app.repository.dao.allTags().any { it.sessionId == id && it.tag == "exercise" })
        }
    }
    @Test fun microphoneRequiresDisclosureAndProvidesSamples() {
        compose.runOnIdle { app.settings.update { it.copy(mode = SensingMode.MICROPHONE, recordNoise = false) } }
        compose.onNodeWithText("Start sleeping").performScrollTo().performClick()
        compose.onNodeWithText("Microphone tracking").assertIsDisplayed()
        assertFalse(app.runtime.tracking.value.running)
        compose.onNodeWithText("Start microphone tracking").performClick()
        waitFor { app.runtime.tracking.value.running }
        val id = app.runtime.tracking.value.sessionId!!
        createdSessions += id
        waitFor { app.runtime.tracking.value.samples >= 4 }
        compose.onNodeWithText("End session").performScrollTo().performClick()
        waitFor { !app.runtime.tracking.value.running }
        val rows = runBlocking { app.repository.dao.epochs(id) }
        assertTrue(rows.sumOf { it.sampleCount } > 0)
        assertTrue(runBlocking { app.repository.dao.noise(id) }.isEmpty())
    }
    @Test fun exactDeadlineRingsWithoutTrackingAndSnoozeReschedules() {
        compose.runOnIdle {
            app.settings.update { it.copy(snoozeMinutes = 1, rampSeconds = 5, alarmToneUri = "content://org.nightjar.sleep.missing/tone") }
            app.alarms.scheduleAt(System.currentTimeMillis() + 3_000, 0)
        }
        waitFor(15_000) { app.runtime.alarm.value.playing }
        assertFalse(app.runtime.tracking.value.running)
        assertEquals(false, app.alarms.plan.value?.enabled)
        compose.onNodeWithText("Good morning.").assertIsDisplayed()
        compose.onNodeWithText("Snooze 1 min").performClick()
        waitFor { !app.runtime.alarm.value.ringing && app.alarms.plan.value?.enabled == true }
        val plan = app.alarms.plan.value!!
        assertTrue(plan.targetTime - System.currentTimeMillis() in 40_000..60_000)
        assertEquals(0, plan.windowMinutes)
        compose.runOnIdle { app.alarms.cancel() }
    }
    @Test fun originalAmbientSoundPlaysAndStops() {
        compose.runOnIdle {
            app.settings.update { it.copy(stopSoundsOnSleep = false, soundTimerMinutes = 1) }
            app.runtime.destination.value = "sounds"
        }
        compose.onNodeWithText("Rain", useUnmergedTree = true).performScrollTo().performClick()
        waitFor(15_000) { app.runtime.sound.value.playing }
        assertNotNull(app.runtime.sound.value.endsAt)
        compose.onNodeWithText("Stop playback").performScrollTo().performClick()
        waitFor { !app.runtime.sound.value.playing }
    }
    @Test fun deadlineScheduledFromConfirmedIdleRingsWithoutActivity() {
        fun shell(command: String): String =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
            }
        compose.runOnIdle {
            app.settings.update { it.copy(alarmToneUri = "", rampSeconds = 5) }
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val deepWasEnabled = shell("dumpsys deviceidle enabled deep") == "1"
        try {
            if (!deepWasEnabled) shell("dumpsys deviceidle enable deep")
            shell("dumpsys deviceidle force-idle")
            assertEquals("IDLE", shell("dumpsys deviceidle get deep"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                app.alarms.scheduleAt(System.currentTimeMillis() + 3_000, 0)
            }
            waitFor(15_000) { app.runtime.alarm.value.playing }
            assertEquals(false, app.alarms.plan.value?.enabled)
        } finally {
            shell("dumpsys deviceidle unforce")
            if (!deepWasEnabled) shell("dumpsys deviceidle disable deep")
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
        compose.onNodeWithText("Dismiss").performClick()
        waitFor { !app.runtime.alarm.value.ringing }
    }
    @Test fun earlyWakeAcceptsFreshLightSignalOnceAndIgnoresDeep() {
        compose.runOnIdle {
            app.settings.update { it.copy(alarmToneUri = "") }
            app.alarms.scheduleAt(System.currentTimeMillis() + 50_000, 1)
            app.alarms.evaluateEarly(SleepPhase.DEEP, 0)
        }
        assertFalse(app.runtime.alarm.value.ringing)
        val token = app.alarms.plan.value!!.token
        compose.runOnIdle {
            app.alarms.evaluateEarly(SleepPhase.LIGHT, 0)
            assertFalse("Duplicate start must be rejected", app.alarms.requestFire(token))
        }
        waitFor(15_000) { app.runtime.alarm.value.playing }
        assertFalse(app.alarms.requestFire(token))
        compose.onNodeWithText("Dismiss").performClick()
        waitFor { !app.runtime.alarm.value.ringing }
    }

    @Test fun ambientTimerExpiresAndSleepOnsetStopsPlayback() {
        compose.runOnIdle {
            app.settings.update { it.copy(soundTimerMinutes = 1, stopSoundsOnSleep = false) }
            ContextCompat.startForegroundService(compose.activity, Intent(compose.activity, SoundPlaybackService::class.java)
                .setAction(SoundPlaybackService.PLAY).putExtra("name", "Rain"))
        }
        waitFor(15_000) { app.runtime.sound.value.playing }
        waitFor(70_000) { !app.runtime.sound.value.playing }
        compose.runOnIdle {
            app.settings.update { it.copy(soundTimerMinutes = 0, stopSoundsOnSleep = true, mode = SensingMode.ACCELEROMETER) }
            ContextCompat.startForegroundService(compose.activity, Intent(compose.activity, SoundPlaybackService::class.java)
                .setAction(SoundPlaybackService.PLAY).putExtra("name", "Ocean"))
            ContextCompat.startForegroundService(compose.activity, Intent(compose.activity, TrackingForegroundService::class.java)
                .setAction(TrackingForegroundService.START))
        }
        waitFor { app.runtime.tracking.value.running }
        createdSessions += app.runtime.tracking.value.sessionId!!
        waitFor(15_000) { app.runtime.sound.value.playing }
        waitFor(105_000) { app.runtime.tracking.value.asleepEpochs >= 3 }
        waitFor { !app.runtime.sound.value.playing }
    }

}
