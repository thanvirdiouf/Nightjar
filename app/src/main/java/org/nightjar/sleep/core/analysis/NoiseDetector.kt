// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core.analysis

import kotlin.math.*

data class DetectedNoise(val startOffsetMs: Long, val durationMs: Long, val type: String, val pcm: ShortArray)

/** An inexpensive candidate detector, not a speech or medical snoring classifier. */
class NoiseDetector(private val sampleRate: Int = 16_000) {
    private var processed = 0L
    private var eventStart = -1L
    private var quietSamples = 0
    private var loudSamples = 0
    private var periodicSamples = 0
    private var cooldownUntil = 0L
    private val chunks = mutableListOf<ShortArray>()
    private var recordedSamples = 0

    @Synchronized fun accept(pcm: ShortArray): DetectedNoise? {
        if (pcm.isEmpty()) return null
        val offset = processed
        processed += pcm.size
        if (offset < cooldownUntil) return null
        val rms = sqrt(pcm.sumOf { val v = it / 32768.0; v * v } / pcm.size)
        val elevated = rms >= .018
        if (eventStart < 0 && elevated) eventStart = offset
        if (eventStart < 0) return null
        val room = sampleRate * 6 - recordedSamples
        if (room > 0) { val part = pcm.copyOf(minOf(room, pcm.size)); chunks += part; recordedSamples += part.size }
        if (elevated) {
            loudSamples += pcm.size
            quietSamples = 0
            if (lowPeriodic(pcm)) periodicSamples += pcm.size
        } else quietSamples += pcm.size
        return if (quietSamples >= sampleRate / 3 || recordedSamples >= sampleRate * 6) finish() else null
    }
    @Synchronized fun finish(): DetectedNoise? {
        if (eventStart < 0) return null
        val result = if (loudSamples >= sampleRate / 5) {
            val audio = ShortArray(recordedSamples)
            var position = 0
            chunks.forEach { it.copyInto(audio, position); position += it.size }
            DetectedNoise(eventStart * 1000 / sampleRate, recordedSamples * 1000L / sampleRate,
                if (loudSamples >= sampleRate && periodicSamples.toDouble() / loudSamples >= .45) "POSSIBLE_SNORING" else "NOISE_BURST",
                audio)
        } else null
        eventStart = -1L; quietSamples = 0; loudSamples = 0; periodicSamples = 0; recordedSamples = 0; chunks.clear()
        if (result != null) cooldownUntil = processed + sampleRate * 5L
        return result
    }
    private fun lowPeriodic(pcm: ShortArray): Boolean {
        // Box-filter/downsample to 1 kHz, then compare plausible 50–250 Hz periods.
        val step = (sampleRate / 1000).coerceAtLeast(1)
        val n = pcm.size / step
        if (n < 40) return false
        val values = DoubleArray(n) { index -> (0 until step).sumOf { pcm[index * step + it].toDouble() } / step }
        val mean = values.average()
        for (i in values.indices) values[i] -= mean
        val rawEnergy = pcm.sumOf { it.toDouble() * it }
        val lowEnergy = values.sumOf { it * it } * step
        if (rawEnergy < 1 || lowEnergy / rawEnergy < .35) return false
        return (4..20).any { lag ->
            var xy = 0.0; var xx = 0.0; var yy = 0.0
            for (i in lag until n) { xy += values[i] * values[i-lag]; xx += values[i] * values[i]; yy += values[i-lag] * values[i-lag] }
            xx > 0 && yy > 0 && xy / sqrt(xx * yy) > .7
        }
    }
}
