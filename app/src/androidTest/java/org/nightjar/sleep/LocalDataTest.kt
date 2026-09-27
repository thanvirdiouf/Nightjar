package org.nightjar.sleep

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.storage.*
import org.nightjar.sleep.core.audio.WavFiles
import org.nightjar.sleep.core.audio.NoiseRecorder
import kotlin.math.*
import android.media.MediaPlayer
import java.io.*
import java.util.UUID
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class LocalDataTest {
    private val context = ApplicationProvider.getApplicationContext<NightjarApplication>()
    private lateinit var source: SleepDatabase
    private lateinit var target: SleepDatabase
    private lateinit var settings: SettingsStore
    private lateinit var saved: AppSettings
    private val clips = mutableListOf<File>()
    @Before fun setup() {
        source = Room.inMemoryDatabaseBuilder(context, SleepDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, SleepDatabase::class.java).build()
        settings = SettingsStore(context)
        saved = settings.current
    }
    @After fun cleanup() = runBlocking {
        listOf(source, target).forEach { db ->
            db.dao().allSessions().forEach { s -> db.dao().noise(s.id).forEach { e ->
                SleepRepository(context, db).clipFile(e.clipPath)?.delete()
            } }
            db.close()
        }
        clips.forEach { it.delete() }
        settings.restore(saved)
    }
    private suspend fun sampleNight(): Long {
        val dao = source.dao()
        val start = System.currentTimeMillis() - 600_000
        val id = dao.insertSession(SleepSession(startTime = start))
        for (i in 0..19) dao.insertEpoch(Epoch(sessionId = id, startTime = start + i * 30_000,
            durationMs = 30_000, intensity = .02f, activityScore = .02f, phase = if (i < 4) "LIGHT" else "DEEP", sampleCount = 150))
        SleepRepository(context, source).finish(id, start + 600_000)
        SleepRepository(context, source).saveJournal(id, "rested, and calm\nsecond line", setOf("exercise"))
        return id
    }
    @Test fun backupRestoresMetricsNotesTagsAudioAndSkipsDuplicates() = runBlocking {
        val id = sampleNight()
        val start = source.dao().session(id)!!.startTime
        val file = File(context.filesDir, "recordings/${UUID.randomUUID()}.wav")
        clips += file
        WavFiles.write(file, ShortArray(16_000))
        source.dao().insertNoise(NoiseEvent(sessionId = id, timestamp = start + 30_000, durationMs = 1000, type = "NOISE_BURST", clipPath = file.name))
        settings.update { it.copy(recordNoise = true, theme = "LIGHT") }
        val bytes = ByteArrayOutputStream()
        DataTransfer(context, source, settings).backup(bytes)
        val restore = DataTransfer(context, target, settings)
        val result = restore.restore(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, result.imported)
        val imported = target.dao().allSessions().single()
        assertEquals(source.dao().session(id)!!.score, imported.score)
        assertEquals("rested, and calm\nsecond line", imported.note)
        assertEquals("exercise", target.dao().allTags().single().tag)
        val clip = SleepRepository(context, target).clipFile(target.dao().noise(imported.id).single().clipPath)!!
        assertArrayEquals(file.readBytes(), clip.readBytes())
        assertFalse("Recording consent must not come from a backup", settings.current.recordNoise)
        assertEquals("LIGHT", settings.current.theme)
        assertEquals(1, restore.restore(ByteArrayInputStream(bytes.toByteArray())).skipped)
        assertEquals(1, target.dao().allSessions().size)
    }
    @Test fun exportsContainRealDataAndEscapeCsvNotes() = runBlocking {
        val id = sampleNight()
        SleepRepository(context, source).saveJournal(id, "=SUM(1,2)", emptySet())
        val csv = ByteArrayOutputStream()
        val json = ByteArrayOutputStream()
        val transfer = DataTransfer(context, source, settings)
        transfer.exportCsv(csv); transfer.exportJson(json)
        assertTrue(csv.toString("UTF-8").contains("\"'=SUM(1,2)\""))
        val root = org.json.JSONObject(json.toString("UTF-8"))
        assertEquals(20, root.getJSONArray("sessions").getJSONObject(0).getJSONArray("epochs").length())
        assertEquals("=SUM(1,2)", root.getJSONArray("sessions").getJSONObject(0).getString("note"))
    }
    @Test fun unsafeZipPathAndInvalidIntervalsDoNotAlterData() = runBlocking {
        val unsafe = ByteArrayOutputStream()
        ZipOutputStream(unsafe).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.wav")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertTrue(runCatching { DataTransfer(context, target, settings).restore(ByteArrayInputStream(unsafe.toByteArray())) }.isFailure)
        sampleNight()
        val jsonBytes = ByteArrayOutputStream()
        DataTransfer(context, source, settings).exportJson(jsonBytes)
        val root = org.json.JSONObject(jsonBytes.toString("UTF-8"))
        root.getJSONArray("sessions").getJSONObject(0).getJSONArray("epochs").getJSONObject(2).put("startTime", 1)
        val malformed = ByteArrayOutputStream()
        ZipOutputStream(malformed).use { zip ->
            zip.putNextEntry(ZipEntry("nightjar.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
        }
        assertTrue(runCatching { DataTransfer(context, target, settings).restore(ByteArrayInputStream(malformed.toByteArray())) }.isFailure)
        assertTrue(target.dao().allSessions().isEmpty())
    }
    @Test fun interruptedSessionRecoveryMarksTheGapUnknown() = runBlocking {
        val repo = SleepRepository(context, source)
        val start = System.currentTimeMillis() - 120_000
        val original = repo.startOrResume(AppSettings(), start)
        source.dao().insertEpoch(Epoch(sessionId = original.id, startTime = start, durationMs = 30_000,
            intensity = .02f, activityScore = .02f, phase = "LIGHT", sampleCount = 150))
        val resumed = repo.startOrResume(AppSettings(), start + 120_000)
        assertEquals(original.id, resumed.id)
        val gap = source.dao().epochs(original.id).last()
        assertEquals("UNKNOWN", gap.phase)
        assertEquals(90_000, gap.durationMs)
        val ended = repo.finish(original.id, start + 120_000, true)!!
        assertEquals("INTERRUPTED", ended.status)
        assertNull("Low coverage must not generate a score", ended.score)
    }
    @Test fun detectedClipsArePlayableAndRetentionDeletesOnlyAudio() = runBlocking {
        val id = sampleNight()
        var failure: String? = null
        val recorder = NoiseRecorder(context, source.dao(), id) { failure = it }
        val tone = ShortArray(32_000) { (sin(2 * PI * 100 * it / 16_000) * 9000).toInt().toShort() }
        (tone + ShortArray(16_000)).toList().chunked(1024).forEach { recorder.accept(it.toShortArray(), 16_000) }
        recorder.finish()
        assertNull(failure)
        val event = source.dao().noise(id).single()
        assertEquals("POSSIBLE_SNORING", event.type)
        val repository = SleepRepository(context, source)
        val file = repository.clipFile(event.clipPath)!!
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath); player.prepare()
            assertTrue(player.duration in 2000..3000)
        } finally { player.release() }
        source.dao().updateNoise(event.copy(timestamp = System.currentTimeMillis() - 9 * 86_400_000L))
        repository.pruneClips(7)
        assertFalse(file.exists())
        assertNull(source.dao().noise(id).single().clipPath)
    }

}
