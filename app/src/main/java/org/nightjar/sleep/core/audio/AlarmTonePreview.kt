// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Short, foreground-only auditions; never schedules or starts an alarm. Call on the main thread. */
class AlarmTonePreview(context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit) : DefaultLifecycleObserver {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val mutableTone = MutableStateFlow<String?>(null)
    val playingTone = mutableTone.asStateFlow()
    internal val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(tone: String, volume: Float) {
        stop()
        job = scope.launch {
            var media: MediaPlayer? = null
            try {
                val file = withContext(Dispatchers.IO) { SynthAudio.file(context, tone) }
                ensureActive()
                val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                val sound = MediaPlayer()
                media = sound
                player = sound
                sound.setAudioAttributes(attributes)
                sound.setDataSource(file.absolutePath)
                val level = volume.takeIf { it.isFinite() }?.coerceIn(.1f, 1f) ?: .8f
                sound.setVolume(level, level)
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                        if (change < 0 && player === sound) stop()
                    }.build()
                focus = request
                check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    "Audio is in use by another app."
                }
                withTimeout(10_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        sound.setOnPreparedListener { if (continuation.isActive) continuation.resume(Unit) }
                        sound.setOnErrorListener { _, _, _ ->
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("The tone could not be previewed."))
                            true
                        }
                        sound.prepareAsync()
                    }
                }
                ensureActive()
                sound.setOnErrorListener { _, _, _ ->
                    if (player === sound) { stop(); error("Tone preview stopped unexpectedly.") }
                    true
                }
                sound.start()
                mutableTone.value = tone
                delay(5_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e.message ?: "The tone could not be previewed.")
            } finally {
                // An older cancelled request must not release a newer preview.
                if (media != null && player === media) release()
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) = stop()

    fun stop() {
        job?.cancel()
        job = null
        release()
    }

    private fun release() {
        player?.release()
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        mutableTone.value = null
    }
}
