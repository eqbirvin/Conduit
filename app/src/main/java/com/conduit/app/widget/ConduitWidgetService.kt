package com.conduit.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.conduit.app.R
import com.conduit.app.getAppIcon
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification

class ConduitWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ConduitWidgetFactory(this.applicationContext, intent)
    }
}

class ConduitWidgetFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private var notifications: List<HubNotification> = emptyList()
    private val database = AppDatabase.getDatabase(context)

    override fun onCreate() {
        // Initialization if needed
    }

    override fun onDataSetChanged() {
        val prefs = context.getSharedPreferences("conduit_widget_prefs", Context.MODE_PRIVATE)
        val selectedFilter = prefs.getString("widget_filter_$appWidgetId", null)

        val activeNotifs = database.notificationDao().getActiveNotificationsWidgetSync()
        notifications = if (!selectedFilter.isNullOrEmpty()) {
            activeNotifs.filter { it.packageName == selectedFilter }
        } else {
            activeNotifs
        }
    }

    override fun onDestroy() {
        notifications = emptyList()
    }

    override fun getCount(): Int = notifications.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= notifications.size) return RemoteViews(context.packageName, R.layout.conduit_widget_list_item)
        
        val notification = notifications[position]
        val views = RemoteViews(context.packageName, R.layout.conduit_widget_list_item)

        views.setTextViewText(R.id.widget_item_title, notification.title ?: "Unknown")
        views.setTextViewText(R.id.widget_item_text, notification.text ?: "")
        
        val timeString = DateUtils.getRelativeTimeSpanString(
            notification.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
        views.setTextViewText(R.id.widget_item_time, timeString)

        // Set Icon
        val iconDrawable = getAppIcon(context, notification.packageName)
        if (iconDrawable != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    views.setImageViewIcon(R.id.widget_item_icon, android.graphics.drawable.Icon.createWithAdaptiveBitmap(
                        getBitmapFromDrawable(iconDrawable)
                    ))
                } else {
                    views.setImageViewBitmap(R.id.widget_item_icon, getBitmapFromDrawable(iconDrawable))
                }
            } catch (e: IllegalArgumentException) {
                views.setImageViewResource(R.id.widget_item_icon, R.mipmap.ic_launcher)
            }
        } else {
            views.setImageViewResource(R.id.widget_item_icon, R.mipmap.ic_launcher)
        }

        // Setup tap intent for the whole item
        val fillInIntent = Intent().apply {
            putExtra(ConduitWidgetProvider.EXTRA_ITEM_KEY, notification.notificationKey)
            putExtra(ConduitWidgetProvider.EXTRA_ITEM_PKG, notification.packageName)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        // Setup dynamic actions
        val actions = com.conduit.app.HubNotificationListenerService.instance?.getNotificationActions(notification.notificationKey)
        val actionViews = listOf(R.id.widget_item_action_1, R.id.widget_item_action_2, R.id.widget_item_action_3)

        // Hide all first
        actionViews.forEach { views.setViewVisibility(it, android.view.View.GONE) }
        views.setViewVisibility(R.id.widget_item_action_smart_check, android.view.View.GONE)

        val generalPrefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val smartMarkRead = generalPrefs.getBoolean("smart_mark_read", true)

        val hasNativeMarkRead = actions?.any { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            title.contains("read") || title.contains("done") || title.contains("clear") || title.contains("dismiss") || title.contains("archive")
        } ?: false

        val showConduitMarkRead = smartMarkRead && !hasNativeMarkRead
        var currentSlot = 0

        if (!actions.isNullOrEmpty()) {
            actions.forEach { action ->
                if (currentSlot >= 3) return@forEach

                val viewId = actionViews[currentSlot]
                views.setViewVisibility(viewId, android.view.View.VISIBLE)
                views.setTextViewText(viewId, action.title)
                views.setInt(viewId, "setBackgroundResource", R.drawable.widget_chip_bg)

                val actionFillInIntent = Intent().apply {
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_KEY, notification.notificationKey)
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_PKG, notification.packageName)
                    putExtra("action_index", actions.indexOf(action))
                }
                views.setOnClickFillInIntent(viewId, actionFillInIntent)
                currentSlot++
            }
        }

        if (showConduitMarkRead) {
            if (currentSlot < 3) {
                val viewId = actionViews[currentSlot]
                views.setViewVisibility(viewId, android.view.View.VISIBLE)
                views.setTextViewText(viewId, "Mark Read")
                views.setInt(viewId, "setBackgroundResource", R.drawable.widget_chip_conduit_bg)

                val actionFillInIntent = Intent().apply {
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_KEY, notification.notificationKey)
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_PKG, notification.packageName)
                    putExtra("is_conduit_mark_read", true)
                }
                views.setOnClickFillInIntent(viewId, actionFillInIntent)
            } else {
                val checkViewId = R.id.widget_item_action_smart_check
                views.setViewVisibility(checkViewId, android.view.View.VISIBLE)

                val actionFillInIntent = Intent().apply {
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_KEY, notification.notificationKey)
                    putExtra(ConduitWidgetProvider.EXTRA_ITEM_PKG, notification.packageName)
                    putExtra("is_conduit_mark_read", true)
                }
                views.setOnClickFillInIntent(checkViewId, actionFillInIntent)
            }
        }

        return views
    }

    private fun getBitmapFromDrawable(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100,
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = if (position < notifications.size) notifications[position].id.toLong() else position.toLong()
    override fun hasStableIds(): Boolean = true
}

