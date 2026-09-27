package org.nightjar.sleep.core.alarm

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import org.nightjar.sleep.R
import org.nightjar.sleep.app
import org.nightjar.sleep.core.*
import org.nightjar.sleep.core.audio.SynthAudio
import org.nightjar.sleep.tracking.TrackingForegroundService

class AlarmPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var activeToken: String? = null
    private var focus: AudioFocusRequest? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); Notifications.channels(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopPlayback(); return START_NOT_STICKY }
            SNOOZE -> {
                stopPlayback()
                runCatching { app.alarms.scheduleAt(System.currentTimeMillis() + app.settings.current.snoozeMinutes * 60_000L, 0) }
                    .onFailure { app.runtime.notice.value = it.message }
                return START_NOT_STICKY
            }
        }
        val token = intent?.getStringExtra("token") ?: run { stopSelf(); return START_NOT_STICKY }
        if (token == activeToken) return START_NOT_STICKY
        val plan = app.alarms.plan.value
        if (plan?.token != token || !plan.enabled) { stopSelf(); return START_NOT_STICKY }
        activeToken = token
        val target = intent.getLongExtra("target", plan.targetTime)
        app.runtime.alarm.value = AlarmState(ringing = true, targetTime = target)
        val stop = PendingIntent.getService(this, 201, Intent(this, AlarmPlaybackService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getService(this, 202, Intent(this, AlarmPlaybackService::class.java).setAction(SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = Notifications.open(this, "alarm", 200)
        val builder = NotificationCompat.Builder(this, Notifications.ALARM).setSmallIcon(R.drawable.ic_nightjar)
            .setContentTitle("Good morning").setContentText("Your Nightjar wake-up alarm is ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setContentIntent(open)
            .addAction(0, "Dismiss", stop).addAction(0, "Snooze", snooze)
        if (Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            builder.setFullScreenIntent(open, true)
        }
        try {
            ServiceCompat.startForeground(this, 200, builder.build(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (e: Exception) {
            fail(token, e.message ?: "The alarm notification could not start.")
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                val settings = app.settings.current
                val file = if (settings.alarmToneUri.isBlank()) withContext(Dispatchers.IO) { SynthAudio.file(this@AlarmPlaybackService, settings.alarmTone) } else null
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                val audio = getSystemService(AudioManager::class.java)
                focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS) stopPlayback()
                    }.build()
                audio.requestAudioFocus(focus!!)
                val media = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setWakeMode(this@AlarmPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                    isLooping = true
                    setVolume(0.02f, 0.02f)
                }
                player = media
                try {
                    if (file != null) media.setDataSource(file.absolutePath) else media.setDataSource(this@AlarmPlaybackService, Uri.parse(settings.alarmToneUri))
                    withContext(Dispatchers.IO) { media.prepare() }
                } catch (e: Exception) {
                    if (e is CancellationException || file != null) throw e
                    media.reset()
                    media.setAudioAttributes(attributes)
                    media.setWakeMode(this@AlarmPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                    media.isLooping = true
                    media.setVolume(0.02f, 0.02f)
                    val fallback = withContext(Dispatchers.IO) { SynthAudio.file(this@AlarmPlaybackService, "Dawn") }
                    media.setDataSource(fallback.absolutePath)
                    withContext(Dispatchers.IO) { media.prepare() }
                    app.runtime.notice.value = "The selected audio file was unavailable. Using Dawn for this alarm."
                }
                media.setOnErrorListener { _, _, _ -> fail(token, "Alarm playback stopped unexpectedly."); true }
                media.start()
                if (!app.alarms.confirmPlaybackStarted(token)) { stopPlayback(); return@launch }
                app.runtime.alarm.value = app.runtime.alarm.value.copy(playing = media.isPlaying)
                if (app.runtime.tracking.value.running) startService(Intent(this@AlarmPlaybackService, TrackingForegroundService::class.java).setAction(TrackingForegroundService.STOP))
                val started = SystemClock.elapsedRealtime()
                while (isActive && player === media) {
                    val progress = ((SystemClock.elapsedRealtime() - started).toFloat() / (settings.rampSeconds * 1000)).coerceIn(0f, 1f)
                    val volume = 0.02f + (settings.alarmVolume - 0.02f) * progress
                    media.setVolume(volume, volume)
                    delay(250)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(token, e.message ?: "Alarm playback failed.")
            }
        }
        return START_NOT_STICKY
    }
    private fun fail(token: String, message: String) {
        app.alarms.playbackFailed(token)
        app.runtime.notice.value = message
        app.runtime.alarm.value = AlarmState(error = message)
        stopPlayback(preserveError = true)
    }
    private fun stopPlayback(preserveError: Boolean = false) {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        if (!preserveError) app.runtime.alarm.value = AlarmState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        scope.cancel()
        runCatching { player?.release() }
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        if (app.runtime.alarm.value.ringing) app.runtime.alarm.value = AlarmState()
        super.onDestroy()
    }
    companion object {
        const val PLAY = "org.nightjar.sleep.PLAY_ALARM"
        const val STOP = "org.nightjar.sleep.STOP_ALARM"
        const val SNOOZE = "org.nightjar.sleep.SNOOZE_ALARM"
    }
}
