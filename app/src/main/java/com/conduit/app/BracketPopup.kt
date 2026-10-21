package com.conduit.app

import android.util.Log
import android.view.WindowManager
import android.view.View
import android.app.PendingIntent

    internal fun HubNotificationListenerService.setBracketExpanded(expanded: Boolean) {
        val view = bracketView ?: return
    }

    internal fun HubNotificationListenerService.showBracketNotificationPopup(packageName: String, contentIntent: PendingIntent?) {
        val iconView = popupIconView ?: return
        val imageView = popupImageView ?: return

        handler.removeCallbacks(collapseRunnable)
        iconView.animate().cancel()

        activePendingIntent = contentIntent
        activePackageName = packageName

        try {
            val drawable = getAppIcon(this, packageName)
            if (drawable != null) {
                imageView.setImageDrawable(drawable)
            } else {
                imageView.setImageResource(R.mipmap.ic_launcher)
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            imageView.setImageResource(R.mipmap.ic_launcher)
        }

        if (!popupWindowAdded && popupWindowParams != null) {
            try {
                windowManager?.addView(iconView, popupWindowParams)
                popupWindowAdded = true
            } catch (e: android.view.WindowManager.BadTokenException) {
                android.util.Log.e("Conduit", "Bad window token", e)
            } catch (e: SecurityException) {
                android.util.Log.e("Conduit", "Security exception", e)
            }
        }

        iconView.visibility = View.VISIBLE
        iconView.alpha = 0f
        iconView.translationX = 40f * resources.displayMetrics.density

        setBracketExpanded(true)

        iconView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .withEndAction {
                handler.postDelayed(collapseRunnable, 2000)
            }
            .start()
    }

    internal fun HubNotificationListenerService.animateCollapse() {
        val iconView = popupIconView ?: return
        iconView.animate()
            .alpha(0f)
            .translationX(40f * resources.displayMetrics.density)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                iconView.visibility = View.GONE
                if (popupWindowAdded) {
                    try { windowManager?.removeView(iconView) } catch (e: IllegalArgumentException) {}
                    popupWindowAdded = false
                }
                setBracketExpanded(false)
                activePendingIntent = null
                activePackageName = null
            }
            .start()
    }

    internal fun HubNotificationListenerService.hidePopupImmediately() {
        handler.removeCallbacks(collapseRunnable)
        popupIconView?.let { iconView ->
            iconView.animate().cancel()
            iconView.visibility = View.GONE
            iconView.alpha = 0f
            iconView.translationX = 0f
            if (popupWindowAdded) {
                try { windowManager?.removeView(iconView) } catch (e: IllegalArgumentException) {}
                popupWindowAdded = false
            }
        }
        setBracketExpanded(false)
        activePendingIntent = null
        activePackageName = null
    }

    internal fun HubNotificationListenerService.removeBracketView() {
        hangerRootView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("Conduit", "View not attached", e)
            }
            hangerRootView = null
        }
        bracketView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("Conduit", "View not attached", e)
            }
            bracketView = null
            if (popupWindowAdded) {
                popupIconView?.let { try { windowManager?.removeView(it) } catch (e: IllegalArgumentException) {} }
                popupWindowAdded = false
            }
            popupIconView = null
            popupImageView = null
            popupWindowParams = null
            activePendingIntent = null
            activePackageName = null
            handler.removeCallbacks(collapseRunnable)
        }
    }
