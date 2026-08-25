package com.conduit.app.updater

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.conduit.app.data.SettingsRepository

class UpdateWorker(
    appContext: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val updateInfo = UpdateManager.checkForUpdates()
            if (updateInfo != null) {
                val latestVersion = updateInfo.first
                // We need to update SettingsRepository state
                // To do this, we need the preferences instance. 
                // We can fetch it via context.
                val prefs = applicationContext.getSharedPreferences("conduit_prefs", Context.MODE_PRIVATE)
                val settingsRepo = SettingsRepository(prefs)
                
                settingsRepo.updateUpdateAvailableState(hasUpdate = true, latestVersion = latestVersion)
                
                // Fire a toast or silent notification (Toast on background thread requires main thread dispatcher, 
                // but WorkManager is usually background. Since we just update state, HubScreen will pick it up)
                
                // For the toast requested in requirements: "a quick toast notification"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Conduit Update Available: $latestVersion", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Error in UpdateWorker", e)
            Result.failure()
        }
    }
}
