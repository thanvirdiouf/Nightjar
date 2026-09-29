// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core.analysis

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class NoiseDetectorTest {
    private fun feed(detector: NoiseDetector, audio: ShortArray): List<DetectedNoise> =
        audio.toList().chunked(1024).mapNotNull { detector.accept(it.toShortArray()) }
    @Test fun silenceDoesNotCreateEvents() {
        assertTrue(feed(NoiseDetector(), ShortArray(16000 * 10)).isEmpty())
    }
    @Test fun sustainedPeriodicLowToneIsOnlyACandidate() {
        val detector = NoiseDetector()
        val tone = ShortArray(16000 * 2) { (sin(2 * PI * 100 * it / 16000) * 8000).toInt().toShort() }
        val result = feed(detector, tone + ShortArray(16000))
        assertEquals(1, result.size)
        assertEquals("POSSIBLE_SNORING", result.single().type)
        assertTrue(result.single().durationMs in 2000..3000)
    }
    @Test fun briefBroadbandBurstIsNoise() {
        val random = Random(17)
        val audio = ShortArray(8000) { random.nextInt(-8000, 8000).toShort() }
        val events = feed(NoiseDetector(), audio + ShortArray(16000))
        assertEquals("NOISE_BURST", events.single().type)
    }
    @Test fun tinyClickIsIgnoredAndLongClipIsBounded() {
        assertTrue(feed(NoiseDetector(), ShortArray(500) { 9000 } + ShortArray(16000)).isEmpty())
        val events = feed(NoiseDetector(), ShortArray(16000 * 7) { (sin(2 * PI * 120 * it / 16000) * 10000).toInt().toShort() })
        assertEquals(1, events.size)
        assertEquals(96000, events.single().pcm.size)
    }
}
