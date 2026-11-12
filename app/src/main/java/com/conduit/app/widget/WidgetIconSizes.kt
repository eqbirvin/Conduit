package com.conduit.app.widget

import android.content.Context
import kotlin.math.roundToInt

/**
 * Pixel sizes at which widget app icons are actually displayed, used to request icons from
 * [com.conduit.app.AppIconBitmapCache]. They mirror the layouts; change them together.
 * Layout dp→px conversion: view sizes round (getDimensionPixelSize), paddings truncate (getDimensionPixelOffset).
 */
internal object WidgetIconSizes {
    /** widget_slot_icon_N in conduit_widget_layout.xml: 48dp ImageView with 4dp padding = 40dp content box (fitCenter). */
    fun slotIconPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (48 * density).roundToInt() - 2 * (4 * density).toInt()
    }

    /**
     * widget_item_icon in conduit_widget_list_item.xml is 36dp, but it is set via Icon.createWithAdaptiveBitmap:
     * AdaptiveIconDrawable draws that bitmap 1.5x the view size (25% extra inset per side) and masks it, so the
     * bitmap is displayed at 54dp.
     */
    /** widget_item_icon in conduit_widget_list_item.xml: the 36dp view box the masked row icon is drawn into. */
    fun listItemViewPx(context: Context): Int =
        (36 * context.resources.displayMetrics.density).roundToInt()

    fun listItemAdaptiveIconPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (36 * density).roundToInt() * 3 / 2
    }
}
