package org.nightjar.sleep

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.nightjar.sleep.core.AppSettings
import org.nightjar.sleep.core.storage.*
import org.nightjar.sleep.ui.dateLabel
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.app
    private val ids = mutableListOf<Long>()
    private lateinit var settings: AppSettings
    private var latest = 0L
    @Before fun seed() = runBlocking {
        settings = app.settings.current
        check(!app.runtime.tracking.value.running && app.alarms.plan.value?.enabled != true)
        val base = System.currentTimeMillis() - 3_600_000
        for (night in 0..2) {
            val start = base - night * 86_400_000L
            val id = app.repository.dao.insertSession(SleepSession(startTime = start))
            ids += id
            if (night == 0) latest = start
            for (i in 0..19) app.repository.dao.insertEpoch(Epoch(sessionId = id, startTime = start + i * 30_000,
                durationMs = 30_000, intensity = .02f, activityScore = .02f,
                phase = if (i < 4) "LIGHT" else if (i < 10) "DEEP" else if (i < 12) "AWAKE" else "LIGHT", sampleCount = 150))
            app.repository.finish(id, start + 600_000)
        }
        compose.runOnIdle { app.runtime.destination.value = "journal" }
    }
    @After fun cleanup() = runBlocking {
        ids.forEach { app.repository.deleteSession(it) }
        compose.runOnIdle { app.settings.restore(settings); app.runtime.destination.value = "tonight" }
    }
    private fun screenshot(name: String) {
        val file = File(compose.activity.getExternalFilesDir(null), name)
        file.outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun reportNotesTrendsAndLightThemeWork() {
        compose.onNodeWithText(dateLabel(latest)).performScrollTo().performClick()
        compose.onNodeWithText("Quality estimate").assertIsDisplayed()
        compose.onNode(hasContentDescription("Sleep phase estimates.", substring = true)).performScrollTo().assertIsDisplayed()
        screenshot("report-test.png")
        compose.onNodeWithText("Sleep notes").performScrollTo().performTextInput("Slept after an evening walk")
        compose.onNodeWithText("Tags, separated by commas").performScrollTo().performTextInput("exercise, relaxed")
        compose.onNodeWithText("Save notes").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { app.repository.dao.session(ids.first())!!.note == "Slept after an evening walk" } }
        compose.onNodeWithText("Trends").performClick()
        compose.onNodeWithText("Over 3 nights").performScrollTo().assertIsDisplayed()
        screenshot("trends-test.png")
        compose.onNodeWithText("30 days").performClick()
        compose.onNodeWithText("Bedtime and wake-up rhythm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Candidate noise events").performScrollTo().assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Notes saved.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Light").performScrollTo()
        screenshot("settings-before-test.png")
        compose.onNodeWithText("Light").performClick()
        screenshot("settings-after-test.png")
        compose.runOnIdle { assertEquals("LIGHT", app.settings.current.theme) }
        compose.onNodeWithText("Make it yours.").performScrollTo()
        screenshot("settings-light-test.png")
    }
}
