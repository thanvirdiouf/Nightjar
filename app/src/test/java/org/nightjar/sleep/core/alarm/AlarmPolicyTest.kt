// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core.alarm

import org.junit.Assert.*
import org.junit.Test
import org.nightjar.sleep.core.analysis.SleepPhase
import java.time.*

class AlarmPolicyTest {
    private val plan = AlarmPlan("test", 1_800_000, 20)
    @Test fun neverFiresOutsideWindow() {
        assertFalse(AlarmPolicy.mayWakeEarly(plan, 599_999, SleepPhase.LIGHT, 0))
        assertTrue(AlarmPolicy.mayWakeEarly(plan, 600_000, SleepPhase.LIGHT, 0))
        assertFalse(AlarmPolicy.mayWakeEarly(plan, 1_800_000, SleepPhase.LIGHT, 0))
    }
    @Test fun unknownDeepOrStaleSignalCannotWakeEarly() {
        assertFalse(AlarmPolicy.mayWakeEarly(plan, 1_000_000, SleepPhase.UNKNOWN, 0))
        assertFalse(AlarmPolicy.mayWakeEarly(plan, 1_000_000, SleepPhase.DEEP, 0))
        assertFalse(AlarmPolicy.mayWakeEarly(plan, 1_000_000, SleepPhase.LIGHT, 90_001))
        assertFalse(AlarmPolicy.mayWakeEarly(plan.copy(enabled = false), 1_000_000, SleepPhase.AWAKE, 0))
        assertFalse(AlarmPolicy.mayWakeEarly(plan.copy(windowMinutes = 0), 1_000_000, SleepPhase.AWAKE, 0))
    }
    @Test fun passedWakeTimeSchedulesNextDay() {
        val now = Instant.parse("2026-09-26T08:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-09-27T07:00:00Z").toEpochMilli(), AlarmPolicy.nextTarget(now, 7, 0, ZoneOffset.UTC))
    }
    @Test fun nonexistentDstWakeTimeMovesForwardWithoutCrashing() {
        val now = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        val target = Instant.ofEpochMilli(AlarmPolicy.nextTarget(now, 2, 30, ZoneId.of("America/New_York"))).atZone(ZoneId.of("America/New_York"))
        assertEquals(3, target.hour)
        assertEquals(30, target.minute)
    }
}
