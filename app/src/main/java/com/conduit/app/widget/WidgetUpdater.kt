package com.conduit.app.widget

import android.content.Context

object WidgetUpdater {
    fun updateAllWidgets(context: Context) {
        ConduitWidgetProvider.updateAllWidgets(context)
        CustomViewWidgetProvider.updateAllWidgets(context)
    }
}
