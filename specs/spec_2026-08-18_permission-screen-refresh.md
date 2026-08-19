# Spec: Permission Screen Refresh

## Goal
Improve the first-launch experience by making the app automatically detect when the user grants Notification Access, and upgrading the visual design of the permissions screen to feel more premium and clear.

## Decisions

### User-Directed Decisions
*   **Visual Design**: A clean, premium look with updated typography and a static icon/logo (no animations needed for the icon). (Source: User)
*   **Transition Flow**: When the app detects the permission has been granted, it will briefly display a "Success!" state (e.g., a checkmark) for about 1 second before automatically transitioning to the main Hub Screen. (Source: User)

### Accepted Defaults
*   **Failure/Return State**: If the user returns from settings WITHOUT granting the permission, the screen simply remains in its "needs permission" state without showing intrusive error dialogs.
*   **Button Removal**: The manual "I've granted it, continue" button will be removed, as the app will fully rely on auto-detection via `onResume` lifecycle events.

## Scope Fences (Do Not Touch)
*   `HubNotificationListenerService.kt` and the core notification listening logic.
*   The main `HubScreen.kt` UI and navigation flow beyond the initial permission check.
*   Database schema and underlying notification data models.

## Verification Checklist
*   [ ] **A.** Build and install the release APK on a fresh device/emulator.
*   [ ] **B.** Launch the app to verify the new premium Permission Screen is displayed.
*   [ ] **C.** Tap "Grant Permission", which should open system settings. Grant the permission.
*   [ ] **D.** Press the system back button to return to Conduit.
*   [ ] **E.** Verify that the screen immediately changes to a "Success!" state (checkmark).
*   [ ] **F.** Verify that after ~1 second, the app automatically transitions to the Hub Screen.
*   [ ] **G.** Revoke the permission in settings, return to the app, and verify it correctly stays on the Permission Screen without errors.
