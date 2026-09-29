// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Nightjar contributors

package org.nightjar.sleep.core

import android.app.*
import android.content.Context
import android.content.Intent
import org.nightjar.sleep.MainActivity

object Notifications {
    const val TRACKING = "tracking"
    const val ALARM = "alarm"
    const val SOUNDS = "sounds"
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(TRACKING, "Overnight tracking", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(ALARM, "Wake-up alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Scheduled wake-up alarms with gentle volume ramp"
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        manager.createNotificationChannel(NotificationChannel(SOUNDS, "Sleep sounds", NotificationManager.IMPORTANCE_LOW))
    }
    fun open(context: Context, page: String, code: Int = 0): PendingIntent =
        PendingIntent.getActivity(context, code, Intent(context, MainActivity::class.java)
            .putExtra("screen", page).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
