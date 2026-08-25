# FOSS OTA Updates Requirements Specification

## Goal
Implement a native Over-the-Air (OTA) update system in Conduit that queries a separate public GitHub repository (`conduit-releases`) for updates, bypassing third-party update libraries.

## Decisions

### Sourced from User Answers (Deciders)
1. **Background Scheduling**: Use `WorkManager` for background checks to ensure reliable execution that respects battery optimizations and survives reboots. (User Answer)
2. **Download Trigger**: The automatic background task will *only* check for the version. The APK download (via `DownloadManager`) will only begin when the user clicks "Update" on the prompt. (User Answer)
3. **Dismissal Behavior**: If the user dismisses the "Update Available" dialog, the top bar icon will remain permanently visible until they either update the app or manually clear the state (if implemented later), but the dialog will close. (User Answer)

### Sourced from Defaults
1. **GitHub API Auth**: Unauthenticated API calls will be used to query the public `conduit-releases` repo. The 60 requests/hour limit is acceptable for our daily/weekly check intervals.
2. **Settings Storage**: The chosen update interval (Daily, 3 Days, Weekly, Disabled) will be stored via `SharedPreferences` in `ConduitSettings.kt` and managed via `SettingsRepository.kt`.
3. **FileProvider Authority**: Will use `${applicationId}.provider` to securely pass the downloaded APK file URI to the Android Package Installer.
4. **Network Constraints**: The background JSON version check can run on any network. The download (triggered manually by the user) will also run on the current network unless the OS restricts it.
5. **JSON Parsing**: We will use Retrofit (with `kotlinx.serialization` if possible, or Gson if preferred based on dependencies) to fetch the latest GitHub release.

## Scope Fences ("Do Not Touch")
- **Existing Notification Logic**: The Hub Notification Listener, Sync engine, and widget updating mechanisms will not be altered.
- **Database Schemas**: Room database entities (`AppDatabase`, `HubNotification`) will remain untouched as updates rely solely on `SharedPreferences`.

## Verification Checklist
A. On a release build (`./gradlew copyApkToOutputs`), open Settings and confirm the new "Auto-check for updates" dropdown and "Check for Updates" manual button are present.
B. Manually tap "Check for Updates". If a newer release exists on GitHub, verify the "Update Available" dialog appears with the correct current and new version strings.
C. Tap "Update" in the dialog and verify that a download begins (observable via system notifications/DownloadManager).
D. Verify that upon download completion, the system Package Installer automatically prompts to install the new APK (verifying FileProvider is configured correctly).
E. (Simulated) Verify that when an automatic update check finds a new version, a Toast appears and the Top Bar displays an update icon.
F. Tap the Top Bar update icon and verify it opens the "Update Available" dialog. Dismiss the dialog and verify the icon remains in the Top Bar.
