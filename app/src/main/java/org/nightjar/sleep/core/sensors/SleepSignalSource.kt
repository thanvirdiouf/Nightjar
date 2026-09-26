package org.nightjar.sleep.core.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.media.*
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

interface SleepSignalSource {
    fun start()
    fun close()
}

class AccelerometerSource(
    context: Context,
    private val sample: (Float, Long) -> Unit
) : SleepSignalSource, SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var previous: FloatArray? = null
    override fun start() {
        check(sensor != null) { "This device has no accelerometer. Try microphone mode." }
        check(manager.registerListener(this, sensor, 200_000, 1_000_000)) { "The accelerometer could not be started." }
    }
    override fun onSensorChanged(event: SensorEvent) {
        val current = event.values.copyOf()
        previous?.let { prior ->
            val delta = sqrt((0..2).sumOf { ((current[it] - prior[it]).toDouble()).let { v -> v * v } })
            sample((delta * 0.12).toFloat().coerceIn(0f, 1f), SystemClock.elapsedRealtime())
        }
        previous = current
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun close() { manager.unregisterListener(this); previous = null }
}

class MicrophoneSource(
    private val context: Context,
    private val sample: (Float, Long) -> Unit,
    private val error: (String) -> Unit,
    private val pcm: ((ShortArray, Int) -> Unit)? = null
) : SleepSignalSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: AudioRecord? = null
    private var reader: Job? = null
    @Suppress("MissingPermission")
    override fun start() {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is needed for microphone tracking."
        }
        val rate = 16_000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "This device does not support microphone sampling." }
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 4096))
        if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); throw IllegalStateException("The microphone could not be initialized.") }
        recorder = audio
        audio.startRecording()
        check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Another app may be using the microphone." }
        reader = scope.launch {
            val buffer = ShortArray(1024)
            try {
                while (isActive) {
                    val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "The microphone stopped providing samples." }
                    var energy = 0.0
                    for (i in 0 until count) { val value = buffer[i] / 32768.0; energy += value * value }
                    val rms = sqrt(energy / count)
                    sample((rms * 16).toFloat().coerceIn(0f, 1f), SystemClock.elapsedRealtime())
                    pcm?.invoke(buffer.copyOf(count), rate)
                }
            } catch (e: Exception) {
                if (isActive) error(e.message ?: "Microphone sampling failed.")
            }
        }
    }
    override fun close() {
        reader?.cancel()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        scope.cancel()
    }
}

data class Aggregate(val intensity: Float, val samples: Int, val valid: Boolean)

class SignalAccumulator {
    private var sum = 0.0
    private var count = 0
    private var first = 0L
    private var last = 0L
    private var latest = 0f
    @Synchronized fun add(value: Float, elapsed: Long) {
        if (!value.isFinite()) return
        if (count == 0) first = elapsed
        sum += value.coerceIn(0f, 1f)
        count++
        last = elapsed
        latest = value.coerceIn(0f, 1f)
    }
    @Synchronized fun peek(): Pair<Float, Int> = latest to count
    @Synchronized fun take(elapsed: Long, duration: Long): Aggregate {
        val valid = count >= 3 && last - first >= minOf(1000, duration / 3) && elapsed - last <= 5_000
        val result = Aggregate(if (count > 0) (sum / count).toFloat() else 0f, count, valid)
        sum = 0.0; count = 0; first = 0; last = 0
        return result
    }
}
