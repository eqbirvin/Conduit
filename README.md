# Conduit

Conduit is a local utility app for Android that brings notifications from all your communication channels into a unified feed. It allows you to view, filter, search, and action incoming notifications locally on your device without relying on external cloud APIs or third party servers.


## App Description

Conduit mirrors active system notifications into a central hub. It extracts native notification action chips, allowing you to reply to messages, like posts, mark emails as read, or trigger app actions directly from the hub interface. All processing happens 100% locally on your device to preserve security and privacy.


## Features

1. Unified Notification Feed: View notifications from messaging, email, phone calls, social media, and system channels in a single timeline.

2. Native Action Chips: Perform inline replies, mark read actions, and trigger application actions using the native action chips provided by supported apps.

3. Channel Filtering: Filter your view to specific channels or search across read and archived notifications.

4. Android Native Snooze: Integrates directly with Android system notification snooze, keeping your status bar tray synchronized.

5. Work Profile Support: Dynamically detects, lists, and launches managed apps from Android Work Profiles with visual briefcase badges.

6. Floating Dock and Widget: Access quick app filters from a customizable floating dock and a home screen widget.


## Recent Updates (v2.08)

- **UI Polish**: Shrunk default avatar sizes to match native Android layout, freeing up horizontal space for notifications.
- **Minimized Notification Icons**: Added a new setting to display tiny icons inline with the app name, maximizing text width for notification content.
- **Custom View Filtering**: The floating app dock can now be filtered to only display apps included in your active Custom View.
- **Dock Refinements**: The floating dock now dynamically wraps its content width and floats cleanly over scrolling content.


## Building the Project

Requirements:
Android Studio or JDK 17 with Android SDK platform level 34.

Build command:
Run the following command in PowerShell to compile the debug APK and copy it to the outputs folder:

.\gradlew copyApkToOutputs

The output APK will be placed in the App.Outputs directory.


## Project History and Caveman Log

Project updates, feature additions, and bug fixes are logged chronologically in the central project ledger:

build.log/caveman.log

Refer to caveman.log to trace historical changes and version updates across release cycles.
