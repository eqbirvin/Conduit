@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.conduit.app.ui

import com.conduit.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.Icons
import androidx.compose.animation.*
import android.app.Notification
import android.app.RemoteInput
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.conduit.app.data.AppDatabase
import com.conduit.app.data.HubNotification
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.enableEdgeToEdge

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notification: HubNotification,
    isArchivedView: Boolean = false,
    onArchiveNotification: ((Int, Long) -> Unit)? = null,
    onPinNotification: ((HubNotification) -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    isCompactMode: Boolean = false,
    showActionChips: Boolean = true,
    minimizeIcons: Boolean = false,
    isUnifiedView: Boolean = false,
    allActions: List<Notification.Action>? = null,
    onTriggerAction: (Notification.Action) -> Unit = {},
    onReply: (String, Notification.Action) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val replyAction = remember(allActions) {
        allActions?.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
    }

    val generalPrefs = remember(context) { context.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE) }
    val smartMarkRead = remember(generalPrefs) { generalPrefs.getBoolean("smart_mark_read", true) }
    val smartMarkReadTarget = remember(generalPrefs) { generalPrefs.getString("smart_mark_read_target", "widget_only") ?: "widget_only" }

    val hasNativeMarkRead = remember(allActions) {
        allActions?.any { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            title.contains("read") || title.contains("done") || title.contains("clear") || title.contains("dismiss") || title.contains("archive")
        } ?: false
    }

    val showConduitMarkRead = showActionChips && !isCompactMode && !isArchivedView && !notification.isArchived &&
            smartMarkRead && smartMarkReadTarget == "widget_and_app" && !hasNativeMarkRead

    val hasChips = (allActions != null && allActions.isNotEmpty()) || showConduitMarkRead

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else if (notification.isArchived) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
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
                            android.util.Log.e("Conduit", "Exception caught", e)
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
                            android.util.Log.e("Conduit", "Exception caught", e)
                        }
                    }
                }
            },
                onLongClick = {
                    if (!isSelectionMode) {
                        performHapticClick(context)
                        onPinNotification?.invoke(notification)
                    }
                }
            )
    ) {
        var appName by remember(notification.packageName) { mutableStateOf(appLabelCache[notification.packageName] ?: notification.channel) }
        
        LaunchedEffect(notification.packageName) {
            if (!appLabelCache.containsKey(notification.packageName)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val label = getAppLabel(context, notification.packageName, notification.channel)
                    appName = label
                }
            }
        }
        
        val replyPrimaryColor = MaterialTheme.colorScheme.primary
        val annotatedText = remember(notification.text, replyPrimaryColor) {
            val fullText = notification.text ?: ""
            buildAnnotatedString {
                val lines = fullText.split("\n")
                lines.forEachIndexed { index, line ->
                    if (index > 0) append("\n")
                    if (line.startsWith("\u21aa You:")) {
                        val youPrefix = "\u21aa You:"
                        val messagePart = line.substring(youPrefix.length)
                        withStyle(style = SpanStyle(color = replyPrimaryColor, fontWeight = FontWeight.Bold)) {
                            append(youPrefix)
                        }
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(messagePart)
                        }
                    } else {
                        append(line)
                    }
                }
            }
        }

        val AvatarBlock = @Composable {
            val avatarSize = if (isCompactMode) 36.dp else if (minimizeIcons) 24.dp else 50.dp
            val avatarBoxWidth = if (isCompactMode) 44.dp else if (minimizeIcons) 24.dp else 60.dp
            Box(
                modifier = Modifier
                    .width(avatarBoxWidth)
                    .padding(top = if (minimizeIcons && !isCompactMode) 0.dp else if (isCompactMode) 4.dp else 8.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(bounded = false, radius = 32.dp),
                        enabled = true
                    ) { onSelectToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(avatarSize)
                    )
                } else {
                    val channelUpper = notification.channel.uppercase(java.util.Locale.ROOT)
                    if (channelUpper == "SMS" || channelUpper == "GOOGLE MESSAGES" ||
                        channelUpper == "EMAIL" || channelUpper == "GMAIL" || channelUpper == "SPARK" || channelUpper == "OUTLOOK" ||
                        channelUpper == "SNAPCHAT" ||
                        channelUpper == "LINKEDIN" ||
                        channelUpper == "INSTAGRAM" ||
                        channelUpper == "PHONE" || channelUpper == "SYSTEM PHONE" || channelUpper == "PHONE (GOOGLE DIALER)" || channelUpper == "TRUECALLER" ||
                        channelUpper == "TELEGRAM" || channelUpper == "TELEGRAM X" ||
                        channelUpper == "REDDIT" ||
                        channelUpper == "STEAM" ||
                        channelUpper == "FACEBOOK" || channelUpper == "MESSENGER" ||
                        channelUpper == "TWITTER (X)" || channelUpper == "MICROSOFT TEAMS"
                    ) {
                        AppIcon(notification.packageName, size = avatarSize)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = notification.title?.firstOrNull()?.uppercase() ?: "M"
                            Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = if (isCompactMode) 14.sp else if (minimizeIcons) 12.sp else 20.sp)
                        }
                    }
                }
            }
        }

        val HeaderTitleBlock = @Composable {
            Column {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (notification.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (notification.isArchived && !isArchivedView) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    if (isUnifiedView) "READ" else "ARCHIVED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = notification.title ?: "Unknown",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (notification.isPinned) formatTimestampWithDate(notification.timestamp) else formatTimestamp(notification.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isArchivedView && notification.archivedTimestamp != null) {
                    val label = if (notification.isSnoozed) "Snoozed: " else "Read: "
                    val color = if (notification.isSnoozed) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = label + formatTimestamp(notification.archivedTimestamp),
                        fontSize = 11.sp,
                        color = color
                    )
                }
                
                Text(
                    text = appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val BodyAndChipsBlock = @Composable {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = annotatedText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isCompactMode) 2 else 6,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (showActionChips && !isCompactMode && !isArchivedView && !notification.isArchived && hasChips && !isReplying) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        allActions?.forEach { action ->
                            val title = action.title?.toString() ?: ""
                            if (title.isBlank()) return@forEach
                            
                            val isReply = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
                            
                            Surface(
                                onClick = { 
                                    performHapticTick(context)
                                    if (isReply) {
                                        isReplying = true
                                    } else {
                                        onTriggerAction(action)
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = title, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (showConduitMarkRead) {
                            Surface(
                                onClick = {
                                    performHapticTick(context)
                                    val db = com.conduit.app.data.AppDatabase.getDatabase(context)
                                    val service = HubNotificationListenerService.instance
                                    val syncDismissal = generalPrefs.getBoolean("sync_dismissal", true)
                                    
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        db.notificationDao().archiveNotificationByKey(notification.notificationKey, System.currentTimeMillis())
                                        if (syncDismissal) {
                                            service?.cancel(notification.notificationKey)
                                        }
                                        com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "Mark Read",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (isReplying) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Type a reply...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            TextButton(onClick = { 
                                isReplying = false
                                replyText = ""
                            }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (replyAction != null) {
                                        onReply(replyText, replyAction)
                                    }
                                    isReplying = false
                                    replyText = ""
                                },
                                enabled = replyText.isNotBlank(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }

        if (minimizeIcons && !isCompactMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(vertical = 8.dp)
            ) {
                // Line 1: Title and time (with optional checkmark)
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = androidx.compose.material3.ripple(bounded = false, radius = 32.dp),
                                    enabled = true
                                ) { onSelectToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                if (notification.isPinned) {
                                    Icon(
                                        Icons.Filled.PushPin,
                                        contentDescription = "Pinned",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (notification.isArchived && !isArchivedView) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            if (isUnifiedView) "READ" else "ARCHIVED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = notification.title ?: "Unknown",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (notification.isPinned) formatTimestampWithDate(notification.timestamp) else formatTimestamp(notification.timestamp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (isArchivedView && notification.archivedTimestamp != null) {
                            val label = if (notification.isSnoozed) "Snoozed: " else "Read: "
                            val color = if (notification.isSnoozed) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                text = label + formatTimestamp(notification.archivedTimestamp),
                                fontSize = 11.sp,
                                color = color
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Line 2: Small App Icon + App Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarSize = 16.dp
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp),
                                enabled = true
                            ) { onSelectToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        val channelUpper = notification.channel.uppercase(java.util.Locale.ROOT)
                        if (channelUpper == "SMS" || channelUpper == "GOOGLE MESSAGES" ||
                            channelUpper == "EMAIL" || channelUpper == "GMAIL" || channelUpper == "SPARK" || channelUpper == "OUTLOOK" ||
                            channelUpper == "SNAPCHAT" ||
                            channelUpper == "LINKEDIN" ||
                            channelUpper == "INSTAGRAM" ||
                            channelUpper == "PHONE" || channelUpper == "SYSTEM PHONE" || channelUpper == "PHONE (GOOGLE DIALER)" || channelUpper == "TRUECALLER" ||
                            channelUpper == "TELEGRAM" || channelUpper == "TELEGRAM X" ||
                            channelUpper == "REDDIT" ||
                            channelUpper == "STEAM" ||
                            channelUpper == "FACEBOOK" || channelUpper == "MESSENGER" ||
                            channelUpper == "TWITTER (X)" || channelUpper == "MICROSOFT TEAMS"
                        ) {
                            AppIcon(notification.packageName, size = avatarSize)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(avatarSize)
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = notification.title?.firstOrNull()?.uppercase() ?: "M"
                                Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = 10.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    Text(
                        text = appName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BodyAndChipsBlock()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                AvatarBlock()
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (isCompactMode) 4.dp else 8.dp)
                ) {
                    HeaderTitleBlock()
                    BodyAndChipsBlock()
                }
            }
        }
    }
}

private val dateFormatHeader = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("M/d \u00b7 h:mm a", Locale.getDefault())

fun formatDateHeader(timestamp: Long): String {
    return dateFormatHeader.format(Date(timestamp)).uppercase()
}

fun formatTimestamp(timestamp: Long): String {
    return timeFormat.format(Date(timestamp))
}

fun formatTimestampWithDate(timestamp: Long): String {
    return dateTimeFormat.format(Date(timestamp))
}

@Composable
fun AppIcon(packageName: String, size: androidx.compose.ui.unit.Dp = 50.dp) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(appIconCache[packageName]) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = remember(size, density) { with(density) { size.roundToPx() } }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val drawable = getAppIcon(context, packageName)
                    if (drawable != null) {
                        val bmp = drawable.toBitmap(sizePx, sizePx).asImageBitmap()
                        appIconCache.put(packageName, bmp)
                        iconBitmap = bmp
                    }
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    // Ignore missing icon
                }
            }
        }
    }

    val currentIcon = iconBitmap
    if (currentIcon != null) {
        Image(
            bitmap = currentIcon,
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    } else {
        // Fallback to circular initial
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initial = packageName.split(".").lastOrNull()?.firstOrNull()?.uppercase() ?: "A"
            Text(initial, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.4).sp)
        }
    }
}



