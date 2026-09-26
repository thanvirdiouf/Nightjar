package org.nightjar.sleep.core.alarm

import java.time.*
import org.nightjar.sleep.core.analysis.SleepPhase

data class AlarmPlan(val token: String, val targetTime: Long, val windowMinutes: Int, val enabled: Boolean = true)

object AlarmPolicy {
    fun nextTarget(now: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        require(hour in 0..23 && minute in 0..59)
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var date = current.toLocalDate()
        var target = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (target <= now) {
            date = date.plusDays(1)
            target = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        }
        return target
    }
    fun mayWakeEarly(plan: AlarmPlan, now: Long, phase: SleepPhase, sampleAgeMs: Long): Boolean =
        plan.enabled && plan.windowMinutes > 0 &&
            now >= plan.targetTime - plan.windowMinutes * 60_000L && now < plan.targetTime &&
            sampleAgeMs in 0..90_000 &&
            (phase == SleepPhase.LIGHT || phase == SleepPhase.AWAKE)
}
