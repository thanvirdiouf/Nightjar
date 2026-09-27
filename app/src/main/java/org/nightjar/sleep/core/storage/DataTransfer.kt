package org.nightjar.sleep.core.storage

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nightjar.sleep.core.AppSettings
import org.nightjar.sleep.core.SettingsStore
import org.nightjar.sleep.core.analysis.*
import java.io.*
import java.util.UUID
import java.util.zip.*

class DataTransfer(private val context: Context, private val database: SleepDatabase, private val settings: SettingsStore) {
    private val dao = database.dao()
    private val repository = SleepRepository(context, database)
    private suspend fun snapshot(includeClips: Boolean): JSONObject = database.withTransaction {
        check(dao.activeSession() == null) { "Finish the saved tracking session before exporting or restoring." }
        val tags = dao.allTags().groupBy { it.sessionId }
        JSONObject().put("format", "nightjar").put("version", 1).put("settings", settings.current.toJson())
            .put("sessions", JSONArray().apply {
                dao.allSessions().forEach { s ->
                    put(JSONObject().put("startTime", s.startTime).put("endTime", s.endTime).put("mode", s.mode)
                        .put("note", s.note).put("status", s.status).put("lowThreshold", s.lowThreshold.toDouble()).put("highThreshold", s.highThreshold.toDouble())
                        .put("tags", JSONArray(tags[s.id].orEmpty().map { it.tag }))
                        .put("epochs", JSONArray().apply {
                            dao.epochs(s.id).forEach { e ->
                                put(JSONObject().put("startTime", e.startTime).put("durationMs", e.durationMs)
                                    .put("intensity", e.intensity.toDouble()).put("activityScore", e.activityScore.toDouble())
                                    .put("phase", e.phase).put("sampleCount", e.sampleCount))
                            }
                        }).put("noise", JSONArray().apply {
                            dao.noise(s.id).forEach { e ->
                                val clip = if (includeClips) repository.clipFile(e.clipPath)?.name else null
                                put(JSONObject().put("timestamp", e.timestamp).put("durationMs", e.durationMs).put("type", e.type)
                                    .put("clip", clip ?: JSONObject.NULL))
                            }
                        }))
                }
            })
    }
    suspend fun exportJson(output: OutputStream) = withContext(Dispatchers.IO) {
        output.bufferedWriter().use { it.write(snapshot(false).toString(2)) }
    }
    suspend fun exportCsv(output: OutputStream) = withContext(Dispatchers.IO) {
        val sessions = database.withTransaction { dao.allSessions() }
        fun cell(value: Any?): String = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
        output.bufferedWriter().use { writer ->
            writer.appendLine("start_time_ms,end_time_ms,mode,score,sleep_ms,latency_ms,awake_ms,deep_percent,awakenings,status,note")
            sessions.forEach { s ->
                // Prefix spreadsheet formulas in free text to keep exported notes inert when opened.
                val note = if (s.note.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'" + s.note else s.note
                writer.appendLine(listOf(s.startTime,s.endTime,s.mode,s.score,s.sleepMs,s.latencyMs,s.awakeMs,s.deepPercent,s.awakenings,s.status,note).joinToString(",") { cell(it) })
            }
        }
    }
    suspend fun backup(output: OutputStream) = withContext(Dispatchers.IO) {
        val json = snapshot(true)
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val names = mutableSetOf<String>()
            val sessions = json.getJSONArray("sessions")
            for (i in 0 until sessions.length()) {
                val noise = sessions.getJSONObject(i).getJSONArray("noise")
                for (j in 0 until noise.length()) {
                    val name = noise.getJSONObject(j).optString("clip").takeIf { it != "null" && it.isNotEmpty() } ?: continue
                    if (!names.add(name)) continue
                    val file = repository.clipFile(name)
                    if (file == null) noise.getJSONObject(j).put("clip", JSONObject.NULL)
                    else {
                        zip.putNextEntry(ZipEntry("recordings/$name"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            zip.putNextEntry(ZipEntry("nightjar.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
    }
    data class RestoreResult(val imported: Int, val skipped: Int)
    suspend fun restore(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        check(dao.activeSession() == null) { "Finish the saved tracking session before restoring." }
        val stage = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val moved = mutableListOf<File>()
        var committed = false
        try {
            var total = 0L
            val names = mutableSetOf<String>()
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name)) { "Invalid or duplicate backup entry." }
                    val isJson = entry.name == "nightjar.json"
                    require(isJson || entry.name.matches(Regex("recordings/[A-Za-z0-9_-]{1,80}\\.wav"))) { "Unexpected backup file." }
                    val limit = if (isJson) 32L * 1024 * 1024 else 400_000L
                    val file = File(stage, if (isJson) "nightjar.json" else entry.name.substringAfter('/'))
                    file.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count; total += count
                            require(entryBytes <= limit && total <= 256L * 1024 * 1024) { "Backup is too large." }
                            out.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val json = JSONObject(File(stage, "nightjar.json").readText())
            require(json.getString("format") == "nightjar" && json.getInt("version") == 1) { "Unsupported Nightjar backup version." }
            val sessions = json.getJSONArray("sessions")
            require(sessions.length() <= 10_000) { "Too many sessions in this backup." }
            val restoredSettings = AppSettings.fromJson(json.getJSONObject("settings")).copy(recordNoise = false, alarmToneUri = "")
            var imported = 0; var skipped = 0
            database.withTransaction {
                check(dao.activeSession() == null) { "Stop tracking before restoring." }
                val existing = dao.allSessions().map { Triple(it.startTime, it.endTime, it.mode) }.toMutableSet()
                for (i in 0 until sessions.length()) {
                    val row = sessions.getJSONObject(i)
                    val start = row.getLong("startTime"); val end = row.getLong("endTime")
                    require(start > 0 && end >= start && end - start <= 7L * 86_400_000) { "Invalid session timestamps." }
                    val mode = SensingMode.valueOf(row.getString("mode"))
                    val low = row.getDouble("lowThreshold").toFloat()
                    val high = row.getDouble("highThreshold").toFloat()
                    val config = AnalysisConfig(low, high)
                    val key = Triple(start, end as Long?, mode.name)
                    if (key in existing) { skipped++; continue }
                    val id = dao.insertSession(SleepSession(startTime = start, mode = mode.name,
                        note = row.optString("note").take(2000), lowThreshold = low, highThreshold = high))
                    val epochs = row.getJSONArray("epochs")
                    require(epochs.length() <= 25_000) { "Too many intervals in a session." }
                    var previousEnd = start
                    for (j in 0 until epochs.length()) {
                        val e = epochs.getJSONObject(j)
                        val time = e.getLong("startTime"); val duration = e.getLong("durationMs")
                        require(time >= previousEnd && duration > 0 && duration <= end - time && time <= end) { "Overlapping or invalid intervals." }
                        val intensity = e.getDouble("intensity").toFloat()
                        val score = e.getDouble("activityScore").toFloat()
                        require(intensity.isFinite() && intensity in 0f..1f && score.isFinite() && score in 0f..1f) { "Invalid intensity." }
                        val phase = SleepPhase.valueOf(e.getString("phase"))
                        val count = e.getInt("sampleCount")
                        require(count >= 0) { "Invalid sample count." }
                        dao.insertEpoch(Epoch(sessionId = id, startTime = time, durationMs = duration, intensity = intensity,
                            activityScore = score, phase = phase.name, sampleCount = count))
                        previousEnd = time + duration
                    }
                    val noise = row.getJSONArray("noise")
                    require(noise.length() <= 10_000) { "Too many noise events." }
                    for (j in 0 until noise.length()) {
                        val e = noise.getJSONObject(j)
                        val time = e.getLong("timestamp"); val duration = e.getLong("durationMs")
                        require(time in start..end && duration in 1..10_000) { "Invalid noise event." }
                        val type = e.getString("type")
                        require(type in setOf("POSSIBLE_SNORING", "NOISE_BURST")) { "Unknown noise type." }
                        var clip: String? = null
                        if (!e.isNull("clip")) {
                            val name = e.getString("clip")
                            require(name.matches(Regex("[A-Za-z0-9_-]{1,80}\\.wav"))) { "Invalid clip name." }
                            val source = File(stage, name)
                            require(source.isFile) { "A recording is missing from the backup." }
                            val destination = File(context.filesDir, "recordings/${UUID.randomUUID()}.wav")
                            destination.parentFile!!.mkdirs()
                            source.copyTo(destination)
                            moved += destination
                            clip = destination.name
                        }
                        dao.insertNoise(NoiseEvent(sessionId = id, timestamp = time, durationMs = duration, type = type, clipPath = clip))
                    }
                    val tags = row.getJSONArray("tags")
                    require(tags.length() <= 20) { "Too many tags." }
                    dao.insertTags((0 until tags.length()).map { tags.getString(it) }.distinct().map {
                        require(it.length in 1..50) { "Invalid tag." }; DailyTag(id, it)
                    })
                    repository.finish(id, end, row.optString("status") == "INTERRUPTED")
                    existing += key; imported++
                }
            }
            committed = true
            settings.restore(restoredSettings)
            RestoreResult(imported, skipped)
        } catch (e: Exception) {
            if (!committed) moved.forEach { it.delete() }
            throw e
        } finally { stage.deleteRecursively() }
    }
}
