package com.conduit.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.CallDiagnosticsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CallDiagnosticsUiState(
    val enabled: Boolean = false,
    val eventCount: Int = 0,
    val isBusy: Boolean = false
)

sealed interface CallDiagnosticsExportResult {
    data class Ready(val uri: Uri) : CallDiagnosticsExportResult
    data object Empty : CallDiagnosticsExportResult
    data object Failed : CallDiagnosticsExportResult
}

class CallDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = CallDiagnosticsStore(application)
    private val prefs = application.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(CallDiagnosticsUiState())
    val uiState: StateFlow<CallDiagnosticsUiState> = _uiState.asStateFlow()

    private val _exportResults = MutableSharedFlow<CallDiagnosticsExportResult>(extraBufferCapacity = 1)
    val exportResults: SharedFlow<CallDiagnosticsExportResult> = _exportResults.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val (enabled, count) = withContext(Dispatchers.IO) {
            prefs.getBoolean(CallDiagnosticsStore.PREF_KEY_ENABLED, false) to store.readRecent().size
        }
        _uiState.update { it.copy(enabled = enabled, eventCount = count) }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean(CallDiagnosticsStore.PREF_KEY_ENABLED, enabled).apply()
        }
    }

    fun export() = viewModelScope.launch {
        _uiState.update { it.copy(isBusy = true) }
        val app = getApplication<Application>()
        val result = withContext(Dispatchers.IO) {
            val file = store.writeExport(BuildConfig.VERSION_NAME) ?: return@withContext CallDiagnosticsExportResult.Empty
            try {
                CallDiagnosticsExportResult.Ready(FileProvider.getUriForFile(app, "${app.packageName}.provider", file))
            } catch (e: IllegalArgumentException) {
                android.util.Log.e(TAG, "FileProvider cannot share the call diagnostics export", e)
                CallDiagnosticsExportResult.Failed
            }
        }
        _uiState.update { it.copy(isBusy = false) }
        _exportResults.emit(result)
    }

    fun clear() = viewModelScope.launch {
        withContext(Dispatchers.IO) { store.clear() }
        _uiState.update { it.copy(eventCount = 0) }
    }

    private companion object {
        const val TAG = "CallDiagnostics"
    }
}
