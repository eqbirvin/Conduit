package com.conduit.app

import android.app.Notification
import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.CustomView
import com.conduit.app.data.HubNotification
import com.conduit.app.data.NotificationRepository
import com.conduit.app.data.ViewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HubViewModel(
    application: Application,
    private val repository: NotificationRepository,
    val viewsRepository: ViewsRepository
) : AndroidViewModel(application) {

    private val rawNotifications: StateFlow<List<HubNotification>> = repository.activeNotifications
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val rawArchivedNotifications: StateFlow<List<HubNotification>> = repository.archivedNotifications
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeViewId = MutableStateFlow<String?>(null)
    val activeViewId: StateFlow<String?> = _activeViewId

    init {
        // Initialize activeViewId with defaultViewId if it exists in the views list
        viewModelScope.launch {
            viewsRepository.views.combine(viewsRepository.defaultViewId) { views, defaultId ->
                if (defaultId != null && views.any { it.id == defaultId }) {
                    defaultId
                } else {
                    null
                }
            }.collect { validDefaultId ->
                // Only set it once on startup, or when the default changes (if it changes to null, unset active)
                if (_activeViewId.value == null && validDefaultId != null) {
                    _activeViewId.value = validDefaultId
                } else if (validDefaultId == null && _activeViewId.value == viewsRepository.defaultViewId.value) {
                    _activeViewId.value = null
                }
            }
        }
    }

    fun setActiveViewId(id: String?) {
        _activeViewId.value = id
    }

    private fun filterByView(notifications: List<HubNotification>, activeId: String?, views: List<CustomView>): List<HubNotification> {
        val activeView = views.find { it.id == activeId } ?: return notifications
        val context = getApplication<Application>()
        return notifications.filter { notif ->
            val repPkg = getRepresentativePackage(context, notif.packageName)
            activeView.packageNames.contains(repPkg)
        }
    }

    val notifications: StateFlow<List<HubNotification>> = combine(rawNotifications, _activeViewId, viewsRepository.views) { notifs, activeId, views ->
        filterByView(notifs, activeId, views)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val archivedNotifications: StateFlow<List<HubNotification>> = combine(rawArchivedNotifications, _activeViewId, viewsRepository.views) { notifs, activeId, views ->
        filterByView(notifs, activeId, views)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val actionsByKey: StateFlow<Map<String, List<Notification.Action>>> = repository.activeNotifications
        .map { list ->
            val map = mutableMapOf<String, List<Notification.Action>>()
            list.forEach { notif ->
                val actions = repository.getNotificationActions(notif.notificationKey)
                if (actions != null) {
                    map[notif.notificationKey] = actions
                }
            }
            map
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun archiveNotification(id: Int, timestamp: Long) {
        viewModelScope.launch {
            repository.archiveNotification(id, timestamp)
        }
    }

    fun archiveMany(ids: List<Int>) {
        viewModelScope.launch {
            repository.archiveMany(ids)
        }
    }

    fun markSelectedAsRead(ids: List<Int>, context: Context) {
        viewModelScope.launch {
            val notifs = notifications.value.filter { it.id in ids }
            notifs.forEach { notif ->
                val actions = repository.getNotificationActions(notif.notificationKey)
                if (actions != null) {
                    val readAction = actions.find { 
                        it.title?.toString()?.contains("read", ignoreCase = true) == true ||
                        it.title?.toString()?.contains("done", ignoreCase = true) == true ||
                        it.title?.toString()?.contains("seen", ignoreCase = true) == true
                    }
                    if (readAction != null) {
                        try {
                            readAction.actionIntent.send()
                        } catch (e: android.app.PendingIntent.CanceledException) {
                            android.util.Log.e("HubViewModel", "Failed to send read action", e)
                        }
                    }
                }
            }
            repository.archiveMany(ids)
            com.conduit.app.widget.ConduitWidgetProvider.updateAllWidgets(context)
        }
    }

    fun snoozeNotification(id: Int, timestamp: Long, durationMs: Long = 3600000L) {
        viewModelScope.launch {
            repository.snoozeNotification(id, timestamp, durationMs)
        }
    }

    fun togglePin(notification: HubNotification, syncPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinWithSync(notification, syncPinned)
        }
    }

    fun markRead(key: String) {
        viewModelScope.launch {
            repository.markRead(key)
        }
    }

    fun triggerAction(notification: HubNotification, action: Notification.Action) {
        viewModelScope.launch {
            try {
                action.actionIntent.send()
                val titleString = action.title?.toString()?.lowercase() ?: ""
                if (titleString.contains("archive") || 
                    titleString.contains("delete") || 
                    titleString.contains("mark as read") || 
                    titleString.contains("clear")) {
                    repository.archiveNotificationByKey(notification.notificationKey, System.currentTimeMillis())
                }
            } catch (e: android.app.PendingIntent.CanceledException) {
                android.util.Log.e("HubViewModel", "Failed to trigger action", e)
            }
        }
    }

    fun sendReply(notification: HubNotification, text: String, action: Notification.Action) {
        viewModelScope.launch {
            try {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val intent = android.content.Intent()
                    val bundle = android.os.Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, text)
                    }
                    android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                    action.actionIntent.send(null, 0, intent)
                    
                    // Auto-archive on reply
                    repository.archiveNotificationByKey(notification.notificationKey, System.currentTimeMillis())
                }
            } catch (e: android.app.PendingIntent.CanceledException) {
                android.util.Log.e("HubViewModel", "Failed to send reply", e)
            }
        }
    }

    fun triggerContentIntent(notification: HubNotification, context: Context) {
        viewModelScope.launch {
            try {
                val intent = repository.getContentIntent(notification.notificationKey)
                if (intent != null) {
                    val options = android.app.ActivityOptions.makeBasic()
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        options.pendingIntentBackgroundActivityStartMode = android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    intent.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    val launchedCrossProfile = com.conduit.app.launchApp(context, notification.packageName)
                    if (!launchedCrossProfile) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        }
                    }
                }
            } catch (e: android.app.PendingIntent.CanceledException) {
                android.util.Log.e("HubViewModel", "Failed to trigger content intent (canceled)", e)
            } catch (e: android.content.ActivityNotFoundException) {
                android.util.Log.e("HubViewModel", "Failed to find activity for launch intent", e)
            } catch (e: SecurityException) {
                android.util.Log.e("HubViewModel", "SecurityException on intent launch", e)
            }
        }
    }

    class Factory(
        private val application: Application,
        private val repository: NotificationRepository,
        private val viewsRepository: ViewsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HubViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HubViewModel(application, repository, viewsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

