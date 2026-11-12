package com.conduit.app.widget

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

object WidgetUpdater {
    // Bursts of calls (notification ingestion/removal) within this window collapse into one refresh.
    private const val COALESCE_WINDOW_MS = 300L

    private val refreshPending = AtomicBoolean(false)

    // One refresh pass at a time. On Android 15+ every widget refresh carries that widget's whole list
    // through binder (up to ~800 KB against a ~1 MB per-process buffer), so overlapping passes, or
    // several widgets refreshed at once, make the system drop refreshes.
    private val refreshMutex = Mutex()

    // True while a pass is waiting for the mutex: it has not read any data yet, so it covers later calls too.
    private val passQueued = AtomicBoolean(false)

    /**
     * Schedules one refresh of every Conduit widget at most [COALESCE_WINDOW_MS] after the first
     * call of a burst. The pending and queued flags are cleared before the refresh reads any data,
     * so a call that arrives during or after it schedules another one: the final state is always
     * delivered. Widgets are refreshed strictly one after another.
     * Runs on the process-lifetime scope (never cancelled) and off the main thread, because each
     * refresh makes blocking AppWidgetManager binder calls.
     */
    fun updateAllWidgets(context: Context) {
        if (!refreshPending.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            delay(COALESCE_WINDOW_MS)
            refreshPending.set(false)
            if (!passQueued.compareAndSet(false, true)) return@launch
            refreshMutex.withLock {
                passQueued.set(false)
                ConduitWidgetProvider.refreshAllWidgets(appContext)
                CustomViewWidgetProvider.refreshAllWidgets(appContext)
            }
        }
    }
}
