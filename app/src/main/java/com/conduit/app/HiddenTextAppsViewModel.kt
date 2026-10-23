package com.conduit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.HiddenTextApp
import com.conduit.app.data.HiddenTextAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Supplies the app list for the hidden text rule editor's "chosen apps" picker.
class HiddenTextAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HiddenTextAppsRepository(application)

    private val _apps = MutableStateFlow<List<HiddenTextApp>>(emptyList())
    val apps: StateFlow<List<HiddenTextApp>> = _apps.asStateFlow()

    init {
        viewModelScope.launch { _apps.value = repository.loadApps() }
    }
}
