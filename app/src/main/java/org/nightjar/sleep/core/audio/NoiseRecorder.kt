package org.nightjar.sleep.core.audio

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.nightjar.sleep.core.analysis.*
import org.nightjar.sleep.core.storage.*
import java.io.File
import java.util.UUID

/** Bounded queue: a slow disk never retains an unbounded amount of raw audio. */
class NoiseRecorder(private val context: Context, private val dao: SleepDao, private val sessionId: Long,
    private val error: (String) -> Unit) {
    private val detector = NoiseDetector()
    private val started = System.currentTimeMillis()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<DetectedNoise>(2)
    private var accepted = 0
    private val writer = scope.launch {
        for (event in events) {
            val file = File(context.filesDir, "recordings/${UUID.randomUUID()}.wav")
            try {
                WavFiles.write(file, event.pcm)
                dao.insertNoise(NoiseEvent(sessionId = sessionId, timestamp = started + event.startOffsetMs,
                    durationMs = event.durationMs, type = event.type, clipPath = file.name))
            } catch (e: Exception) { file.delete(); error("A noise clip could not be saved: ${e.message}") }
        }
    }
    fun accept(pcm: ShortArray, rate: Int) {
        if (rate != 16_000 || accepted >= 300) return
        detector.accept(pcm)?.let { if (events.trySend(it).isSuccess) accepted++ }
    }
    suspend fun finish() {
        detector.finish()?.let { if (accepted < 300) events.send(it) }
        events.close()
        writer.join()
        scope.cancel()
    }
    fun close() { events.close(); scope.cancel() }
}
