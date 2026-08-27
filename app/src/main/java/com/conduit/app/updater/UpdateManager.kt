package com.conduit.app.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.conduit.app.BuildConfig
import com.conduit.app.data.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.File
import androidx.work.*
import java.util.concurrent.TimeUnit

@Serializable
data class GithubAsset(
    val name: String,
    val browser_download_url: String
)

@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String,
    val assets: List<GithubAsset>
)

interface GitHubReleaseApi {
    @GET("releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_API_BASE = "https://api.github.com/repos/eqbirvin/conduit-releases/"
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_BASE)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(GitHubReleaseApi::class.java)

    /**
     * Checks for updates and returns the download URL and version if a newer version is available.
     * Returns null if already on the latest version or if an error occurs.
     */
    suspend fun checkForUpdates(): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val release = api.getLatestRelease()
            val latestVersion = release.tag_name.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME
            
            if (isNewerVersion(currentVersion, latestVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    return@withContext Pair(latestVersion, apkAsset.browser_download_url)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
        }
        return@withContext null
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLength) {
                val c = currentParts.getOrElse(i) { 0 }
                val l = latestParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (c > l) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Version parsing error", e)
        }
        return false
    }

    fun downloadAndInstall(context: Context, url: String, version: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            val fileName = "conduit-update-$version.apk"
            
            val request = DownloadManager.Request(uri)
                .setTitle("Downloading Conduit Update")
                .setDescription("Downloading version $version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                
            val downloadId = downloadManager.enqueue(request)
            
            // Register receiver to trigger install when download completes
            val receiver = DownloadReceiver(downloadId, fileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    receiver, 
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(
                    receiver, 
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue download", e)
        }
    }

    fun setupWorker(context: Context, interval: String) {
        val workManager = WorkManager.getInstance(context)
        val tag = "UpdateWorkerTag"
        
        if (interval == "DISABLED") {
            workManager.cancelAllWorkByTag(tag)
            return
        }

        val repeatInterval = when (interval) {
            "DAILY" -> 1L
            "EVERY_3_DAYS" -> 3L
            "WEEKLY" -> 7L
            else -> 1L
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(repeatInterval, TimeUnit.DAYS)
            .setConstraints(constraints)
            .addTag(tag)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "ConduitUpdateCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest
        )
    }
}

class DownloadReceiver(private val downloadId: Long, private val fileName: String) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id == downloadId) {
            try {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(installIntent)
                }
            } catch (e: Exception) {
                Log.e("DownloadReceiver", "Failed to start install intent", e)
            }
            context.applicationContext.unregisterReceiver(this)
        }
    }
}
