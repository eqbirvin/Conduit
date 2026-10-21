package com.conduit.app

import com.conduit.app.HubNotificationListenerService.HangerItem
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.provider.Settings
import android.app.PendingIntent

    internal fun HubNotificationListenerService.updateBracketPosition() {
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

    internal fun HubNotificationListenerService.updateBracketState() {
        val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
        val enableBracket = prefs.getBoolean("enable_bracket", false)
        val canDrawOverlays = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        if (enableBracket && canDrawOverlays) {
            addBracketView()
        } else {
            removeBracketView()
        }
    }

    internal fun HubNotificationListenerService.addBracketView() {
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

            val navBg = android.view.View(this@addBracketView)
            navBg.setBackgroundColor(Color.BLACK)
            addView(navBg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
            
            setOnApplyWindowInsetsListener { _, insets ->
                val navHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                val lp = navBg.layoutParams
                if (lp.height != navHeight) {
                    lp.height = navHeight
                    navBg.layoutParams = lp
                }
                insets
            }
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
                                        item.pendingIntent.send(this@addBracketView, 0, null, null, null, null, getBasBundle())
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
                                pendingIntent.send(this@addBracketView, 0, null, null, null, null, getBasBundle())
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
                    pendingIntent.send(this@addBracketView, 0, null, null, null, null, getBasBundle())
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
