package com.conduit.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.conduit.app.*
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HubScreen(
    channelStates: Map<String, Boolean>,
    notifications: List<HubNotification>,
    archivedNotifications: List<HubNotification>,
    fabConfigs: List<FabAction>,
    onSaveFabConfigs: (List<FabAction>) -> Unit,
    aiBundle: List<String>,
    notesBundle: List<String>,
    recorderBundle: List<String>,
    composeBundle: List<String>,
    onNavigateToSettings: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onArchiveNotification: (Int, Long) -> Unit,
    onSnoozeNotification: (Int, Long) -> Unit,
    onPinNotification: (HubNotification) -> Unit,
    swipeLeftAction: String,
    swipeRightAction: String,
    dockSizeIndex: Int,
    enableBubbles: Boolean,
    enableBracket: Boolean,
    bracketNotificationPopup: Boolean,
    bracketHangerEnabled: Boolean,
    bracketVerticalPosition: Float,
    onUpdateBracketPosition: (Float) -> Unit,
    unifiedView: Boolean,
    onUnifiedViewChanged: (Boolean) -> Unit,
    showActionChips: Boolean,
    smartMarkRead: Boolean,
    smartMarkReadTarget: String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var selectedWorkProfileOnly by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isTodoMode by remember { mutableStateOf(false) }

    var notificationToSnooze by remember { mutableStateOf<HubNotification?>(null) }
    var notificationToBlock by remember { mutableStateOf<HubNotification?>(null) }
    var notificationToReply by remember { mutableStateOf<Pair<HubNotification, android.app.Notification.Action>?>(null) }
    var replyText by remember { mutableStateOf("") }

    var showMenuBundle by remember { mutableStateOf<String?>(null) }
    var showCustomizeFab by remember { mutableStateOf<FabAction?>(null) }
    var showAddFabDialog by remember { mutableStateOf(false) }
    
    var fabExpanded by remember { mutableStateOf(false) }

    val actionsByKey = remember(notifications, showActionChips) {
        if (!showActionChips) {
            emptyMap()
        } else {
            notifications.associate { n ->
                n.notificationKey to (HubNotificationListenerService.instance?.getNotificationActions(n.notificationKey) ?: emptyList())
            }
        }
    }

    val isSelectionMode = selectedIds.isNotEmpty()

    val filteredNotifications = remember(notifications, channelStates.toMap()) {
        notifications.filter {
            val prefKey = HubNotificationListenerService.supportedApps.values.find { appInfo -> appInfo.second == it.channel }?.first
            if (prefKey != null) {
                channelStates[prefKey] ?: true
            } else true
        }
    }

    val activeChannelFiltered = remember(filteredNotifications, selectedChannel, selectedWorkProfileOnly, searchQuery) {
        var list = filteredNotifications
        if (selectedWorkProfileOnly) {
            list = list.filter { isWorkProfilePackage(context, it.packageName) }
        } else if (selectedChannel != null) {
            list = list.filter {
                val repPkg = getRepresentativePackage(context, it.packageName)
                val targetRep = getRepresentativePackage(context, selectedChannel!!)
                repPkg == targetRep
            }
        }
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            list = list.filter {
                (it.title?.lowercase()?.contains(query) == true) ||
                (it.text?.lowercase()?.contains(query) == true) ||
                (it.channel.lowercase().contains(query)) ||
                (it.packageName.lowercase().contains(query))
            }
        }
        list
    }

    val pinnedNotifications = remember(activeChannelFiltered) {
        activeChannelFiltered.filter { it.isPinned }
    }

    val mainListNotifications = remember(activeChannelFiltered) {
        activeChannelFiltered.filter { !it.isPinned }
    }

    val unreadChannels = remember(filteredNotifications) {
        val workMap = mutableMapOf<String, Boolean>()
        val channelMap = mutableMapOf<String, String>()
        val counts = mutableMapOf<String, Int>()
        var workCount = 0

        filteredNotifications.forEach { notification ->
            val repPkg = getRepresentativePackage(context, notification.packageName)
            val isWork = isWorkProfilePackage(context, notification.packageName)
            
            if (isWork) {
                workCount++
                workMap[repPkg] = true
            }
            
            if (!channelMap.containsKey(repPkg)) {
                channelMap[repPkg] = notification.channel
            }
            counts[repPkg] = (counts[repPkg] ?: 0) + 1
        }

        channelMap.keys.map { repPkg ->
            Triple(repPkg, counts[repPkg] ?: 0, workMap[repPkg] ?: false)
        }.sortedByDescending { it.second } to workCount
    }

    val channelList = unreadChannels.first
    val totalWorkUnreadCount = unreadChannels.second

    fun advanceTodoToNextApp() {
        if (channelList.isNotEmpty()) {
            val currentIndex = channelList.indexOfFirst { 
                val targetRep = getRepresentativePackage(context, selectedChannel ?: "")
                it.first == targetRep 
            }
            if (currentIndex != -1 && currentIndex < channelList.size - 1) {
                selectedChannel = channelList[currentIndex + 1].first
            } else if (channelList.isNotEmpty()) {
                selectedChannel = channelList[0].first
            }
        } else {
            selectedChannel = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search notifications...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Conduit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "BETA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Search")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { isTodoMode = !isTodoMode }) {
                                Icon(
                                    if (isTodoMode) Icons.Filled.DynamicFeed else Icons.Filled.Checklist,
                                    contentDescription = if (isTodoMode) "Exit Todo Mode" else "Enter Todo Mode"
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )

                if (isSelectionMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedIds = emptySet() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row {
                                IconButton(onClick = {
                                    val idsToProcess = selectedIds.toList()
                                    kotlinx.coroutines.GlobalScope.launch {
                                        val database = AppDatabase.getDatabase(context)
                                        idsToProcess.forEach { id ->
                                            if (prefs.getBoolean("sync_dismissal", true)) {
                                                val n = notifications.find { it.id == id }
                                                if (n != null) {
                                                    HubNotificationListenerService.instance?.cancel(n.notificationKey)
                                                }
                                            }
                                            database.notificationDao().archiveNotification(id, System.currentTimeMillis())
                                        }
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                    performHapticClick(context)
                                    selectedIds = emptySet()
                                }) {
                                    Icon(
                                        imageVector = if (unifiedView) Icons.Filled.Check else Icons.Filled.Archive,
                                        contentDescription = if (unifiedView) "Mark Selected Read" else "Clear Selected",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    fabConfigs.forEach { fab ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clickable {
                                    fabExpanded = false
                                    if (fab.type == "MENU") {
                                        showMenuBundle = fab.target
                                    } else if (fab.type == "APP") {
                                        val intent = context.packageManager.getLaunchIntentForPackage(fab.target)
                                        if (intent != null) context.startActivity(intent)
                                    }
                                }
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = fab.label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    if (fab.type == "MENU") {
                                        showMenuBundle = fab.target
                                    } else if (fab.type == "APP") {
                                        val intent = context.packageManager.getLaunchIntentForPackage(fab.target)
                                        if (intent != null) context.startActivity(intent)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(getFabIcon(fab.iconName), contentDescription = fab.label)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "Actions"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTodoMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TODO MODE ACTIVE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!isTodoMode && channelList.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedChannel == null && !selectedWorkProfileOnly,
                            onClick = { 
                                selectedChannel = null
                                selectedWorkProfileOnly = false
                            },
                            label = { Text("All (${filteredNotifications.size})") }
                        )
                    }

                    if (totalWorkUnreadCount > 0) {
                        item {
                            FilterChip(
                                selected = selectedWorkProfileOnly,
                                onClick = {
                                    selectedWorkProfileOnly = !selectedWorkProfileOnly
                                    selectedChannel = null
                                },
                                label = { Text("Work ($totalWorkUnreadCount)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Work, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }

                    items(channelList, key = { it.first }) { (repPkg, count, isWork) ->
                        val isSelected = selectedChannel == repPkg && !selectedWorkProfileOnly
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedWorkProfileOnly = false
                                selectedChannel = if (isSelected) null else repPkg
                            },
                            label = { Text("$count") },
                            leadingIcon = {
                                AppIcon(packageName = repPkg, size = 20.dp)
                            }
                        )
                    }
                }
            }

            val isChannelFilterActive = (selectedChannel != null || selectedWorkProfileOnly) && !isTodoMode
            if (isChannelFilterActive && mainListNotifications.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val idsToProcess = mainListNotifications.map { it.id }
                            kotlinx.coroutines.GlobalScope.launch {
                                val database = AppDatabase.getDatabase(context)
                                idsToProcess.forEach { id ->
                                    if (prefs.getBoolean("sync_dismissal", true)) {
                                        val n = notifications.find { it.id == id }
                                        if (n != null) {
                                            HubNotificationListenerService.instance?.cancel(n.notificationKey)
                                        }
                                    }
                                    database.notificationDao().archiveNotification(id, System.currentTimeMillis())
                                }
                                com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                            }
                            performHapticClick(context)
                        }
                    ) {
                        Icon(
                            imageVector = if (unifiedView) Icons.Filled.DoneAll else Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (unifiedView) "Mark All Read" else "Clear All")
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (activeChannelFiltered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching notifications." else "All caught up!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val groupedNotifications = remember(activeChannelFiltered) {
                        activeChannelFiltered.groupBy { formatDateHeader(it.timestamp) }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (pinnedNotifications.isNotEmpty()) {
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PINNED",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            items(pinnedNotifications, key = { "pinned_${it.id}" }) { notification ->
                                Box(modifier = Modifier.animateItem(
                                    placementSpec = tween(durationMillis = 300)
                                )) {
                                    NotificationItem(
                                        notification = notification,
                                        isArchivedView = false,
                                        isUnifiedView = unifiedView,
                                        onArchiveNotification = onArchiveNotification,
                                        onPinNotification = onPinNotification,
                                        isSelected = selectedIds.contains(notification.id),
                                        isSelectionMode = isSelectionMode,
                                        onSelectToggle = {
                                            val becomingSelected = !selectedIds.contains(notification.id)
                                            if (becomingSelected) performHapticClick(context) else performHapticTick(context)
                                            selectedIds = if (becomingSelected) {
                                                selectedIds + notification.id
                                            } else {
                                                selectedIds - notification.id
                                            }
                                        },
                                        isCompactMode = false,
                                        showActionChips = showActionChips,
                                        allActions = actionsByKey[notification.notificationKey]
                                    )
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                }
                            }
                        }

                        groupedNotifications.forEach { (dateHeader, itemsList) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dateHeader,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val pendingCount = itemsList.count { notifications.contains(it) }
                                        val chipText = if (pendingCount > 0) "${itemsList.size} | UNREAD $pendingCount" else "${itemsList.size}"
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = chipText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            items(itemsList, key = { it.id }) { notification ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        val action = if (value == SwipeToDismissBoxValue.StartToEnd) swipeRightAction else swipeLeftAction
                                        when (action) {
                                            "ARCHIVE" -> {
                                                if (prefs.getBoolean("sync_dismissal", true)) {
                                                    HubNotificationListenerService.instance?.cancel(notification.notificationKey)
                                                }
                                                onArchiveNotification(notification.id, System.currentTimeMillis())
                                                if (isTodoMode) advanceTodoToNextApp()
                                                !unifiedView
                                            }
                                            "SNOOZE" -> {
                                                notificationToSnooze = notification
                                                false
                                            }
                                            "PIN" -> {
                                                onPinNotification(notification)
                                                false
                                            }
                                            "BLOCK" -> {
                                                notificationToBlock = notification
                                                false
                                            }
                                            else -> false
                                        }
                                    },
                                    positionalThreshold = { distance -> distance * 0.6f }
                                )
                                
                                LaunchedEffect(dismissState.targetValue) {
                                    if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                        performHapticTick(context)
                                    }
                                }
                                
                                SwipeToDismissBox(
                                    state = dismissState,
                                    modifier = Modifier.animateItem(
                                        placementSpec = tween(durationMillis = 300)
                                    ),
                                    backgroundContent = {
                                        val direction = dismissState.dismissDirection
                                        if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
                                        val action = if (direction == SwipeToDismissBoxValue.StartToEnd) swipeRightAction else swipeLeftAction
                                        
                                        val color = when (action) {
                                            "ARCHIVE" -> MaterialTheme.colorScheme.primaryContainer
                                            "SNOOZE" -> Color(0xFFFF9800)
                                            "PIN" -> MaterialTheme.colorScheme.secondaryContainer
                                            "BLOCK" -> MaterialTheme.colorScheme.errorContainer
                                            else -> Color.Gray
                                        }
                                        
                                        val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                        
                                        val icon = when (action) {
                                            "ARCHIVE" -> if (unifiedView) Icons.Filled.Check else Icons.Filled.Archive
                                            "SNOOZE" -> Icons.Filled.Schedule
                                            "PIN" -> Icons.Filled.PushPin
                                            "BLOCK" -> Icons.Filled.Block
                                            else -> Icons.Filled.Delete
                                        }
                                        
                                        val label = when (action) {
                                            "ARCHIVE" -> if (unifiedView) "Mark Read" else "Clear"
                                            "SNOOZE" -> "Snooze"
                                            "PIN" -> if (notification.isPinned) "Unpin" else "Pin"
                                            "BLOCK" -> "Block"
                                            else -> ""
                                        }
                                        
                                        val textColor = when (action) {
                                            "ARCHIVE" -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "SNOOZE" -> Color.White
                                            "PIN" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            "BLOCK" -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> Color.White
                                        }
                                        
                                        val scale by animateFloatAsState(
                                            if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1.25f
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(color)
                                                .padding(horizontal = 24.dp),
                                            contentAlignment = alignment
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    icon,
                                                    contentDescription = null,
                                                    tint = textColor,
                                                    modifier = Modifier.scale(scale).size(28.dp)
                                                )
                                                Text(
                                                    label,
                                                    color = textColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.scale(scale)
                                                )
                                            }
                                        }
                                    },
                                    content = {
                                        Column {
                                            NotificationItem(
                                                notification = notification,
                                                isArchivedView = false,
                                                isUnifiedView = unifiedView,
                                                onArchiveNotification = onArchiveNotification,
                                                onPinNotification = onPinNotification,
                                                isSelected = selectedIds.contains(notification.id),
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    val becomingSelected = !selectedIds.contains(notification.id)
                                                    if (becomingSelected) performHapticClick(context) else performHapticTick(context)

                                                    selectedIds = if (becomingSelected) {
                                                        selectedIds + notification.id
                                                    } else {
                                                        selectedIds - notification.id
                                                    }
                                                },
                                                isCompactMode = false,
                                                showActionChips = showActionChips,
                                                allActions = actionsByKey[notification.notificationKey]
                                            )
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isTodoMode && channelList.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        items(channelList, key = { it.first }) { (repPkg, count, isWork) ->
                            val isSelected = selectedChannel == repPkg
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedChannel = if (isSelected) null else repPkg
                                },
                                label = { Text("$count") },
                                leadingIcon = {
                                    AppIcon(packageName = repPkg, size = 20.dp)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (notificationToSnooze != null) {
        val n = notificationToSnooze!!
        AlertDialog(
            onDismissRequest = { notificationToSnooze = null },
            title = { Text("Snooze Notification") },
            text = { Text("How long would you like to snooze '${n.title ?: "this notification"}'?") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        onSnoozeNotification(n.id, System.currentTimeMillis() + 1 * 3600 * 1000)
                        notificationToSnooze = null
                    }) { Text("1 Hour") }
                    TextButton(onClick = {
                        onSnoozeNotification(n.id, System.currentTimeMillis() + 4 * 3600 * 1000)
                        notificationToSnooze = null
                    }) { Text("4 Hours") }
                    TextButton(onClick = {
                        onSnoozeNotification(n.id, System.currentTimeMillis() + 24 * 3600 * 1000)
                        notificationToSnooze = null
                    }) { Text("Tomorrow") }
                }
            },
            dismissButton = {
                TextButton(onClick = { notificationToSnooze = null }) { Text("Cancel") }
            }
        )
    }

    if (notificationToBlock != null) {
        val n = notificationToBlock!!
        AlertDialog(
            onDismissRequest = { notificationToBlock = null },
            title = { Text("Block Channel") },
            text = { Text("Are you sure you want to disable notifications for '${n.channel}'?") },
            confirmButton = {
                TextButton(onClick = {
                    val prefKey = HubNotificationListenerService.supportedApps.values.find { it.second == n.channel }?.first
                    if (prefKey != null) {
                        prefs.edit().putBoolean(prefKey, false).apply()
                    }
                    notificationToBlock = null
                }) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { notificationToBlock = null }) { Text("Cancel") }
            }
        )
    }

    if (notificationToReply != null) {
        val (n, act) = notificationToReply!!
        AlertDialog(
            onDismissRequest = { notificationToReply = null; replyText = "" },
            title = { Text("Reply to ${n.title ?: "Notification"}") },
            text = {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sendReply(context, n, replyText, act)
                    notificationToReply = null
                    replyText = ""
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { notificationToReply = null; replyText = "" }) { Text("Cancel") }
            }
        )
    }
}
