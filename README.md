# Conduit

## App Description

Conduit is a local utility app for Android that brings notifications from all your communication channels into a unified feed. It allows you to view, filter, search, and action incoming notifications locally on your device without relying on external cloud APIs or third party servers.

This repo stores the source code for the app. For the most recent built apks and releases of the app, check out ("Conduit-Releases")[https://github.com/eqbirvin/Conduit-Releases/tree/main]. I also keep a better readme on Conduit-Releases with feature list, screenshots, supported apps/channels, etc.

If you have an issue or a feature request, utilize Conduit-Releases "Issues" tab. 

## Building the Project

Requirements:
Android Studio or JDK 17 with Android SDK platform level 34.

Build command:
Run the following command in PowerShell to compile the debug APK and copy it to the outputs folder:

.\gradlew copyApkToOutputs

The output APK will be placed in the App.Outputs directory.

