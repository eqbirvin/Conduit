package com.conduit.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.conduit.app.R
import com.conduit.app.getAppIcon
import com.conduit.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConduitWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SELECT_PACKAGE = "com.conduit.app.widget.ACTION_SELECT_PACKAGE"
        const val EXTRA_PKG = "com.conduit.app.widget.EXTRA_PKG"
        const val EXTRA_ITEM_KEY = "com.conduit.app.widget.EXTRA_ITEM_KEY"
        const val EXTRA_ITEM_PKG = "com.conduit.app.widget.EXTRA_ITEM_PKG"
        
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ConduitWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, ConduitWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
            }
        }

        suspend fun updateAppWidgetSync(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val db = AppDatabase.getDatabase(context)
            val activeNotifs = db.notificationDao().getActiveNotificationsWidgetSync()
                
                // Group active notifications by package
                val grouped = activeNotifs.groupBy { it.packageName }
                
                // Sort apps chronologically by their newest notification's timestamp
                val sortedApps = grouped.keys.map { pkg ->
                    pkg to (grouped[pkg]?.maxOfOrNull { it.timestamp } ?: 0L)
                }.sortedByDescending { it.second }.map { it.first }
                
                val prefs = context.getSharedPreferences("conduit_widget_prefs", Context.MODE_PRIVATE)
                val selectedFilter = prefs.getString("widget_filter_$appWidgetId", null)
                
                val views = RemoteViews(context.packageName, R.layout.conduit_widget_layout)
                
                val slotLayoutIds = listOf(
                    R.id.widget_slot_1,
                    R.id.widget_slot_2,
                    R.id.widget_slot_3,
                    R.id.widget_slot_4,
                    R.id.widget_slot_5,
                    R.id.widget_slot_6,
                    R.id.widget_slot_7
                )
                val slotIconIds = listOf(
                    R.id.widget_slot_icon_1,
                    R.id.widget_slot_icon_2,
                    R.id.widget_slot_icon_3,
                    R.id.widget_slot_icon_4,
                    R.id.widget_slot_icon_5,
                    R.id.widget_slot_icon_6,
                    R.id.widget_slot_icon_7
                )
                val slotBadgeIds = listOf(
                    R.id.widget_slot_badge_1,
                    R.id.widget_slot_badge_2,
                    R.id.widget_slot_badge_3,
                    R.id.widget_slot_badge_4,
                    R.id.widget_slot_badge_5,
                    R.id.widget_slot_badge_6,
                    R.id.widget_slot_badge_7
                )
                
                // Hide all slots first
                for (id in slotLayoutIds) {
                    views.setViewVisibility(id, View.GONE)
                }
                
                val pm = context.packageManager
                sortedApps.take(7).forEachIndexed { index, pkg ->
                    val layoutId = slotLayoutIds[index]
                    val iconId = slotIconIds[index]
                    val badgeId = slotBadgeIds[index]
                    
                    views.setViewVisibility(layoutId, View.VISIBLE)
                    
                    // Set app icon
                    val iconDrawable = getAppIcon(context, pkg)
                    if (iconDrawable != null) {
                        try {
                            val iconBitmap = getBitmapFromDrawable(iconDrawable)
                            views.setImageViewBitmap(iconId, iconBitmap)
                        } catch (e: IllegalArgumentException) {
                            views.setImageViewResource(iconId, R.mipmap.ic_launcher)
                        }
                    } else {
                        views.setImageViewResource(iconId, R.mipmap.ic_launcher)
                    }
                    
                    // Highlight background if selected
                    if (pkg == selectedFilter) {
                        views.setInt(layoutId, "setBackgroundResource", R.drawable.widget_icon_bg)
                    } else {
                        views.setInt(layoutId, "setBackgroundResource", 0)
                    }
                    
                    // Set badge count
                    val count = grouped[pkg]?.size ?: 0
                    if (count > 0) {
                        views.setViewVisibility(badgeId, View.VISIBLE)
                        views.setTextViewText(badgeId, count.toString())
                    } else {
                        views.setViewVisibility(badgeId, View.GONE)
                    }
                    
                    // Set up click intent for this slot to filter
                    val clickIntent = Intent(context, ConduitWidgetProvider::class.java).apply {
                        action = ACTION_SELECT_PACKAGE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra(EXTRA_PKG, pkg)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + index, // unique request code per slot
                        clickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(layoutId, pendingIntent)
                }
                
                // Set up ListView adapter
                val intent = Intent(context, ConduitWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_list_view, intent)
                views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view)
                
                // Set up item click template
                val clickIntentTemplate = Intent(context, ConduitWidgetClickActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val clickPendingIntentTemplate = PendingIntent.getActivity(
                    context,
                    0,
                    clickIntentTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntentTemplate)
                
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable) {
                return drawable.bitmap
            }
            val bitmap = Bitmap.createBitmap(
                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100,
                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidgetSync(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_SELECT_PACKAGE -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val pkg = intent.getStringExtra(EXTRA_PKG)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val prefs = context.getSharedPreferences("conduit_widget_prefs", Context.MODE_PRIVATE)
                            val currentFilter = prefs.getString("widget_filter_$appWidgetId", null)
                            
                            val newFilter = if (currentFilter == pkg) {
                                null // Toggle off if clicked again
                            } else {
                                pkg
                            }
                            prefs.edit().putString("widget_filter_$appWidgetId", newFilter).apply()
                            
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            updateAppWidgetSync(context, appWidgetManager, appWidgetId)
                            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}

