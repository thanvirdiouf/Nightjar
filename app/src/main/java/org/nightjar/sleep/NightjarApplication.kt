package org.nightjar.sleep

import android.app.Application
import android.content.Context
import org.nightjar.sleep.core.AppContainer

class NightjarApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.alarms.reconcile()
    }
}
val Context.app: AppContainer get() = (applicationContext as NightjarApplication).container
