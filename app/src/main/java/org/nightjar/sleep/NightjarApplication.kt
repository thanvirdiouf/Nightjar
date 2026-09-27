package org.nightjar.sleep

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch
import org.nightjar.sleep.core.AppContainer

class NightjarApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        container.alarms.reconcile()
        container.scope.launch {
            runCatching { container.repository.pruneClips(container.settings.current.retentionDays) }
                .onFailure { container.runtime.notice.value = "Expired recordings could not be removed." }
        }
    }
}
val Context.app: AppContainer get() = (applicationContext as NightjarApplication).container
