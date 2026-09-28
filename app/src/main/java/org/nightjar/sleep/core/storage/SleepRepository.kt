package org.nightjar.sleep.core.storage

import android.content.Context
import androidx.room.withTransaction
import org.nightjar.sleep.core.AppSettings
import org.nightjar.sleep.core.analysis.*
import java.io.File

class SleepRepository(private val context: Context, private val database: SleepDatabase) {
    val dao = database.dao()
    val sessions = dao.observeSessions()
    suspend fun startOrResume(settings: AppSettings, now: Long): SleepSession = database.withTransaction {
        val current = dao.activeSession()
        if (current != null) {
            val lastEnd = dao.epochs(current.id).lastOrNull()?.let { it.startTime + it.durationMs } ?: current.startTime
            if (now - lastEnd > 1_000) dao.insertEpoch(Epoch(sessionId = current.id, startTime = lastEnd,
                durationMs = now - lastEnd, intensity = 0f, activityScore = 0f, phase = SleepPhase.UNKNOWN.name, sampleCount = 0))
            current
        } else {
            val session = SleepSession(startTime = now, mode = settings.mode.name,
                lowThreshold = settings.lowThreshold, highThreshold = settings.highThreshold)
            val id = dao.insertSession(session)
            session.copy(id = id)
        }
    }
    suspend fun finish(id: Long, now: Long, interrupted: Boolean = false): SleepSession? = database.withTransaction {
        val session = dao.session(id) ?: return@withTransaction null
        if (session.endTime != null) return@withTransaction session
        val epochs = dao.epochs(id)
        val end = maxOf(now, epochs.lastOrNull()?.let { it.startTime + it.durationMs } ?: session.startTime)
        val metrics = SleepQualityCalculator.calculate(session.startTime, end, epochs,
            AnalysisConfig(session.lowThreshold, session.highThreshold))
        session.copy(endTime = end, score = metrics.score, latencyMs = metrics.latencyMs,
            sleepMs = metrics.sleepMs, awakeMs = metrics.awakeMs, deepPercent = metrics.deepPercent,
            awakenings = metrics.awakenings, status = if (interrupted) "INTERRUPTED" else "COMPLETE")
            .also { dao.updateSession(it) }
    }
    suspend fun saveJournal(id: Long, note: String, tags: Set<String>) = database.withTransaction {
        dao.session(id)?.let { dao.updateSession(it.copy(note = note.take(2000))) }
        dao.clearTags(id)
        dao.insertTags(tags.filter { it.length in 1..50 }.take(20).map { DailyTag(id, it) })
    }
    fun clipFile(path: String?): File? {
        if (path == null) return null
        val root = File(context.filesDir, "recordings").canonicalFile
        val candidate = File(root, path).canonicalFile
        return candidate.takeIf { it.parentFile == root && it.isFile }
    }
    suspend fun pruneClips(retentionDays: Int, now: Long = System.currentTimeMillis()) {
        dao.expiredClips(now - retentionDays * 86_400_000L).forEach {
            val file = clipFile(it.clipPath)
            check(file == null || file.delete()) { "An expired recording could not be deleted." }
            dao.updateNoise(it.copy(clipPath = null))
        }
        // A process interruption between writing a clip and its DB row can leave an orphan.
        val referenced = dao.allNoise().mapNotNull { it.clipPath }.toSet()
        File(context.filesDir, "recordings").listFiles()?.forEach { file ->
            if (file.isFile && file.name !in referenced && file.lastModified() < now - retentionDays * 86_400_000L) {
                check(file.delete()) { "An expired unreferenced recording could not be removed." }
            }
        }
    }
    suspend fun deleteSession(id: Long) = database.withTransaction {
        val session = dao.session(id) ?: return@withTransaction
        check(session.endTime != null) { "Stop the active session before deleting it." }
        dao.noise(id).forEach {
            val file = clipFile(it.clipPath)
            check(file == null || file.delete()) { "A recording could not be deleted. Please try again." }
        }
        dao.deleteSession(session)
    }
}
