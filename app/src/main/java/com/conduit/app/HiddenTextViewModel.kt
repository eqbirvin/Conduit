package com.conduit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.HiddenTextApp
import com.conduit.app.data.HiddenTextRepository
import com.conduit.app.data.HiddenTextRule
import com.conduit.app.data.HiddenTextSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SuggestionScanState {
    object Idle : SuggestionScanState
    object Scanning : SuggestionScanState
    data class Done(val scannedCount: Int, val suggestions: List<HiddenTextSuggestion>) : SuggestionScanState
}

// Backs the Hidden Text settings screens: the app picker list and the recurring-text suggestion scan.
class HiddenTextViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HiddenTextRepository(application)

    private val _apps = MutableStateFlow<List<HiddenTextApp>>(emptyList())
    val apps: StateFlow<List<HiddenTextApp>> = _apps.asStateFlow()

    private val _scanState = MutableStateFlow<SuggestionScanState>(SuggestionScanState.Idle)
    val scanState: StateFlow<SuggestionScanState> = _scanState.asStateFlow()

    init {
        viewModelScope.launch { _apps.value = repository.loadApps() }
    }

    fun scan(isDemo: Boolean, rules: List<HiddenTextRule>) {
        if (_scanState.value == SuggestionScanState.Scanning) return
        _scanState.value = SuggestionScanState.Scanning
        viewModelScope.launch {
            val result = repository.scanForSuggestions(isDemo, rules)
            _scanState.value = SuggestionScanState.Done(result.scannedCount, result.suggestions)
        }
    }

    // Drops a suggestion once the user has turned it into a rule.
    fun removeSuggestion(phrase: String) {
        _scanState.update { state ->
            if (state is SuggestionScanState.Done) state.copy(suggestions = state.suggestions.filter { it.phrase != phrase }) else state
        }
    }
}
