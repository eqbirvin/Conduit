package com.conduit.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.conduit.app.data.SensitiveAccessState
import com.conduit.app.data.SensitiveGrantResult
import com.conduit.app.data.SensitiveNotificationAccessRepository
import com.conduit.app.shizuku.ShizukuRepository
import com.conduit.app.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SensitiveAccessUiState(
    val accessState: SensitiveAccessState = SensitiveAccessState.UNKNOWN,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val isLoaded: Boolean = false,
    val isGranting: Boolean = false,
    val errorMessage: String? = null
)

class SensitiveAccessViewModel(application: Application) : AndroidViewModel(application) {

    private val shizuku = ShizukuRepository(application)
    private val access = SensitiveNotificationAccessRepository(application, shizuku)

    private val _uiState = MutableStateFlow(SensitiveAccessUiState())
    val uiState: StateFlow<SensitiveAccessUiState> = _uiState.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Set when the user taps Grant before authorizing Conduit in Shizuku; the grant
    // runs automatically once Shizuku reports the permission was approved.
    private var grantAfterPermission = false

    init {
        viewModelScope.launch {
            merge(shizuku.statusChanges(), refreshRequests.onStart { emit(Unit) })
                .collect { reload() }
        }
    }

    /** Re-reads state, e.g. after returning from the Shizuku app or running the ADB command. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    fun onGrantWithShizuku() {
        when (_uiState.value.shizukuStatus) {
            ShizukuStatus.READY -> grant()
            ShizukuStatus.PERMISSION_REQUIRED -> {
                grantAfterPermission = true
                _uiState.update { it.copy(errorMessage = null) }
                viewModelScope.launch { shizuku.requestPermission() }
            }
            else -> Unit
        }
    }

    fun onOpenShizuku() {
        viewModelScope.launch {
            val intent = shizuku.managerLaunchIntent() ?: return@launch
            getApplication<Application>().startActivity(intent)
        }
    }

    private suspend fun reload() {
        val status = shizuku.currentStatus()
        val accessState = access.accessState()
        _uiState.update { it.copy(accessState = accessState, shizukuStatus = status, isLoaded = true) }

        if (grantAfterPermission) {
            when (status) {
                ShizukuStatus.READY -> {
                    grantAfterPermission = false
                    if (accessState != SensitiveAccessState.GRANTED) grant()
                }
                ShizukuStatus.PERMISSION_DENIED, ShizukuStatus.NOT_RUNNING, ShizukuStatus.NOT_INSTALLED ->
                    grantAfterPermission = false
                else -> Unit
            }
        }
    }

    private fun grant() {
        if (_uiState.value.isGranting) return
        _uiState.update { it.copy(isGranting = true, errorMessage = null) }
        viewModelScope.launch {
            val error = when (val result = access.grantWithShizuku()) {
                SensitiveGrantResult.Granted -> null
                SensitiveGrantResult.ServiceUnavailable ->
                    "Couldn't reach Shizuku. Make sure it's running, or use the ADB command."
                is SensitiveGrantResult.CommandFailed ->
                    "Shizuku couldn't grant access (code ${result.exitCode}). Try the ADB command instead."
            }
            _uiState.update { it.copy(isGranting = false, errorMessage = error) }
            reload()
        }
    }
}
