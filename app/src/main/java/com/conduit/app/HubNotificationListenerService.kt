package com.conduit.app

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.provider.Settings
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.LocusIdCompat

class HubNotificationListenerService : NotificationListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var instance: HubNotificationListenerService? = null

        val supportedApps = mapOf(
            "com.google.android.apps.messaging" to Pair("channel_messages", "Google Messages"),
            "com.google.android.gm" to Pair("channel_gmail", "Gmail"),
            "com.readdle.spark" to Pair("channel_spark", "Spark"),
            "com.microsoft.office.outlook" to Pair("channel_outlook", "Outlook"),
            "com.snapchat.android" to Pair("channel_snapchat", "Snapchat"),
            "com.linkedin.android" to Pair("channel_linkedin", "LinkedIn"),
            "com.instagram.android" to Pair("channel_instagram", "Instagram"),
            "com.google.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.samsung.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.android.phone" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.android.server.telecom" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.android.contacts" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.google.android.apps.tycho" to Pair("channel_phone", "Phone (Google Dialer)"),
            "com.truecaller" to Pair("channel_truecaller", "Truecaller"),
            "org.telegram.messenger" to Pair("channel_telegram", "Telegram"),
            "org.thunderdog.challegram" to Pair("channel_telegram_x", "Telegram X"),
            "com.reddit.frontpage" to Pair("channel_reddit", "Reddit"),
            "com.valvesoftware.android.steam.community" to Pair("channel_steam", "Steam"),
            "com.valvesoftware.android.steam.friendsui" to Pair("channel_steam_chat", "Steam Chat"),
            "com.facebook.katana" to Pair("channel_facebook", "Facebook"),
            "com.facebook.orca" to Pair("channel_messenger", "Messenger"),
            "com.twitter.android" to Pair("channel_twitter", "Twitter (X)"),
            "com.microsoft.teams" to Pair("channel_teams", "Microsoft Teams")
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val notificationMutex = Mutex()
    private lateinit var database: AppDatabase
    private val actionCache = java.util.concurrent.ConcurrentHashMap<String, List<Notification.Action>>()
    private val contentIntentCache = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()
    private val DISMISS_ORIGINAL_NOTIFICATIONS = false
    
    private var windowManager: WindowManager? = null
    private var bracketView: View? = null
    private var hangerRootView: FrameLayout? = null
    private var hangerLinearLayout: android.widget.LinearLayout? = null
    private var popupIconView: View? = null
    private var popupImageView: android.widget.ImageView? = null
    private var popupWindowParams: WindowManager.LayoutParams? = null
    private var popupWindowAdded = false
    private var activePendingIntent: PendingIntent? = null
    private var activePackageName: String? = null
    
    private var hangerOpened = false
    private var startX = 0f
    private var startY = 0f
    private var gestureStartTime = 0L
    private var isDragging = false
    private var hoverIndex = -1
    sealed class HangerItem {
        data class BundleItem(val packageName: String) : HangerItem()
        data class NotificationItem(val pendingIntent: PendingIntent?, val packageName: String) : HangerItem()
    }
    private var pendingIntentList = mutableListOf<HangerItem>()
    private var expandedBundlePackageName: String? = null
    private val expandBundleRunnable = Runnable {
        val index = hoverIndex
        if (index >= 0 && index < pendingIntentList.size) {
            val item = pendingIntentList[index]
            if (item is HangerItem.BundleItem) {
                if (expandedBundlePackageName != item.packageName) {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        manager.defaultVibrator
                    } else {
                        getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else {
                        vibrator.vibrate(50)
                    }
                    expandedBundlePackageName = item.packageName
                    populateHangerNotifications()
                }
            }
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val collapseRunnable = Runnable { animateCollapse() }
    private val updatePersistentRunnable = Runnable { updatePersistentNotification() }

    private fun scheduleUpdatePersistentNotification() {
        handler.removeCallbacks(updatePersistentRunnable)
        handler.postDelayed(updatePersistentRunnable, 500)
    }

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                closeHanger()
            }
        }
    }

    private fun getBasBundle(): android.os.Bundle {
        val options = android.app.ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        return options.toBundle()
    }

    fun importActiveNotifications() {
        val active = try { activeNotifications } catch (e: Exception) { null }
        active?.forEach { sbn ->
            try {
                onNotificationPosted(sbn)
            } catch (e: Exception) {
                android.util.Log.e("HubService", "Error importing notification", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        scheduleUpdatePersistentNotification()

        scope.launch {
            try {
                val prefs = getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                val autoDismissDetached = prefs.getBoolean("auto_dismiss_detached", true)
                val now = System.currentTimeMillis()
                
                val activeKeys = try { activeNotifications.map { it.key }.toSet() } catch (e: Exception) { emptySet() }
                val unarchivedMetadata = database.notificationDao().getActiveNotificationMetadata()
                
                var hasChanges = false
                for (meta in unarchivedMetadata) {
                    if (meta.isPinned) continue
                    
                    val inTray = activeKeys.contains(meta.notificationKey)
                    if (!inTray && meta.detachedAt == null) {
                        database.notificationDao().setDetached(meta.id, now)
                        if (meta.kind == "OTHER" && autoDismissDetached && !meta.isSnoozed) {
                            database.notificationDao().archiveNotification(meta.id, now)
                        }
                        hasChanges = true
                    } else if (inTray && meta.detachedAt != null) {
                        database.notificationDao().clearDetached(meta.id)
                        hasChanges = true
                    }
                }
                
                if (hasChanges) {
                    com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                }

                val retentionDays = prefs.getInt("retention_days", 90)
                val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
                val deletedCount = database.notificationDao().deleteOldArchivedNotifications(cutoffTimestamp)
                if (deletedCount > 0) {
                    android.util.Log.d("HubNotificationService", "Pruned $deletedCount notifications older than $retentionDays days")
                }
            } catch (e: Exception) {
                android.util.Log.e("HubNotificationService", "Failed to run notification retention cleanup", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        try {
            val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(closeSystemDialogsReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(closeSystemDialogsReceiver, filter)
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Conduit", "Invalid receiver", e)
        } catch (e: SecurityException) {
            android.util.Log.e("Conduit", "Security exception", e)
        }
        
        updateBracketState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        try {
            unregisterReceiver(closeSystemDialogsReceiver)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Conduit", "Receiver not registered", e)
        }
        removeBracketView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "enable_bracket") {
            updateBracketState()
        } else if (key == "bracket_vertical_position") {
            updateBracketPosition()
        }
    }

    private fun updateBracketPosition() {
        val view = bracketView ?: return
        if (hangerOpened) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val position = prefs.getFloat("bracket_vertical_position", 0.5f)
        
        val screenHeight = resources.displayMetrics.heightPixels
        val bracketHeight = (120 * resources.displayMetrics.density).toInt()
        
        params.y = ((screenHeight - bracketHeight) * position).toInt()
        
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Conduit", "View not attached", e)
        } catch (e: android.view.WindowManager.BadTokenException) {
            android.util.Log.e("Conduit", "Bad window token", e)
        }
    }

    private fun updateBracketState() {
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val enableBracket = prefs.getBoolean("enable_bracket", false)
        val canDrawOverlays = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        if (enableBracket && canDrawOverlays) {
            addBracketView()
        } else {
            removeBracketView()
        }
    }

    private fun addBracketView() {
        if (bracketView != null) return // Already added

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // --- Hanger Fullscreen Window Setup ---
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
        
        hangerRootView = object : FrameLayout(this) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (ev.action == android.view.MotionEvent.ACTION_CANCEL) {
                    closeHanger()
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    closeHanger()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) {
                    closeHanger()
                }
            }
        }.apply {
            isFocusableInTouchMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBackgroundColor(Color.parseColor("#99000000"))
            } else {
                setBackgroundColor(Color.parseColor("#E6000000"))
            }
            setOnClickListener { closeHanger() }
        }
        
        val scrollParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val scrollView = android.widget.ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, (64 * resources.displayMetrics.density).toInt(), 0, (64 * resources.displayMetrics.density).toInt())
            setOnClickListener { closeHanger() }
        }
        hangerLinearLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                0,
                (72 * resources.displayMetrics.density).toInt(),
                0
            )
            setOnClickListener { closeHanger() }
        }
        scrollView.addView(hangerLinearLayout)
        hangerRootView?.addView(scrollView, scrollParams)

        // --- Bracket Handle Window Setup ---
        val params = WindowManager.LayoutParams(
            (32 * resources.displayMetrics.density).toInt(),
            (120 * resources.displayMetrics.density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val position = prefs.getFloat("bracket_vertical_position", 0.5f)
        val screenHeight = resources.displayMetrics.heightPixels
        val bracketHeight = (120 * resources.displayMetrics.density).toInt()
        params.y = ((screenHeight - bracketHeight) * position).toInt()

        val view = FrameLayout(this)
        
        // 1. Bracket Handle (touch container)
        val bracketHandle = FrameLayout(this)
        
        // Inner visual background for the bracket
        val bracketBackground = View(this)
        val bg = GradientDrawable()
        bg.setColor(Color.BLACK)
        val radius = 24f * resources.displayMetrics.density
        bg.cornerRadii = floatArrayOf(
            radius, radius,
            0f, 0f,
            0f, 0f,
            radius, radius
        )
        bracketBackground.background = bg
        
        val bgParams = FrameLayout.LayoutParams(
            (16 * resources.displayMetrics.density).toInt(),
            (100 * resources.displayMetrics.density).toInt()
        )
        bgParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        bracketHandle.addView(bracketBackground, bgParams)

        // Line view inside the handle
        val lineView = View(this)
        val lineBg = GradientDrawable()
        lineBg.setColor(Color.parseColor("#44FFFFFF"))
        lineBg.cornerRadius = 2f * resources.displayMetrics.density
        lineView.background = lineBg
        
        val lineParams = FrameLayout.LayoutParams(
            (2 * resources.displayMetrics.density).toInt(),
            (32 * resources.displayMetrics.density).toInt()
        )
        lineParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        lineParams.rightMargin = (6 * resources.displayMetrics.density).toInt()
        bracketHandle.addView(lineView, lineParams)
        
        val handleWidth = (32 * resources.displayMetrics.density).toInt()
        val handleHeight = (120 * resources.displayMetrics.density).toInt()
        
        val handleParams = FrameLayout.LayoutParams(handleWidth, handleHeight)
        handleParams.gravity = Gravity.TOP or Gravity.END
        view.addView(bracketHandle, handleParams)

        // 2. Popup Icon view (standalone window)
        val iconContainer = FrameLayout(this)
        val iconBg = GradientDrawable()
        iconBg.shape = GradientDrawable.OVAL
        iconBg.setColor(Color.parseColor("#222222")) // Dark background for the icon
        iconContainer.background = iconBg
        
        val imageView = android.widget.ImageView(this)
        val imageParams = FrameLayout.LayoutParams(
            (24 * resources.displayMetrics.density).toInt(),
            (24 * resources.displayMetrics.density).toInt()
        )
        imageParams.gravity = Gravity.CENTER
        imageView.layoutParams = imageParams
        iconContainer.addView(imageView)
        iconContainer.alpha = 0f
        
        // Define WindowManager layout params for the popup icon
        popupWindowParams = WindowManager.LayoutParams(
            (40 * resources.displayMetrics.density).toInt(),
            (40 * resources.displayMetrics.density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (24 * resources.displayMetrics.density).toInt() // 16dp visual handle + 8dp margin
            y = params.y + ((120 - 40) / 2 * resources.displayMetrics.density).toInt() // Center vertically with the bracket handle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        popupIconView = iconContainer
        popupImageView = imageView
        
        bracketHandle.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rect = android.graphics.Rect(
                        0,
                        0,
                        handleWidth,
                        handleHeight
                    )
                    view.systemGestureExclusionRects = listOf(rect)
                }
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("Conduit", "Invalid gesture rect", e)
            }
        }

        val longPressRunnable = Runnable {
            if (!isDragging && !hangerOpened) {
                isDragging = true
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    manager.defaultVibrator
                } else {
                    getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                } else {
                    vibrator.vibrate(50)
                }
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent, getBasBundle())
            }
        }

        val touchListener = android.view.View.OnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    gestureStartTime = System.currentTimeMillis()
                    isDragging = false
                    handler.postDelayed(longPressRunnable, 500)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val touchSlop = 10 * resources.displayMetrics.density
                    
                    if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    
                    val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                    val hangerEnabled = prefs.getBoolean("bracket_hanger_enabled", true)
                    
                    if (isDragging && hangerEnabled) {
                        if (!hangerOpened && dx < - (20 * resources.displayMetrics.density)) {
                            openHanger()
                        }
                        if (hangerOpened) {
                            updateHoverState(event.rawX, event.rawY)
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (hangerOpened) {
                        if (hoverIndex >= 0 && hoverIndex < pendingIntentList.size) {
                            // Dragged over a card and released
                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                                manager.defaultVibrator
                            } else {
                                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                            } else {
                                vibrator.vibrate(20)
                            }
                            try {
                                val item = pendingIntentList[hoverIndex]
                                if (item is HangerItem.NotificationItem) {
                                    if (item.pendingIntent != null) {
                                        item.pendingIntent.send(this@HubNotificationListenerService, 0, null, null, null, null, getBasBundle())
                                    } else {
                                        throw Exception("No intent")
                                    }
                                } else if (item is HangerItem.BundleItem) {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(launchIntent)
                                    }
                                }
                            } catch (e: android.app.PendingIntent.CanceledException) {
                                val pkg = if (pendingIntentList[hoverIndex] is HangerItem.BundleItem) (pendingIntentList[hoverIndex] as HangerItem.BundleItem).packageName else (pendingIntentList[hoverIndex] as HangerItem.NotificationItem).packageName
                                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent, getBasBundle())
                                }
                            }
                            handler.postDelayed({ closeHanger() }, 150)
                        } else {
                            val duration = System.currentTimeMillis() - gestureStartTime
                            if (isDragging && duration > 350) {
                                // Long swipe-and-hold released on empty space
                                closeHanger()
                            } else if (!isDragging) {
                                // Simple tap on the handle
                                closeHanger()
                            }
                        }
                    } else if (!isDragging) {
                        // Regular click
                        val pendingIntent = activePendingIntent
                        if (pendingIntent != null) {
                            try {
                                pendingIntent.send(this@HubNotificationListenerService, 0, null, null, null, null, getBasBundle())
                            } catch (e: android.app.PendingIntent.CanceledException) {
                                activePackageName?.let { pkg ->
                                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(launchIntent, getBasBundle())
                                    }
                                }
                            }
                            hidePopupImmediately()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        
        bracketHandle.setOnTouchListener(touchListener)
        iconContainer.setOnClickListener {
            val pendingIntent = activePendingIntent
            if (pendingIntent != null) {
                try {
                    pendingIntent.send(this@HubNotificationListenerService, 0, null, null, null, null, getBasBundle())
                } catch (e: android.app.PendingIntent.CanceledException) {
                    activePackageName?.let { pkg ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent, getBasBundle())
                        }
                    }
                }
                hidePopupImmediately()
            }
        }

        try {
            windowManager?.addView(view, params)
            bracketView = view
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
    }

    private fun setBracketExpanded(expanded: Boolean) {
        val view = bracketView ?: return
    }

    private fun showBracketNotificationPopup(packageName: String, contentIntent: PendingIntent?) {
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

    private fun animateCollapse() {
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

    private fun hidePopupImmediately() {
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

    private fun removeBracketView() {
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            // Ignore group summaries to prevent duplicates (Google Messages, WhatsApp, etc. post both a summary and child notifications)
            if ((it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
                return
            }

            val packageName = it.packageName
            val notificationKey = it.key
            
            val postedActions = mutableListOf<Notification.Action>()
            it.notification.actions?.let { a -> postedActions.addAll(a) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val contextual = it.notification.extras.getParcelableArrayList<Notification.Action>("android.contextualActions")
                    contextual?.let { c -> postedActions.addAll(c) }
                } catch (e: ClassCastException) {} catch (e: android.os.BadParcelableException) {}
            }
            actionCache[notificationKey] = postedActions
            contentIntentCache.remove(notificationKey)

            
            val appInfo = supportedApps[packageName]
            val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && it.notification.category == Notification.CATEGORY_CALL) ||
                                        packageName.contains(".dialer", ignoreCase = true) ||
                                        packageName.endsWith(".phone", ignoreCase = true)
            
            val channel = appInfo?.second ?: if (isSystemPhoneFallback) "Phone (Google Dialer)" else null
            
            if (channel != null) {
                // Ignore ongoing notifications (like background services)
                if (it.isOngoing) return@let

                val extras = it.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val timestamp = it.postTime

                // Intercept MessagingStyle self-replies
                val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                var isSelfReply = false
                var replyText = ""

                if (channel != "Snapchat" && messagesArray != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messagesArray)
                        if (msgs.isNotEmpty()) {
                            val lastMsg = msgs.last()
                            val selfDisplayName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()
                            val senderName = lastMsg.senderPerson?.name?.toString() ?: lastMsg.sender?.toString()

                            if (senderName == null || 
                                (selfDisplayName != null && senderName.equals(selfDisplayName, ignoreCase = true)) || 
                                senderName.equals("You", ignoreCase = true)) {
                                isSelfReply = true
                                replyText = lastMsg.text?.toString() ?: ""
                            }
                        }
                    } catch (e: ClassCastException) {
                        android.util.Log.e("Conduit", "Class cast exception", e)
                    } catch (e: IllegalArgumentException) {
                        android.util.Log.e("Conduit", "Illegal argument exception", e)
                    }
                }

                        if (isSelfReply && replyText.isNotEmpty()) {
                    scope.launch {
                        val existing = database.notificationDao().getMostRecentByTitleAndPackage(packageName, title)
                        if (existing != null) {
                            val suffix = "\n\u21aa You: $replyText"
                            val currentText = existing.text ?: ""
                            val currentTitle = existing.title ?: ""
                            
                            val textUpdated = !currentText.endsWith(suffix)
                            val titleUpdated = !currentTitle.endsWith(" - Replied")
                            
                            if (textUpdated || titleUpdated) {
                                val newText = if (textUpdated) currentText + suffix else currentText
                                val newTitle = if (titleUpdated) "$currentTitle - Replied" else currentTitle
                                database.notificationDao().updateAndUnarchive(existing.id, newTitle, newText, timestamp)
                                com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                            }
                        }
                    }
                    return@let // Discard this duplicate system tray post since it's just our own reply!
                }

                // Check custom block rules
                if (shouldBlockNotification(applicationContext, packageName, title, text)) return@let

                // Ignore if it's a background work notification or empty
                if (text.contains("doing work in the background", ignoreCase = true)) return@let
                if (text.contains("updating messages", ignoreCase = true)) return@let
                if (title.isBlank() && text.isBlank()) return@let

                val prefKey = appInfo?.first ?: if (isSystemPhoneFallback) "channel_phone" else null
                val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                val enabled = if (prefKey != null) prefs.getBoolean(prefKey, true) else true
                if (!enabled) return@let

                Log.d("HubService", "Intercepted MSG [$channel]: $title - $text")

                val overrideMap = emptyMap<String, String>()
                var kind = overrideMap[packageName]
                if (kind == null) {
                    val category = it.notification.category
                    val hasMessagingStyle = it.notification.extras.containsKey(Notification.EXTRA_MESSAGES)
                    val hasReply = postedActions.any { a -> a.remoteInputs?.isNotEmpty() == true }
                    val isMessage = hasMessagingStyle || hasReply || category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL
                    val isCall = category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_MISSED_CALL
                    kind = if (isMessage) "MESSAGE" else if (isCall) "CALL" else "OTHER"
                }

                val hubNotification = HubNotification(
                    packageName = packageName,
                    notificationKey = notificationKey,
                    title = title,
                    text = text,
                    channel = channel,
                    timestamp = timestamp,
                    kind = kind
                )

                val contentIntent = it.notification.contentIntent
                if (contentIntent != null) {
                    contentIntentCache[notificationKey] = contentIntent
                }

                scope.launch {
                    notificationMutex.withLock {
                        // Reboot-repost dedup:
                        val unarchivedMatch = database.notificationDao().getUnarchivedExactMatch(packageName, title, text)
                        if (unarchivedMatch != null) {
                            database.notificationDao().adoptRow(unarchivedMatch.id, notificationKey, timestamp, kind)
                            com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                            return@withLock
                        }

                        // Prevent aggressive duplicates when Google Messages changes the notification key
                        val exactMatch = database.notificationDao().getMostRecentExactMatch(packageName, title, text)
                        if (exactMatch != null && (timestamp - exactMatch.timestamp) < 60000) {
                            return@withLock // Exact same message received within 60 seconds, ignore
                        }

                        val existingActive = database.notificationDao().getActiveNotificationByKey(notificationKey)
                        if (existingActive != null) {
                            if (existingActive.title == title && existingActive.text == text) {
                                // Exact duplicate update, ignore
                                database.notificationDao().updateNotificationContentDetailed(existingActive.id, title, text, timestamp, kind)
                                return@withLock
                            } else {
                                // Update existing active notification
                                database.notificationDao().updateNotificationContentDetailed(existingActive.id, title, text, timestamp, kind)
                            }
                        } else {
                            database.notificationDao().insert(hubNotification)
                        }
                        
                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                        
                        // Post native Bubble notification
                        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("enable_bubbles", false)) {
                            postBubbleNotification(applicationContext, packageName, title, text)
                        }

                        // Trigger Bracket popup if enabled
                        if (prefs.getBoolean("enable_bracket", false) && prefs.getBoolean("bracket_notification_popup", true)) {
                            handler.post {
                                showBracketNotificationPopup(packageName, contentIntent)
                            }
                        }

                        // Future-proofing: If enabled, dismiss the original notification to avoid duplication
                        if (DISMISS_ORIGINAL_NOTIFICATIONS) {
                            try {
                                cancelNotification(notificationKey)
                            } catch (e: Exception) {
                                android.util.Log.e("Conduit", "Exception caught", e)
                            }
                        }

                        scheduleUpdatePersistentNotification()
                    }
                }
            }
        }
    }

    private fun shouldBlockNotification(context: Context, packageName: String, title: String, text: String): Boolean {
        val prefs = context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val blockedSet = prefs.getStringSet("blocked_rules", emptySet()) ?: emptySet()
        for (ruleStr in blockedSet) {
            val parts = ruleStr.split("|", limit = 3)
            if (parts.size >= 3) {
                val rulePackage = parts[0]
                val ruleType = parts[1]
                val rulePattern = parts[2]
                
                if (rulePackage == packageName) {
                    if (ruleType == "TITLE" && title.contains(rulePattern, ignoreCase = true)) {
                        return true
                    }
                    if (ruleType == "TEXT" && text.contains(rulePattern, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: NotificationListenerService.RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        sbn?.let {
            actionCache[it.key] = emptyList()
            val prefs = getSharedPreferences("conduit_prefs", android.content.Context.MODE_PRIVATE)
            val syncDismissal = prefs.getBoolean("sync_dismissal", true)
            val autoDismissDetached = prefs.getBoolean("auto_dismiss_detached", true)
            
            val readReasons = setOf(
                REASON_CANCEL, REASON_CANCEL_ALL, REASON_CLICK,
                REASON_APP_CANCEL, REASON_APP_CANCEL_ALL
            )
            
            scope.launch {
                if (reason in readReasons) {
                    if (syncDismissal) {
                        database.notificationDao().archiveNotificationByKey(it.key, System.currentTimeMillis())
                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                    }
                } else {
                    val activeNotification = database.notificationDao().getActiveNotificationByKey(it.key)
                    if (activeNotification != null) {
                        val now = System.currentTimeMillis()
                        database.notificationDao().setDetached(activeNotification.id, now)
                        if (activeNotification.kind == "OTHER" && autoDismissDetached && !activeNotification.isPinned && !activeNotification.isSnoozed) {
                            database.notificationDao().archiveNotification(activeNotification.id, now)
                        }
                        com.conduit.app.widget.WidgetUpdater.updateAllWidgets(this@HubNotificationListenerService)
                    }
                }
            }
        }
        scheduleUpdatePersistentNotification()
    }
    fun cancel(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
    }

    private fun createBubbleNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Conduit Bubbles"
            val descriptionText = "Notifications for floating bubbles"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("conduit_bubble_channel", name, importance).apply {
                description = descriptionText
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowBubbles(true)
                }
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createBubbleShortcut(context: Context, contactName: String, contactKey: String, icon: IconCompat) {
        val person = Person.Builder()
            .setName(contactName)
            .setKey(contactKey)
            .setIcon(icon)
            .build()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("from_bubble", true)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, contactKey)
            .setShortLabel(contactName)
            .setLongLived(true)
            .setIntent(intent)
            .setPerson(person)
            .setIcon(icon)
            .setCategories(java.util.Collections.singleton("android.shortcut.conversation"))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
    fun postBubbleNotification(context: Context, packageName: String, title: String, text: String) {
        val senderName = if (title.isBlank()) "Conduit Hub" else title
        val shortcutId = "conduit_shortcut_" + packageName

        var appIcon: IconCompat? = null
        try {
            val drawable = getAppIcon(context, packageName)
            if (drawable != null) {
                val bitmap = drawable.toBitmap(width = 192, height = 192)
                appIcon = IconCompat.createWithBitmap(bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
        val finalIcon = appIcon ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        createBubbleNotificationChannel(context)
        createBubbleShortcut(context, senderName, shortcutId, finalIcon)

        val isTestBubble = packageName == "com.conduit.app" || title == "Conduit Test Bubble"
        val bubbleMetadata = createBubbleMetadata(shortcutId, suppress = !isTestBubble)

        val person = Person.Builder()
            .setName(senderName)
            .setKey(shortcutId)
            .setIcon(finalIcon)
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(text, System.currentTimeMillis(), person)

        val notificationBuilder = NotificationCompat.Builder(context, "conduit_bubble_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(text)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(style)
            .setBubbleMetadata(bubbleMetadata)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setGroup("com.conduit.app.BUBBLE_GROUP")

        val summaryNotification = NotificationCompat.Builder(context, "conduit_bubble_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Conduit Messages")
            .setContentText("New active conversations")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("com.conduit.app.BUBBLE_GROUP")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(shortcutId.hashCode(), notificationBuilder.build())
            notificationManager.notify(9999, summaryNotification)
        } catch (e: SecurityException) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
    }

    private fun createBubbleMetadata(shortcutId: String, suppress: Boolean = true): NotificationCompat.BubbleMetadata {
        return NotificationCompat.BubbleMetadata.Builder(shortcutId)
            .setDesiredHeight(600)
            .setAutoExpandBubble(true)
            .setSuppressNotification(suppress)
            .build()
    }

    fun snooze(key: String, durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                snoozeNotification(key, durationMs)
            } catch (e: Exception) {
                android.util.Log.e("Conduit", "Exception caught", e)
            }
        }
    }

    fun getReplyAction(key: String): Notification.Action? {
        val actions = getNotificationActions(key) ?: return null
        return actions.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    fun getNotificationActions(key: String): List<Notification.Action>? {
        if (actionCache.containsKey(key)) {
            val cached = actionCache[key]
            return if (cached.isNullOrEmpty()) null else cached
        }

        try {
            val active = activeNotifications
            for (sbn in active) {
                if (sbn.key == key) {
                    val list = mutableListOf<Notification.Action>()
                    sbn.notification.actions?.let { list.addAll(it) }
                    
                    // Add contextual actions if available (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val extras = sbn.notification.extras
                        try {
                            val contextual = extras.getParcelableArrayList<Notification.Action>("android.contextualActions")
                            contextual?.let { list.addAll(it) }
                        } catch (e: Exception) {
                            // Sometimes parceling fails for custom actions
                        }
                    }
                    
                    val result = if (list.isEmpty()) emptyList() else list
                    actionCache[key] = result
                    return if (result.isEmpty()) null else result
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Conduit", "Exception caught", e)
        }
        actionCache[key] = emptyList()
        return null
    }

    fun getCachedContentIntent(key: String): PendingIntent? {
        return contentIntentCache[key]
    }

    private fun openHanger() {
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

    private fun closeHanger() {
        if (!hangerOpened) return
        hangerOpened = false
        hoverIndex = -1
        
        try {
            hangerRootView?.visibility = View.GONE
            windowManager?.removeView(hangerRootView)
        } catch (e: Exception) { android.util.Log.e("Conduit", "Exception caught", e) }
        
        hangerLinearLayout?.removeAllViews()
        pendingIntentList.clear()
        expandedBundlePackageName = null
        handler.removeCallbacks(expandBundleRunnable)
    }

    private fun updateHoverState(x: Float, y: Float) {
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
                if (item is HangerItem.BundleItem && expandedBundlePackageName != item.packageName) {
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

    private fun populateHangerNotifications() {
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
            val isSms = packageName == "com.google.android.apps.messaging"
            val isEmail = notif.category == Notification.CATEGORY_EMAIL || 
                          packageName == "com.readdle.spark" || 
                          packageName == "com.google.android.gm"
            val isSnapchat = packageName == "com.snapchat.android"
            val isLinkedIn = packageName == "com.linkedin.android"
            val isInstagram = packageName == "com.instagram.android"
            val isPhone = packageName == "com.google.android.dialer" ||
                          packageName == "com.android.dialer" ||
                          packageName == "com.samsung.android.dialer" ||
                          packageName == "com.android.phone" ||
                          packageName == "com.android.server.telecom" ||
                          packageName == "com.android.contacts" ||
                          packageName == "com.truecaller"
            val isTelegram = packageName == "org.telegram.messenger"
            val isReddit = packageName == "com.reddit.frontpage"
            val isSteam = packageName == "com.valvesoftware.android.steam.community"

            if (!(isSms || 
                  isEmail || 
                  isSnapchat || 
                  isLinkedIn || 
                  (isInstagram && channelInstagram) || 
                  (isPhone && channelPhone) ||
                  (isTelegram && channelTelegram) ||
                  (isReddit && channelReddit) ||
                  (isSteam && channelSteam))) {
                continue
            }
            filteredNotifs.add(sbn)
        }

        if (groupByChannel) {
            val grouped = filteredNotifs.groupBy { it.packageName }
            for ((pkg, notifs) in grouped) {
                if (notifs.size == 1) {
                    val card = createNotificationCard(notifs[0], density, false)
                    layout.addView(card)
                    pendingIntentList.add(HangerItem.NotificationItem(notifs[0].notification.contentIntent, pkg))
                } else {
                    if (expandedBundlePackageName == pkg) {
                        // Show bundle header
                        val bundleHeader = createBundleCard(pkg, notifs.size, density, true)
                        layout.addView(bundleHeader)
                        pendingIntentList.add(HangerItem.BundleItem(pkg))
                        // Show all items under it
                        for (notif in notifs) {
                            val card = createNotificationCard(notif, density, true)
                            layout.addView(card)
                            pendingIntentList.add(HangerItem.NotificationItem(notif.notification.contentIntent, pkg))
                        }
                    } else {
                        // Collapsed bundle
                        val bundleCard = createBundleCard(pkg, notifs.size, density, false)
                        layout.addView(bundleCard)
                        pendingIntentList.add(HangerItem.BundleItem(pkg))
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

    private fun createNotificationCard(sbn: android.service.notification.StatusBarNotification, density: Float, isIndented: Boolean): View {
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
                    pi.send(this@HubNotificationListenerService, 0, Intent(), null, null, null, options.toBundle())
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

    private fun createBundleCard(packageName: String, count: Int, density: Float, isExpanded: Boolean): View {
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
        var appName = "App"
        try {
            appName = getAppLabel(this, packageName)
            val icon = getAppIcon(this, packageName)
            if (icon != null) {
                appIconView.setImageDrawable(icon)
            }
        } catch (e: Exception) {}
        innerLayout.addView(appIconView)
        
        val titleView = android.widget.TextView(this)
        titleView.text = "$count Notifications from $appName"
        titleView.textSize = 16f
        titleView.setTextColor(if (isNight) Color.WHITE else Color.BLACK)
        titleView.setTypeface(null, android.graphics.Typeface.BOLD)
        titleView.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        innerLayout.addView(titleView)

        val chevron = android.widget.ImageView(this)
        chevron.layoutParams = android.widget.LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
        chevron.setImageResource(if (isExpanded) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
        chevron.setColorFilter(if (isNight) Color.WHITE else Color.BLACK)
        innerLayout.addView(chevron)
        
        card.addView(innerLayout)

        card.setOnClickListener {
            if (isExpanded) {
                expandedBundlePackageName = null
            } else {
                expandedBundlePackageName = packageName
            }
            populateHangerNotifications()
        }

        return card
    }

    fun updatePersistentNotification() {
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("persistent_tray_enabled", false)) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(2001)
            return
        }

        val activeNotifs = activeNotifications.sortedByDescending { it.postTime }
        val filteredNotifs = mutableListOf<android.service.notification.StatusBarNotification>()
        for (sbn in activeNotifs) {
            val packageName = sbn.packageName
            val notif = sbn.notification
            
            val appInfo = supportedApps[packageName]
            val isSystemPhoneFallback = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && notif.category == Notification.CATEGORY_CALL) ||
                                        packageName.contains(".dialer", ignoreCase = true) ||
                                        packageName.endsWith(".phone", ignoreCase = true)
            
            val channelInfo = appInfo ?: if (isSystemPhoneFallback) Pair("channel_phone", "Phone (Google Dialer)") else null
            if (channelInfo != null) {
                val prefKey = channelInfo.first
                if (prefs.getBoolean(prefKey, true)) {
                    filteredNotifs.add(sbn)
                }
            }
        }

        val groupedNotifs = filteredNotifs.groupBy { it.packageName }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (groupedNotifs.isEmpty()) {
            nm.cancel(2001)
            return
        }

        val remoteViews = android.widget.RemoteViews(packageName, R.layout.persistent_notification)
        remoteViews.removeAllViews(R.id.persistent_icon_container)

        val density = resources.displayMetrics.density

        for ((pkg, notifs) in groupedNotifs) {
            val count = notifs.size
            val pm = packageManager
            val appIcon = getAppIcon(this, pkg) ?: continue
            
            val size = (48 * density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            appIcon.setBounds(0, (8 * density).toInt(), (40 * density).toInt(), (48 * density).toInt())
            appIcon.draw(canvas)
            
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.RED
            val cx = size - (12 * density)
            val cy = (12 * density)
            canvas.drawCircle(cx, cy, (10 * density), paint)
            
            paint.color = android.graphics.Color.WHITE
            paint.textSize = (12 * density)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val textY = cy - ((paint.descent() + paint.ascent()) / 2)
            canvas.drawText(count.toString(), cx, textY, paint)

            val childView = android.widget.RemoteViews(packageName, R.layout.persistent_notification_icon)
            childView.setImageViewBitmap(R.id.app_icon, bitmap)
            
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "com.conduit.app.OPEN_FILTER"
                putExtra("FILTER_PACKAGE", pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(this, pkg.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            childView.setOnClickPendingIntent(R.id.app_icon, pi)
            
            remoteViews.addView(R.id.persistent_icon_container, childView)
        }

        val channelId = "persistent_tray_default"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Persistent Tray", NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            
        nm.notify(2001, builder.build())
    }
}

