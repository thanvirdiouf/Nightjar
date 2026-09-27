package org.nightjar.sleep.tracking

import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nightjar.sleep.R
import org.nightjar.sleep.app
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.analysis.*
import org.nightjar.sleep.core.sensors.*
import org.nightjar.sleep.core.audio.NoiseRecorder
import org.nightjar.sleep.core.storage.*
import kotlin.math.max

class TrackingForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val flushLock = Mutex()
    private val accumulator = SignalAccumulator()
    private var source: SleepSignalSource? = null
    private var noiseRecorder: NoiseRecorder? = null
    private var session: SleepSession? = null
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false
    private var initializing = false
    private var pendingStop = false
    private var epochStartElapsed = 0L
    private var epochStartWall = 0L
    private var lastWakeRefresh = 0L
    private var history = mutableListOf<Float>()
    private var classifier = SleepPhaseClassifier()
    private var epochCount = 0
    private var asleepRun = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); Notifications.channels(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            if (initializing) { pendingStop = true; return START_NOT_STICKY }
            finish(false)
            return START_NOT_STICKY
        }
        if (initializing || session != null) return START_NOT_STICKY
        initializing = true
        stopping = false
        val settings = app.settings.current
        try {
            foreground(settings.mode)
        } catch (e: Exception) {
            app.runtime.tracking.value = TrackingState(error = e.message ?: "Tracking could not start.")
            stopSelf()
            return START_NOT_STICKY
        }
        app.runtime.tracking.value = TrackingState(starting = true, mode = settings.mode)
        scope.launch {
            try {
                val active = app.repository.startOrResume(settings, System.currentTimeMillis())
                session = active
                val mode = SensingMode.valueOf(active.mode)
                foreground(mode)
                classifier = SleepPhaseClassifier(AnalysisConfig(active.lowThreshold, active.highThreshold))
                val previous = app.repository.dao.epochs(active.id)
                history = previous.asReversed().takeWhile { it.phase != SleepPhase.UNKNOWN.name }.take(4).asReversed().map { it.intensity }.toMutableList()
                epochCount = previous.size
                epochStartElapsed = SystemClock.elapsedRealtime()
                epochStartWall = max(System.currentTimeMillis(), previous.lastOrNull()?.let { it.startTime + it.durationMs } ?: active.startTime)
                if (pendingStop) { initializing = false; finish(false); return@launch }
                if (mode == SensingMode.MICROPHONE && settings.recordNoise) noiseRecorder = NoiseRecorder(this@TrackingForegroundService, app.repository.dao, active.id) { app.runtime.notice.value = it }
                source = if (mode == SensingMode.ACCELEROMETER) AccelerometerSource(this@TrackingForegroundService, accumulator::add)
                    else MicrophoneSource(this@TrackingForegroundService, accumulator::add, { message ->
                        scope.launch { app.runtime.notice.value = message; finish(true, message) }
                    }, noiseRecorder?.let { recorder -> { pcm, rate -> recorder.accept(pcm, rate) } })
                source!!.start()
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nightjar:Tracking").apply {
                    setReferenceCounted(false)
                    acquire(12 * 3_600_000L)
                }
                lastWakeRefresh = SystemClock.elapsedRealtime()
                app.runtime.tracking.value = TrackingState(running = true, sessionId = active.id, startTime = active.startTime, mode = mode, epochs = epochCount)
                initializing = false
                ticker = scope.launch {
                    while (isActive && !stopping) {
                        delay(1_000)
                        val elapsed = SystemClock.elapsedRealtime()
                        val peek = accumulator.peek()
                        app.runtime.tracking.value = app.runtime.tracking.value.copy(intensity = peek.first, samples = peek.second)
                        if (elapsed - epochStartElapsed >= EPOCH_MS) flushLock.withLock { if (!stopping) flushEpoch(elapsed) }
                        if (elapsed - lastWakeRefresh > 10 * 3_600_000L) {
                            wakeLock?.acquire(12 * 3_600_000L)
                            lastWakeRefresh = elapsed
                        }
                    }
                }
                runCatching { app.repository.pruneClips(app.settings.current.retentionDays) }
                    .onFailure { app.runtime.notice.value = "Some expired recordings could not be removed." }
            } catch (e: Exception) {
                initializing = false
                finish(true, e.message ?: "Tracking failed.")
            }
        }
        return START_NOT_STICKY
    }
    private fun foreground(mode: SensingMode) {
        val stop = android.app.PendingIntent.getService(this, 10, Intent(this, TrackingForegroundService::class.java).setAction(STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, Notifications.TRACKING)
            .setSmallIcon(R.drawable.ic_nightjar).setContentTitle("Nightjar is tracking your night")
            .setContentText(if (mode == SensingMode.MICROPHONE) "Microphone intensity • stored locally" else "Motion intensity • stored locally")
            .setContentIntent(Notifications.open(this, "tonight")).setOngoing(true)
            .addAction(0, "End session", stop).build()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            if (mode == SensingMode.MICROPHONE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= 30 && mode == SensingMode.MICROPHONE) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, 100, notification, type)
    }
    private suspend fun flushEpoch(elapsed: Long) {
        val active = session ?: return
        if (epochStartElapsed == 0L) return
        val duration = elapsed - epochStartElapsed
        if (duration < 1_000) return
        val aggregate = accumulator.take(elapsed, duration)
        val score = if (aggregate.valid) EpochScorer.score(aggregate.intensity, history) else 0f
        val phase = classifier.classify(score, aggregate.valid)
        app.repository.dao.insertEpoch(Epoch(sessionId = active.id, startTime = epochStartWall, durationMs = duration,
            intensity = aggregate.intensity, activityScore = score, phase = phase.name, sampleCount = aggregate.samples))
        if (aggregate.valid) { history.add(aggregate.intensity); history = history.takeLast(4).toMutableList() } else history.clear()
        epochCount++
        asleepRun = if (phase == SleepPhase.LIGHT || phase == SleepPhase.DEEP) asleepRun + 1 else 0
        app.runtime.tracking.value = app.runtime.tracking.value.copy(phase = phase, epochs = epochCount, asleepEpochs = asleepRun)
        if (!stopping) app.alarms.evaluateEarly(phase, if (aggregate.valid) 0 else Long.MAX_VALUE)
        epochStartElapsed = elapsed
        epochStartWall += duration
    }
    private fun finish(interrupted: Boolean, error: String? = null) {
        if (stopping) return
        stopping = true
        scope.launch {
            try {
                flushLock.withLock {
                    ticker?.cancel()
                    source?.close()
                    source = null
                    noiseRecorder?.finish()
                    noiseRecorder = null
                    flushEpoch(SystemClock.elapsedRealtime())
                    session?.let { app.repository.finish(it.id, System.currentTimeMillis(), interrupted) }
                }
                if (session != null) app.runtime.destination.value = "journal"
            } catch (e: Exception) {
                app.runtime.notice.value = e.message ?: "The session could not be saved."
            } finally {
                app.runtime.tracking.value = TrackingState(error = error)
                session = null
                if (wakeLock?.isHeld == true) wakeLock?.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    override fun onDestroy() {
        source?.close()
        noiseRecorder?.close()
        ticker?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        scope.cancel()
        if (app.runtime.tracking.value.running || app.runtime.tracking.value.starting) app.runtime.tracking.value =
            TrackingState(error = "Tracking was interrupted. Resume or finish the saved session.")
        super.onDestroy()
    }
    companion object {
        const val START = "org.nightjar.sleep.START_TRACKING"
        const val STOP = "org.nightjar.sleep.STOP_TRACKING"
        const val EPOCH_MS = 30_000L
    }
}
