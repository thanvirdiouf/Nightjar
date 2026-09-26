package org.nightjar.sleep.core.audio

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.nightjar.sleep.R
import org.nightjar.sleep.app
import org.nightjar.sleep.core.*

class SoundPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var playback: Job? = null
    private var focus: AudioFocusRequest? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        Notifications.channels(this)
        scope.launch {
            app.runtime.tracking.collectLatest {
                if (it.running && it.asleepEpochs >= 3 && app.settings.current.stopSoundsOnSleep) stopSound()
            }
        }
        scope.launch { app.runtime.alarm.collectLatest { if (it.ringing) stopSound() } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSound(); return START_NOT_STICKY }
        val name = intent?.getStringExtra("name")?.takeIf { it in SynthAudio.ambientSounds }
            ?: run { stopSelf(); return START_NOT_STICKY }
        playback?.cancel()
        release()
        val stop = PendingIntent.getService(this, 300, Intent(this, SoundPlaybackService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, Notifications.SOUNDS).setSmallIcon(R.drawable.ic_nightjar)
            .setContentTitle(name).setContentText("Nightjar wind-down sounds").setOngoing(true)
            .setContentIntent(Notifications.open(this, "sounds")).addAction(0, "Stop", stop).build()
        try {
            ServiceCompat.startForeground(this, 300, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (e: Exception) { failure(e); return START_NOT_STICKY }
        val minutes = app.settings.current.soundTimerMinutes
        val deadline = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else null
        val wallEnd = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else null
        playback = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { SynthAudio.file(this@SoundPlaybackService, name) }
                ensureActive()
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSound()
                    }.build()
                check(getSystemService(AudioManager::class.java).requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "Audio is in use by another app."
                }
                val media = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setWakeMode(this@SoundPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVolume(0.65f, 0.65f)
                }
                player = media
                media.setOnErrorListener { _, _, _ -> failure(IllegalStateException("Sound playback stopped.")); true }
                withContext(Dispatchers.IO) { media.prepare() }
                ensureActive()
                media.start()
                app.runtime.sound.value = SoundState(true, name, wallEnd)
                while (isActive) {
                    if (deadline != null && SystemClock.elapsedRealtime() >= deadline) { stopSound(); break }
                    delay(500)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
        return START_NOT_STICKY
    }
    private fun failure(e: Exception) {
        stopSound()
        app.runtime.sound.value = SoundState(error = e.message ?: "Sound playback failed.")
    }
    private fun release() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }
    private fun stopSound() {
        playback?.cancel()
        release()
        app.runtime.sound.value = SoundState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        release()
        app.runtime.sound.value = SoundState()
        super.onDestroy()
    }
    companion object {
        const val PLAY = "org.nightjar.sleep.PLAY_SOUND"
        const val STOP = "org.nightjar.sleep.STOP_SOUND"
    }
}
