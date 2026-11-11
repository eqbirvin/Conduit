package com.conduit.app

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/**
 * Process-wide, bounded cache of app-icon bitmaps for the non-Compose surfaces (widgets, persistent tray,
 * bracket hanger/popup, bubbles). Separate from the Compose [appIconCache].
 *
 * Entries are keyed by package + pixel size and rasterised at that size (rule 18), so each caller stores
 * exactly what it displays. The drawable comes from [getAppIcon], so work-profile badging is preserved.
 * Packages without an icon are cached too (rule 19).
 *
 * Invalidation rides on [com.conduit.app.data.InstalledAppsRepository]'s package/profile watching: entries are
 * stored only while it is watching (so it would hear about an update or removal), are dropped per package on
 * package events, and everything is dropped when a profile changes or watching starts or stops. Outside that
 * window every lookup resolves live, as before this cache existed.
 *
 * Returned bitmaps are shared between callers: never mutate or recycle them.
 */
object AppIconBitmapCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MISS_COST_BYTES = 64
    private const val KEY_SEPARATOR = '|'

    private class Entry(val bitmap: Bitmap?)

    private val cache = object : LruCache<String, Entry>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Entry): Int =
            value.bitmap?.allocationByteCount ?: MISS_COST_BYTES
    }

    private val lock = Any()
    private var enabled = false // guarded by lock
    private var generation = 0L // guarded by lock; bumped on every invalidation

    /**
     * The app icon of [packageName] rasterised to fit [sizePx] x [sizePx] (aspect ratio kept), or null if the
     * package has no icon. Blocking on a miss (LauncherApps IPC + icon load): prefer calling off the main thread.
     */
    fun get(context: Context, packageName: String, sizePx: Int): Bitmap? {
        val size = sizePx.coerceAtLeast(1)
        val key = "$packageName$KEY_SEPARATOR$size"
        cache.get(key)?.let { return it.bitmap }

        val startGeneration = synchronized(lock) { generation }
        val bitmap = rasterise(context, packageName, size)
        synchronized(lock) {
            // A package event during the load may mean the bitmap is already stale: return it, don't keep it.
            if (enabled && generation == startGeneration) cache.put(key, Entry(bitmap))
        }
        return bitmap
    }

    /** Called by InstalledAppsRepository when its package watching starts (true) or stops (false). */
    fun setEnabled(isEnabled: Boolean) {
        synchronized(lock) {
            enabled = isEnabled
            generation++
            cache.evictAll()
        }
    }

    /** Drops every size cached for [packageName]. */
    fun invalidate(packageName: String) {
        val prefix = "$packageName$KEY_SEPARATOR"
        synchronized(lock) {
            generation++
            cache.snapshot().keys.forEach { key ->
                if (key.startsWith(prefix)) cache.remove(key)
            }
        }
    }

    /** Drops everything (profile changes, demo-mode toggle). */
    fun clear() {
        synchronized(lock) {
            generation++
            cache.evictAll()
        }
    }

    private fun rasterise(context: Context, packageName: String, sizePx: Int): Bitmap? {
        val drawable = getAppIcon(context, packageName) ?: return null
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        val (bitmapWidth, bitmapHeight) = when {
            w <= 0 || h <= 0 || w == h -> sizePx to sizePx
            w > h -> sizePx to (sizePx.toLong() * h / w).toInt().coerceAtLeast(1)
            else -> (sizePx.toLong() * w / h).toInt().coerceAtLeast(1) to sizePx
        }
        return drawable.toBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    }
}
