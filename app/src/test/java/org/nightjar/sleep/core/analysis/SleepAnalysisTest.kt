package org.nightjar.sleep.core.analysis

import org.junit.Assert.*
import org.junit.Test
import org.nightjar.sleep.core.storage.Epoch

class SleepAnalysisTest {
    private fun epochs(phases: List<SleepPhase>) = phases.mapIndexed { index, phase ->
        Epoch(sessionId = 1, startTime = index * 30_000L, durationMs = 30_000, intensity = 0f,
            activityScore = 0f, phase = phase.name, sampleCount = if (phase == SleepPhase.UNKNOWN) 0 else 150)
    }
    @Test fun missingDataNeverBecomesDeepSleep() {
        val classifier = SleepPhaseClassifier()
        repeat(50) { assertEquals(SleepPhase.UNKNOWN, classifier.classify(0f, false)) }
        assertEquals(SleepPhase.LIGHT, classifier.classify(0f))
    }
    @Test fun deepEstimateNeedsSustainedQuiet() {
        val classifier = SleepPhaseClassifier()
        repeat(4) { assertEquals(SleepPhase.LIGHT, classifier.classify(0f)) }
        assertEquals(SleepPhase.DEEP, classifier.classify(0f))
        assertEquals(SleepPhase.AWAKE, classifier.classify(1f))
        assertEquals(SleepPhase.LIGHT, classifier.classify(0f))
    }
    @Test fun thresholdBoundariesAndNoisyScores() {
        val classifier = SleepPhaseClassifier()
        assertEquals(SleepPhase.AWAKE, classifier.classify(0.35f))
        assertEquals(SleepPhase.LIGHT, classifier.classify(0.2f))
        assertEquals(SleepPhase.UNKNOWN, classifier.classify(Float.NaN))
    }
    @Test fun scorerUsesOnlyPastSamplesAndIsBounded() {
        assertEquals(0f, EpochScorer.score(0f, List(4) { 0f }), 0.0001f)
        assertTrue(EpochScorer.score(1f, List(4) { 0f }) > 0.4f)
        assertEquals(EpochScorer.score(0.2f, listOf(0.3f, 0.5f)), EpochScorer.score(0.2f, listOf(0.3f, 0.5f)))
        assertTrue(EpochScorer.score(4f, emptyList()) in 0f..1f)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidThresholdsRejected() { AnalysisConfig(0.4f, 0.3f) }
    @Test fun shortSessionHasNoQualityScore() {
        val metrics = SleepQualityCalculator.calculate(0, 60_000, epochs(List(2) { SleepPhase.LIGHT }))
        assertNull(metrics.score)
        assertNull(metrics.latencyMs)
    }
    @Test fun sleepLatencyAndAwakeningsUseSustainedRuns() {
        val phases = List(4) { SleepPhase.AWAKE } + List(10) { SleepPhase.LIGHT } +
            List(2) { SleepPhase.AWAKE } + List(10) { SleepPhase.DEEP }
        val metrics = SleepQualityCalculator.calculate(0, phases.size * 30_000L, epochs(phases))
        assertEquals(120_000L, metrics.latencyMs)
        assertEquals(600_000L, metrics.sleepMs)
        assertEquals(180_000L, metrics.awakeMs)
        assertEquals(1, metrics.awakenings)
        assertEquals(50f, metrics.deepPercent, 0.01f)
    }
    @Test fun missingEpochsReduceCoverageAndDoNotAddSleep() {
        val phases = List(4) { SleepPhase.LIGHT } + List(20) { SleepPhase.UNKNOWN }
        val metrics = SleepQualityCalculator.calculate(0, phases.size * 30_000L, epochs(phases))
        assertEquals(120_000L, metrics.sleepMs)
        assertEquals(600_000L, metrics.unknownMs)
        assertNull(metrics.score)
    }
    @Test fun wholeNightRestIsNotAutomaticallyPerfectWhenShort() {
        val phases = List(960) { SleepPhase.DEEP }
        val metrics = SleepQualityCalculator.calculate(0, 8 * 3_600_000L, epochs(phases))
        assertEquals(100, metrics.score)
        assertEquals(100f, metrics.deepPercent, 0.01f)
        assertEquals(0, metrics.awakenings)
    }
}
