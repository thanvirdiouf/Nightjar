// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.nightjar.sleep.core.AppSettings
import org.nightjar.sleep.core.audio.AlarmTonePreview

@RunWith(AndroidJUnit4::class)
class AlarmTonePreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.app
    private lateinit var settings: AppSettings
    private var preview: AlarmTonePreview? = null
    private var scope: CoroutineScope? = null

    @Before fun setup() {
        settings = app.settings.current
        check(!app.runtime.tracking.value.running && !app.runtime.alarm.value.ringing && app.alarms.plan.value?.enabled != true)
        compose.runOnIdle { app.runtime.notice.value = null; app.runtime.destination.value = "alarm" }
    }
    @After fun cleanup() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            preview?.let { compose.activity.lifecycle.removeObserver(it); it.stop() }
            scope?.cancel()
            app.settings.restore(settings)
            app.runtime.notice.value = null
            app.runtime.destination.value = "tonight"
        }
    }

    @Test fun selectingBuiltInTonesPreviewsThenStopsWithoutArmingAlarm() {
        val originalPlan = app.alarms.plan.value
        for (tone in listOf("Dawn", "Soft bells", "Warm pulse")) {
            compose.onNodeWithText(tone).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Previewing $tone").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(tone, app.settings.current.alarmTone) }
        }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Stop preview").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertEquals(originalPlan, app.alarms.plan.value); assertFalse(app.runtime.alarm.value.ringing) }
        // Tapping the already selected tone replays it; stopping doesn't undo selection.
        compose.onNodeWithText("Warm pulse").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Stop preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Stop preview").performScrollTo().performClick()
        compose.onAllNodesWithText("Stop preview").assertCountEquals(0)
        compose.runOnIdle { assertEquals("Warm pulse", app.settings.current.alarmTone) }
    }

    @Test fun rapidReplacementAndBackgroundingReleaseActualPlayback() {
        val failures = mutableListOf<String>()
        compose.runOnIdle {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            preview = AlarmTonePreview(compose.activity, scope!!) { failures.add(it) }
            compose.activity.lifecycle.addObserver(preview!!)
            preview!!.play("Dawn", .3f)
            preview!!.play("Soft bells", .3f)
            preview!!.play("Warm pulse", .3f)
        }
        compose.waitUntil(10_000) { preview!!.playingTone.value == "Warm pulse" }
        compose.runOnIdle { assertTrue(preview!!.isPlaying); assertTrue(failures.isEmpty()) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.onActivity {
            assertFalse(preview!!.isPlaying)
            assertNull(preview!!.playingTone.value)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertFalse(preview!!.isPlaying) }
    }
}
