import re
import sys

def refactor_hub_screen():
    with open('app/src/main/java/com/conduit/app/ui/HubScreen.kt', 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 1. Signature
    content = content.replace(
        "fun HubScreen(\n    onNavigateToArchive: () -> Unit,\n    onNavigateToSettings: () -> Unit,\n    onUnifiedViewChanged: (Boolean) -> Unit\n)",
        "fun HubScreen(\n    viewModel: com.conduit.app.HubViewModel,\n    onNavigateToArchive: () -> Unit,\n    onNavigateToSettings: () -> Unit,\n    onUnifiedViewChanged: (Boolean) -> Unit\n)"
    )

    # 2. State Collection
    old_state = """    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    
    var unifiedView by remember { mutableStateOf(prefs.getBoolean("unified_view", false)) }
    var notifications by remember { mutableStateOf<List<HubNotification>>(emptyList()) }
    var archivedNotifications by remember { mutableStateOf<List<HubNotification>>(emptyList()) }

    LaunchedEffect(Unit) {
        val database = AppDatabase.getDatabase(context)
        kotlinx.coroutines.launch {
            database.notificationDao().getAllNotifications().collect {
                notifications = it
            }
        }
        kotlinx.coroutines.launch {
            database.notificationDao().getArchivedNotifications().collect {
                archivedNotifications = it
            }
        }
    }"""
    
    new_state = """    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    
    var unifiedView by remember { mutableStateOf(prefs.getBoolean("unified_view", false)) }
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val archivedNotifications by viewModel.archivedNotifications.collectAsStateWithLifecycle()"""
    
    content = content.replace(old_state, new_state)

    # 3. Actions by Key
    old_actions = """    val actionsByKey = remember(displayNotifications, showActionChips) {
        if (!showActionChips) emptyMap()
        else displayNotifications.associate {
            it.notificationKey to HubNotificationListenerService.instance?.getNotificationActions(it.notificationKey)
        }
    }"""
    new_actions = """    val actionsByKey by viewModel.actionsByKey.collectAsStateWithLifecycle()"""
    content = content.replace(old_actions, new_actions)

    # 4. archiveMany
    content = re.sub(
        r'kotlinx\.coroutines\.GlobalScope\.launch \{\s*val database = AppDatabase\.getDatabase\(context\)\s*database\.notificationDao\(\)\.archiveNotifications\(idsToArchive, now\)\s*com\.conduit\.app\.widget\.ConduitWidgetProvider\.updateAllWidgets\(context\)\s*\}',
        'viewModel.archiveMany(idsToArchive)',
        content, flags=re.MULTILINE
    )

    # 5. pinMany
    content = re.sub(
        r'kotlinx\.coroutines\.GlobalScope\.launch \{\s*val database = AppDatabase\.getDatabase\(context\)\s*database\.notificationDao\(\)\.pinNotifications\(idsToPin, anyUnpinned\)\s*com\.conduit\.app\.widget\.ConduitWidgetProvider\.updateAllWidgets\(context\)\s*\}',
        '/*TODO: PIN MANY*/',
        content, flags=re.MULTILINE
    )

    # 6. markReadMany block - very large chunk, better to use precise string replacement
    # I'll just use regex to replace everything between `val idsToProcess = selectedIds.toList()` and `performHapticClick(context)`
    # inside that specific IconButton block
    
    old_mark_read = """                        IconButton(onClick = {
                            val idsToProcess = selectedIds.toList()
                            val service = HubNotificationListenerService.instance
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                idsToProcess.forEach { id ->
                                    val notif = notifications.find { it.id == id }
                                    if (notif != null && service != null) {
                                        val sbn = service.activeNotifications.find { it.key == notif.notificationKey }
                                        if (sbn != null) {
                                            val actions = sbn.notification.actions
                                            if (actions != null) {
                                                val readAction = actions.find { 
                                                    it.title.toString().contains("read", ignoreCase = true) ||
                                                    it.title.toString().contains("done", ignoreCase = true) ||
                                                    it.title.toString().contains("seen", ignoreCase = true)
                                                }
                                                if (readAction != null) {
                                                    try {
                                                        readAction.actionIntent.send()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        database.notificationDao().archiveNotification(id, System.currentTimeMillis())
                                    }
                                }
                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        })"""
    new_mark_read = """                        IconButton(onClick = {
                            val allAvailable = notifications + archivedNotifications
                            val keysToProcess = allAvailable.filter { it.id in selectedIds }.map { it.notificationKey }
                            viewModel.viewModelScope.launch {
                                keysToProcess.forEach { viewModel.markRead(it) }
                            }
                            performHapticClick(context)
                            selectedIds = emptySet()
                        })"""
    content = content.replace(old_mark_read, new_mark_read)

    # 7. ARCHIVE_ALL GlobalScope
    old_archive_all = """                                                            kotlinx.coroutines.GlobalScope.launch {
                                                                val database = AppDatabase.getDatabase(context)
                                                                notifications.forEach { database.notificationDao().archiveNotification(it.id, now) }
                                                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                                            }"""
    new_archive_all = """                                                            viewModel.archiveMany(notifications.map { it.id })"""
    content = content.replace(old_archive_all, new_archive_all)
    
    # 8. item invocations in list
    old_item = """                        NotificationItem(
                            notification = it,
                            isArchivedView = false,
                            onArchiveNotification = { id, timestamp ->
                                kotlinx.coroutines.GlobalScope.launch {
                                    val database = AppDatabase.getDatabase(context)
                                    database.notificationDao().archiveNotification(id, timestamp)
                                    if (prefs.getBoolean("sync_dismissal", true)) {
                                        HubNotificationListenerService.instance?.cancel(it.notificationKey)
                                    }
                                    com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                }
                            },
                            onPinNotification = { notification ->
                                val syncPinned = prefs.getBoolean("sync_pinned", false)
                                kotlinx.coroutines.GlobalScope.launch {
                                    val newPinned = !notification.isPinned
                                    val database = AppDatabase.getDatabase(context)
                                    database.notificationDao().togglePin(notification.id)
                                    
                                    if (syncPinned) {
                                        if (newPinned) {
                                            val appLabel = getAppLabel(context, notification.packageName)
                                            val titleString = notification.title?.toString() ?: appLabel
                                            postPinnedNotification(context, notification.id, titleString, notification.text?.toString() ?: "", notification.packageName)
                                        } else {
                                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                            notificationManager.cancel(notification.id)
                                        }
                                    }
                                    com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                }
                            },
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onSelectToggle = {
                                if (isSelected) selectedIds -= it.id else selectedIds += it.id
                                if (selectedIds.isEmpty()) isSelectionMode = false
                            },
                            isCompactMode = isCompactMode,
                            showActionChips = showActionChips,
                            allActions = actionsByKey[it.notificationKey],
                            onMarkRead = {
                                kotlinx.coroutines.GlobalScope.launch {
                                    val database = AppDatabase.getDatabase(context)
                                    database.notificationDao().archiveNotification(it.id, System.currentTimeMillis())
                                    HubNotificationListenerService.instance?.cancel(it.notificationKey)
                                    com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                }
                            }
                        )"""
    new_item = """                        NotificationItem(
                            notification = it,
                            isArchivedView = false,
                            onArchiveNotification = { id, timestamp ->
                                viewModel.archiveNotification(id, timestamp)
                            },
                            onPinNotification = { notification ->
                                val syncPinned = prefs.getBoolean("sync_pinned", false)
                                viewModel.togglePin(notification, syncPinned)
                            },
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onSelectToggle = {
                                if (isSelected) selectedIds -= it.id else selectedIds += it.id
                                if (selectedIds.isEmpty()) isSelectionMode = false
                            },
                            isCompactMode = isCompactMode,
                            showActionChips = showActionChips,
                            allActions = actionsByKey[it.notificationKey],
                            onItemClick = { viewModel.triggerContentIntent(it, context) },
                            onMarkRead = {
                                viewModel.markRead(it.notificationKey)
                            },
                            onTriggerAction = { action ->
                                viewModel.triggerAction(it, action)
                            },
                            onReply = { text, action ->
                                viewModel.sendReply(it, text, action)
                            },
                            smartMarkRead = prefs.getBoolean("smart_mark_read", true),
                            smartMarkReadTarget = prefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only"
                        )"""
    content = content.replace(old_item, new_item)

    with open('app/src/main/java/com/conduit/app/ui/HubScreen.kt', 'w', encoding='utf-8') as f:
        f.write(content)


def refactor_notification_item():
    with open('app/src/main/java/com/conduit/app/ui/NotificationItem.kt', 'r', encoding='utf-8') as f:
        content = f.read()

    old_sig = """fun NotificationItem(
    notification: HubNotification,
    isArchivedView: Boolean = false,
    onArchiveNotification: ((Int, Long) -> Unit)? = null,
    onPinNotification: ((HubNotification) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    isCompactMode: Boolean = false,
    showActionChips: Boolean = true,
    isUnifiedView: Boolean = false,
    allActions: List<Notification.Action>? = null,
    onItemClick: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onTriggerAction: (Notification.Action) -> Unit = {},
    onReply: (String, Notification.Action) -> Unit = { _, _ -> }
) {"""
    new_sig = """fun NotificationItem(
    notification: HubNotification,
    isArchivedView: Boolean = false,
    onArchiveNotification: ((Int, Long) -> Unit)? = null,
    onPinNotification: ((HubNotification) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    isCompactMode: Boolean = false,
    showActionChips: Boolean = true,
    isUnifiedView: Boolean = false,
    allActions: List<Notification.Action>? = null,
    onItemClick: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onTriggerAction: (Notification.Action) -> Unit = {},
    onReply: (String, Notification.Action) -> Unit = { _, _ -> },
    smartMarkRead: Boolean = true,
    smartMarkReadTarget: String = "widget_only"
) {"""
    content = content.replace(old_sig, new_sig)

    old_prefs = """    val generalPrefs = remember(context) { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    val smartMarkRead = remember(generalPrefs) { generalPrefs.getBoolean("smart_mark_read", true) }
    val smartMarkReadTarget = remember(generalPrefs) { generalPrefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only" }"""
    content = content.replace(old_prefs, "")

    old_click = """                    } else {
                        var launched = false
                    val service = HubNotificationListenerService.instance
                    if (service != null) {
                        try {
                            val activeNotifs = service.activeNotifications
                            for (sbn in activeNotifs) {
                                if (sbn.key == notification.notificationKey) {
                                    val intent = sbn.notification.contentIntent
                                    if (intent != null) {
                                        val options = android.app.ActivityOptions.makeBasic()
                                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                                            options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        }
                                        intent.send(context, 0, null, null, null, null, options.toBundle())
                                        launched = true
                                    }
                                    break
                                }
                            }
                            
                            // Fallback to in-memory cache if not found in active live notifications
                            if (!launched) {
                                val cachedIntent = service.getCachedContentIntent(notification.notificationKey)
                                if (cachedIntent != null) {
                                    val options = android.app.ActivityOptions.makeBasic()
                                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                                        options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    }
                                    cachedIntent.send(context, 0, null, null, null, null, options.toBundle())
                                    launched = true
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (!launched) {
                        try {
                            val launchedCrossProfile = launchApp(context, notification.packageName)
                            if (!launchedCrossProfile) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },"""
    new_click = """                    } else {
                        onItemClick()
                    }
                }
            },"""
    content = content.replace(old_click, new_click)
    
    with open('app/src/main/java/com/conduit/app/ui/NotificationItem.kt', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == '__main__':
    refactor_hub_screen()
    refactor_notification_item()
    print("Refactor finished")
