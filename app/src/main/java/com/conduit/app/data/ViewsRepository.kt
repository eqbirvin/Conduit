package com.conduit.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class CustomView(
    val id: String,
    val name: String,
    val packageNames: List<String>,
    val sortOrder: Int,
    val filterDock: Boolean = false
)

class ViewsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("conduit_views_prefs", Context.MODE_PRIVATE)

    private val _views = MutableStateFlow<List<CustomView>>(emptyList())
    val views: StateFlow<List<CustomView>> = _views.asStateFlow()

    private val _defaultViewId = MutableStateFlow<String?>(null)
    val defaultViewId: StateFlow<String?> = _defaultViewId.asStateFlow()

    init {
        loadViews()
    }

    private fun loadViews() {
        val viewsJson = prefs.getString("custom_views", "[]")
        val defaultId = prefs.getString("default_view_id", null)
        
        try {
            val loadedViews = Json.decodeFromString<List<CustomView>>(viewsJson ?: "[]")
            _views.value = loadedViews.sortedBy { it.sortOrder }
            
            if (defaultId != null && loadedViews.any { it.id == defaultId }) {
                _defaultViewId.value = defaultId
            } else {
                _defaultViewId.value = null
                prefs.edit().remove("default_view_id").apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _views.value = emptyList()
            _defaultViewId.value = null
        }
    }

    private fun saveViews(newViews: List<CustomView>) {
        val sortedViews = newViews.sortedBy { it.sortOrder }
        try {
            val json = Json.encodeToString(sortedViews)
            prefs.edit().putString("custom_views", json).apply()
            _views.value = sortedViews
            com.conduit.app.widget.WidgetUpdater.updateAllWidgets(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addView(name: String, packageNames: List<String>, filterDock: Boolean = false): String {
        val currentViews = _views.value.toMutableList()
        val newId = UUID.randomUUID().toString()
        val newView = CustomView(
            id = newId,
            name = name,
            packageNames = packageNames,
            sortOrder = currentViews.size,
            filterDock = filterDock
        )
        currentViews.add(newView)
        saveViews(currentViews)
        return newId
    }

    fun updateView(id: String, name: String, packageNames: List<String>, filterDock: Boolean = false) {
        val currentViews = _views.value.toMutableList()
        val index = currentViews.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = currentViews[index]
            currentViews[index] = existing.copy(name = name, packageNames = packageNames, filterDock = filterDock)
            saveViews(currentViews)
        }
    }

    fun deleteView(id: String) {
        val currentViews = _views.value.filter { it.id != id }
        saveViews(currentViews)
        if (_defaultViewId.value == id) {
            setDefaultView(null)
        }
    }

    fun reorderViews(fromIndex: Int, toIndex: Int) {
        val currentViews = _views.value.toMutableList()
        if (fromIndex !in currentViews.indices || toIndex !in currentViews.indices) return
        
        val view = currentViews.removeAt(fromIndex)
        currentViews.add(toIndex, view)
        
        val reorderedViews = currentViews.mapIndexed { index, customView -> 
            customView.copy(sortOrder = index)
        }
        saveViews(reorderedViews)
    }

    fun setDefaultView(id: String?) {
        _defaultViewId.value = id
        val editor = prefs.edit()
        if (id == null) {
            editor.remove("default_view_id")
        } else {
            editor.putString("default_view_id", id)
        }
        editor.apply()
    }
}
