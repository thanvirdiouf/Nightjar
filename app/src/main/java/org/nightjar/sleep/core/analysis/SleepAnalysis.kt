package org.nightjar.sleep.core.analysis

import org.nightjar.sleep.core.storage.Epoch
import kotlin.math.*

enum class SensingMode { ACCELEROMETER, MICROPHONE }
enum class SleepPhase { AWAKE, LIGHT, DEEP, UNKNOWN }

data class AnalysisConfig(
    val lowThreshold: Float = 0.08f,
    val highThreshold: Float = 0.35f,
    val deepRunEpochs: Int = 5,
    val onsetRunEpochs: Int = 3
) {
    init {
        require(lowThreshold.isFinite() && highThreshold.isFinite())
        require(lowThreshold >= 0 && highThreshold > lowThreshold && highThreshold <= 1)
        require(deepRunEpochs > 0 && onsetRunEpochs > 0)
    }
}

/** Original intensity heuristic. It is not a clinical sleep-staging model. */
object EpochScorer {
    fun score(current: Float, past: List<Float>): Float {
        require(current.isFinite() && past.all { it.isFinite() })
        val values = (past.takeLast(4) + current.coerceIn(0f, 1f))
        val mean = values.average()
        val deviation = sqrt(values.sumOf { (it - mean).pow(2) } / values.size)
        return (0.6 * mean + 0.3 * current.coerceIn(0f, 1f) + 0.1 * deviation).toFloat().coerceIn(0f, 1f)
    }
}

class SleepPhaseClassifier(private val config: AnalysisConfig = AnalysisConfig()) {
    private var quietRun = 0
    fun classify(score: Float, valid: Boolean = true): SleepPhase {
        if (!valid || !score.isFinite()) { quietRun = 0; return SleepPhase.UNKNOWN }
        return when {
            score >= config.highThreshold -> { quietRun = 0; SleepPhase.AWAKE }
            score <= config.lowThreshold -> {
                quietRun++
                if (quietRun >= config.deepRunEpochs) SleepPhase.DEEP else SleepPhase.LIGHT
            }
            else -> { quietRun = 0; SleepPhase.LIGHT }
        }
    }
}

data class SleepMetrics(
    val score: Int?,
    val latencyMs: Long?,
    val sleepMs: Long,
    val awakeMs: Long,
    val unknownMs: Long,
    val deepPercent: Float,
    val awakenings: Int,
    val coverage: Float
)

object SleepQualityCalculator {
    fun calculate(start: Long, end: Long, epochs: List<Epoch>, config: AnalysisConfig = AnalysisConfig()): SleepMetrics {
        val inBed = (end - start).coerceAtLeast(0)
        if (inBed == 0L) return SleepMetrics(null, null, 0, 0, 0, 0f, 0, 0f)
        val ordered = mutableListOf<Epoch>()
        var cursor = start
        fun missing(until: Long) {
            if (until > cursor) ordered += Epoch(sessionId = 0, startTime = cursor, durationMs = until - cursor,
                intensity = 0f, activityScore = 0f, phase = SleepPhase.UNKNOWN.name, sampleCount = 0)
        }
        for (epoch in epochs.sortedBy { it.startTime }) {
            if (epoch.durationMs <= 0) continue
            val from = maxOf(start, cursor, epoch.startTime)
            val until = minOf(end, epoch.startTime + epoch.durationMs)
            if (until <= from) continue
            missing(from)
            val phase = epoch.phase.takeIf { name -> SleepPhase.entries.any { it.name == name } } ?: SleepPhase.UNKNOWN.name
            ordered += epoch.copy(startTime = from, durationMs = until - from, phase = phase)
            cursor = until
        }
        missing(end)
        fun duration(e: Epoch) = (min(e.startTime + e.durationMs, end) - max(e.startTime, start)).coerceAtLeast(0)
        val validMs = ordered.filter { it.phase != SleepPhase.UNKNOWN.name }.sumOf(::duration).coerceAtMost(inBed)
        val coverage = validMs.toFloat() / inBed
        var onsetIndex = -1
        var asleepRun = 0
        ordered.forEachIndexed { index, epoch ->
            val contiguous = index == 0 || epoch.startTime <= ordered[index - 1].startTime + ordered[index - 1].durationMs + 1
            if (!contiguous) asleepRun = 0
            if (epoch.phase == SleepPhase.LIGHT.name || epoch.phase == SleepPhase.DEEP.name) {
                asleepRun++
                if (onsetIndex < 0 && asleepRun >= config.onsetRunEpochs) onsetIndex = index - config.onsetRunEpochs + 1
            } else asleepRun = 0
        }
        val afterOnset = if (onsetIndex >= 0) ordered.drop(onsetIndex) else emptyList()
        val sleepMs = afterOnset.filter { it.phase == SleepPhase.LIGHT.name || it.phase == SleepPhase.DEEP.name }.sumOf(::duration)
        val deepMs = afterOnset.filter { it.phase == SleepPhase.DEEP.name }.sumOf(::duration)
        val awakeMs = (validMs - sleepMs).coerceAtLeast(0)
        var awakenings = 0
        var awakeRunMs = 0L
        var hasSlept = false
        for (epoch in afterOnset) {
            when (epoch.phase) {
                SleepPhase.LIGHT.name, SleepPhase.DEEP.name -> {
                    hasSlept = true
                    awakeRunMs = 0
                }
                SleepPhase.AWAKE.name -> if (hasSlept) {
                    val previous = awakeRunMs
                    awakeRunMs += duration(epoch)
                    if (previous < 60_000 && awakeRunMs >= 60_000) awakenings++
                }
                else -> { awakeRunMs = 0; hasSlept = false }
            }
        }
        // 60% sleep efficiency, 25% progress toward 7.5 hours, 15% continuity.
        // Unknown time stays in the efficiency denominator and never becomes sleep.
        val efficiency = sleepMs.toDouble() / inBed
        val durationProgress = (sleepMs / 27_000_000.0).coerceIn(0.0, 1.0)
        val continuity = (1.0 - awakenings / 12.0).coerceIn(0.0, 1.0)
        val score = if (inBed >= 180_000 && coverage >= 0.5f) {
            (60 * efficiency + 25 * durationProgress + 15 * continuity).roundToInt().coerceIn(0, 100)
        } else null
        return SleepMetrics(score, onsetIndex.takeIf { it >= 0 }?.let { (ordered[it].startTime - start).coerceAtLeast(0) },
            sleepMs, awakeMs, (inBed - validMs).coerceAtLeast(0), if (sleepMs > 0) deepMs * 100f / sleepMs else 0f, awakenings, coverage)
    }
}
