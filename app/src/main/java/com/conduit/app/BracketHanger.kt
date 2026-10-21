package com.conduit.app

import com.conduit.app.HubNotificationListenerService.Companion.supportedApps
import com.conduit.app.HubNotificationListenerService.HangerItem
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.graphics.Color

    internal fun HubNotificationListenerService.openHanger() {
        if (hangerOpened) return
        hangerOpened = true
        
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(10)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val hangerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hangerParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hangerParams.blurBehindRadius = (20 * resources.displayMetrics.density).toInt()
        }
        hangerParams.gravity = Gravity.TOP or Gravity.START
        hangerParams.x = 0
        hangerParams.y = 0

        try {
            hangerRootView?.visibility = View.VISIBLE
            windowManager?.addView(hangerRootView, hangerParams)
            hangerRootView?.requestFocus()
        } catch (e: Exception) { android.util.Log.e("Conduit", "Exception caught", e) }
        
        populateHangerNotifications()
    }

    internal fun HubNotificationListenerService.closeHanger() {
        if (!hangerOpened) return
        hangerOpened = false
        hoverIndex = -1
        
        try {
            hangerRootView?.visibility = View.GONE
            windowManager?.removeView(hangerRootView)
        } catch (e: Exception) { android.util.Log.e("Conduit", "Exception caught", e) }
        
        hangerLinearLayout?.removeAllViews()
        pendingIntentList.clear()
        expandedBundleKey = null
        handler.removeCallbacks(expandBundleRunnable)
    }

    internal fun HubNotificationListenerService.updateHoverState(x: Float, y: Float) {
        val layout = hangerLinearLayout ?: return
        var newHoverIndex = -1
        
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            val loc = IntArray(2)
            child.getLocationOnScreen(loc)
            val rect = android.graphics.Rect(loc[0], loc[1], loc[0] + child.width, loc[1] + child.height)
            if (rect.contains(x.toInt(), y.toInt())) {
                newHoverIndex = i
                break
            }
        }
        
        if (newHoverIndex != hoverIndex) {
            handler.removeCallbacks(expandBundleRunnable)
            if (newHoverIndex != -1) {
                val item = pendingIntentList.getOrNull(newHoverIndex)
                if (item is HangerItem.BundleItem && expandedBundleKey != item.channelKey) {
                    handler.postDelayed(expandBundleRunnable, 400)
                }

                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    manager.defaultVibrator
                } else {
                    getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                }
            }
            
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i) as? android.widget.FrameLayout ?: continue
                if (i == newHoverIndex) {
                    child.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                    child.alpha = 1.0f
                } else {
                    child.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    child.alpha = if (newHoverIndex != -1) 0.5f else 1.0f
                }
            }
            hoverIndex = newHoverIndex
        }
    }

    internal fun HubNotificationListenerService.populateHangerNotifications() {
        val layout = hangerLinearLayout ?: return
        layout.removeAllViews()
        pendingIntentList.clear()
        
        val activeNotifs = activeNotifications.sortedByDescending { it.postTime }
        val density = resources.displayMetrics.density
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val channelInstagram = prefs.getBoolean("channel_instagram", true)
        val channelPhone = prefs.getBoolean("channel_phone", true)
        val channelTelegram = prefs.getBoolean("channel_telegram", true)
        val channelReddit = prefs.getBoolean("channel_reddit", true)
        val channelSteam = prefs.getBoolean("channel_steam", true)
        val groupByChannel = prefs.getBoolean("group_by_channel", false)
        
        val filteredNotifs = mutableListOf<android.service.notification.StatusBarNotification>()
        for (sbn in activeNotifs) {
            val packageName = sbn.packageName
            val notif = sbn.notification
            
            val appInfo = supportedApps[packageName]
            val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && notif.category == Notification.CATEGORY_CALL) ||
                                        packageName.contains(".dialer", ignoreCase = true) ||
                                        packageName.endsWith(".phone", ignoreCase = true)
            
            val channelInfo = appInfo ?: if (isSystemPhoneFallback) Pair("channel_phone", "Phone") else null
            if (channelInfo != null) {
                val prefKey = channelInfo.first
                if (prefs.getBoolean(prefKey, true)) {
                    if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
                        filteredNotifs.add(sbn)
                    }
                }
            }
        }

        if (groupByChannel) {
            val grouped = filteredNotifs.groupBy { sbn ->
                val pkg = sbn.packageName
                val appInfo = supportedApps[pkg]
                val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && sbn.notification.category == Notification.CATEGORY_CALL) ||
                                            pkg.contains(".dialer", ignoreCase = true) ||
                                            pkg.endsWith(".phone", ignoreCase = true)
                appInfo?.first ?: if (isSystemPhoneFallback) "channel_phone" else pkg
            }
            for ((channelKey, notifs) in grouped) {
                if (notifs.size == 1) {
                    val card = createNotificationCard(notifs[0], density, false)
                    layout.addView(card)
                    pendingIntentList.add(HangerItem.NotificationItem(notifs[0].notification.contentIntent, notifs[0].packageName))
                } else {
                    val representativePkg = notifs[0].packageName
                    val channelName = supportedApps.values.firstOrNull { it.first == channelKey }?.second ?: getAppLabel(this, representativePkg)
                    if (expandedBundleKey == channelKey) {
                        // Show bundle header
                        val bundleHeader = createBundleCard(channelKey, representativePkg, channelName, notifs.size, density, true)
                        layout.addView(bundleHeader)
                        pendingIntentList.add(HangerItem.BundleItem(channelKey, representativePkg))
                        // Show all items under it
                        for (notif in notifs) {
                            val card = createNotificationCard(notif, density, true)
                            layout.addView(card)
                            pendingIntentList.add(HangerItem.NotificationItem(notif.notification.contentIntent, notif.packageName))
                        }
                    } else {
                        // Collapsed bundle
                        val bundleCard = createBundleCard(channelKey, representativePkg, channelName, notifs.size, density, false)
                        layout.addView(bundleCard)
                        pendingIntentList.add(HangerItem.BundleItem(channelKey, representativePkg))
                    }
                }
            }
        } else {
            for (sbn in filteredNotifs) {
                val card = createNotificationCard(sbn, density, false)
                layout.addView(card)
                pendingIntentList.add(HangerItem.NotificationItem(sbn.notification.contentIntent, sbn.packageName))
            }
        }
    }

    internal fun HubNotificationListenerService.createNotificationCard(sbn: android.service.notification.StatusBarNotification, density: Float, isIndented: Boolean): View {
        val notif = sbn.notification
        val packageName = sbn.packageName
        val title = notif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val card = FrameLayout(this)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#EFEFEF")) // Light surface
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            bg.setColor(Color.parseColor("#1E1E1E")) // Dark surface
        }
        bg.cornerRadius = 16f * density
        card.background = bg
        
        val cardParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.bottomMargin = (12 * density).toInt()
        if (isIndented) {
            cardParams.leftMargin = (32 * density).toInt()
        }
        card.layoutParams = cardParams
        
        val innerLayout = android.widget.LinearLayout(this)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        
        val iconContainer = FrameLayout(this)
        val iconParams = android.widget.LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
        iconParams.rightMargin = (16 * density).toInt()
        iconContainer.layoutParams = iconParams
        
        val iconView = android.widget.ImageView(this)
        iconView.layoutParams = FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
        try {
            val icon = notif.getLargeIcon() ?: notif.smallIcon
            iconView.setImageIcon(icon)
        } catch (e: Exception) {}
        iconContainer.addView(iconView)
        
        val appIconView = android.widget.ImageView(this)
        val appIconParams = FrameLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
        appIconParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
        appIconView.layoutParams = appIconParams
        try {
            val appIcon = getAppIcon(this, packageName)
            if (appIcon != null) {
                appIconView.setImageDrawable(appIcon)
            }
        } catch (e: Exception) {}
        iconContainer.addView(appIconView)
        
        innerLayout.addView(iconContainer)
        
        val textLayout = android.widget.LinearLayout(this)
        textLayout.orientation = android.widget.LinearLayout.VERTICAL
        
        val titleView = android.widget.TextView(this)
        titleView.text = title.ifEmpty { "Notification" }
        titleView.textSize = 16f
        titleView.setTextColor(if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
        titleView.setTypeface(null, android.graphics.Typeface.BOLD)
        textLayout.addView(titleView)
        
        if (text.isNotEmpty()) {
            val textView = android.widget.TextView(this)
            textView.text = text
            textView.textSize = 14f
            textView.setTextColor(if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.parseColor("#CCCCCC") else Color.parseColor("#444444"))
            textView.maxLines = 6
            textView.ellipsize = android.text.TextUtils.TruncateAt.END
            textLayout.addView(textView)
        }
        
        innerLayout.addView(textLayout)
        card.addView(innerLayout)

        val pi = notif.contentIntent
        card.setOnClickListener {
            try {
                if (pi != null) {
                    val options = android.app.ActivityOptions.makeBasic()
                    if (Build.VERSION.SDK_INT >= 34) {
                        options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    pi.send(this@createNotificationCard, 0, Intent(), null, null, null, options.toBundle())
                } else {
                    throw Exception("No intent")
                }
            } catch (e: Exception) {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            }
            handler.postDelayed({ closeHanger() }, 150)
        }

        return card
    }

    internal fun HubNotificationListenerService.createBundleCard(channelKey: String, packageName: String, channelName: String, count: Int, density: Float, isExpanded: Boolean): View {
        val card = FrameLayout(this)
        val bg = GradientDrawable()
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNight = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        bg.setColor(if (isNight) Color.parseColor("#2A2A2A") else Color.parseColor("#E0E0E0"))
        bg.cornerRadius = 16f * density
        card.background = bg
        
        val cardParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.bottomMargin = (12 * density).toInt()
        card.layoutParams = cardParams
        
        val innerLayout = android.widget.LinearLayout(this)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.gravity = Gravity.CENTER_VERTICAL
        innerLayout.setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        
        val appIconView = android.widget.ImageView(this)
        appIconView.layoutParams = android.widget.LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt()).apply {
            rightMargin = (16 * density).toInt()
        }
        try {
            val icon = getAppIcon(this, packageName)
            if (icon != null) {
                appIconView.setImageDrawable(icon)
            }
        } catch (e: Exception) {}
        innerLayout.addView(appIconView)
        
        val titleView = android.widget.TextView(this)
        titleView.text = "$count Notifications from $channelName"
        titleView.textSize = 16f
        titleView.setTextColor(if (isNight) Color.WHITE else Color.BLACK)
        titleView.setTypeface(null, android.graphics.Typeface.BOLD)
        titleView.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        innerLayout.addView(titleView)

        val chevron = android.widget.ImageView(this)
        chevron.layoutParams = android.widget.LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
        chevron.setImageResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        chevron.setColorFilter(if (isNight) Color.WHITE else Color.BLACK)
        innerLayout.addView(chevron)
        
        card.addView(innerLayout)

        card.setOnClickListener {
            if (isExpanded) {
                expandedBundleKey = null
            } else {
                expandedBundleKey = channelKey
            }
            populateHangerNotifications()
        }

        return card
    }
