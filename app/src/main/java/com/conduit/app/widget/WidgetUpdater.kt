package com.conduit.app.widget

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object WidgetUpdater {
    // Bursts of calls (notification ingestion/removal) within this window collapse into one refresh.
    private const val COALESCE_WINDOW_MS = 300L

    private val refreshPending = AtomicBoolean(false)

    /**
     * Schedules one refresh of every Conduit widget at most [COALESCE_WINDOW_MS] after the first
     * call of a burst. The pending flag is cleared before the refresh is sent, so a call that
     * arrives during or after it schedules another one: the final state is always delivered.
     * Runs on the process-lifetime scope (never cancelled) and off the main thread, because each
     * refresh makes AppWidgetManager binder calls.
     */
    fun updateAllWidgets(context: Context) {
        if (!refreshPending.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            delay(COALESCE_WINDOW_MS)
            refreshPending.set(false)
            ConduitWidgetProvider.updateAllWidgets(appContext)
            CustomViewWidgetProvider.updateAllWidgets(appContext)
        }
    }
}
