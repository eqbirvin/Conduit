package com.conduit.app

data class ChangelogItem(
    val title: String,
    val description: String,
    val iconName: String = "Info"
)

data class ChangelogRelease(
    val versionName: String,
    val date: String,
    val highlights: List<ChangelogItem>
)

val CHANGELOG: List<ChangelogRelease> = listOf(
    ChangelogRelease(
        versionName = "2.12.00",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Auto-Collapse Read Notifications",
                description = "Added a setting to automatically collapse read notifications to save space in your feed. This will override the master expand state.",
                iconName = "Compress"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.11.00",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Long Press to Select",
                description = "You can now long-press any notification to instantly select it and enter multi-select mode. The old tap-on-app-icon method has been removed to prevent accidental selections.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.10.01",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Mark All As Read",
                description = "Added a split button to the date header, allowing you to instantly mark all notifications for a specific date as read with a single tap.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.10.00",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Custom View Widget",
                description = "Added a new widget type that displays notifications filtered by a specific Custom View, complete with a dock of your apps from that view.",
                iconName = "Widgets"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.09.01",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Action Chips Fix",
                description = "Fixed a bug introduced in an earlier version where tapping notification action chips and inline replies would do nothing.",
                iconName = "Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.09.00",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Notification Expand/Collapse",
                description = "You can now expand and collapse individual notifications using the new toggle button next to the timestamp. We've also transformed the top bar button into a master 'Expand/Collapse All' toggle!",
                iconName = "Compress"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.08",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Retired Compact Mode",
                description = "Removed the layout squashing 'compact mode' functionality to standardize a single, clean notification layout. (The toggle button remains visually for a future feature).",
                iconName = "Compress"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.07",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Polished Default Avatar Sizes",
                description = "Shrunk the default notification app icons slightly to match the native Android system size, freeing up a bit more horizontal space for the notification title and content.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.06",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Minimized Layout Spacing 2",
                description = "Completely removed the spacing between the notification title and the app name in minimized mode for the tightest possible look.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.05",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Minimized Layout Spacing",
                description = "Reduced the spacing between the notification title and the app name in minimized mode for a tighter, cleaner look.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.04",
        date = "2026-08-09",
        highlights = listOf(
            ChangelogItem(
                title = "Minimized Icons Refined",
                description = "Refined the Minimized Notification Icons layout to align the title to the far left edge of the notification for a cleaner look.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.03",
        date = "2026-08-07",
        highlights = listOf(
            ChangelogItem(
                title = "Compact Notifications",
                description = "Added a 'Minimize Notification Icons' setting to shrink app icons and maximize space for text and action chips.",
                iconName = "Compress"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.08.00",
        date = "2026-08-07",
        highlights = listOf(
            ChangelogItem(
                title = "Custom View App Dock Filtering",
                description = "You can now configure custom views to exclusively display their included apps in the bottom dock when active.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.07.00",
        date = "2026-08-07",
        highlights = listOf(
            ChangelogItem(
                title = "Manage Views Enhancements",
                description = "Added drag-and-drop reordering to custom views, and moved default view toggling and deletion to the Edit View screen for a cleaner experience.",
                iconName = "Settings"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.06.00",
        date = "2026-08-06",
        highlights = listOf(
            ChangelogItem(
                title = "UI Tweaks & Polish",
                description = "Fine-tuned notification chips and alignments across the feed for a cleaner, Material 3 compliant appearance.",
                iconName = "Palette"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.05.00",
        date = "2026-08-07",
        highlights = listOf(
            ChangelogItem(
                title = "Custom Views",
                description = "Create and manage custom views to filter your notification feed to specific sets of apps, accessible right from the top bar.",
                iconName = "FilterList"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.04.02",
        date = "2026-08-06",
        highlights = listOf(
            ChangelogItem(
                title = "App Bundles Toggle",
                description = "Added a setting to completely hide the floating action button (App Bundles) if you prefer a cleaner interface.",
                iconName = "Settings"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.04.00",
        date = "2026-08-06",
        highlights = listOf(
            ChangelogItem(
                title = "UI Refinements",
                description = "Moved Search and Settings to the top bar, and upgraded the app dock to stretch fully across the screen.",
                iconName = "Dashboard"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.03.00",
        date = "2026-08-06",
        highlights = listOf(
            ChangelogItem(
                title = "Configurable Retention Period",
                description = "Choose your preferred notification auto-retention period (30, 60, 90, 120, or 365 days) directly from Settings.",
                iconName = "Settings"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.13",
        date = "2026-08-05",
        highlights = listOf(
            ChangelogItem(
                title = "App Performance & Stability",
                description = "Improved list rendering, bounded memory usage for app icons, and fortified error handling across all features.",
                iconName = "Speed"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.12",
        date = "2026-08-05",
        highlights = listOf(
            ChangelogItem(
                title = "Under-the-Hood Refinements",
                description = "Refactored the core data layer to improve widget syncing and optimized the 'mark selected as read' action for a faster and more stable experience.",
                iconName = "Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.08",
        date = "2026-08-04",
        highlights = listOf(
            ChangelogItem(
                title = "In-App Release History",
                description = "All release notes are now saved in a permanent structured changelog history accessible from Settings.",
                iconName = "Info"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.06",
        date = "2026-08-04",
        highlights = listOf(
            ChangelogItem(
                title = "90-Day Auto-Retention Policy",
                description = "Old archived notifications that are not pinned are now automatically cleaned up after 90 days to keep database performance optimal.",
                iconName = "Storage"
            ),
            ChangelogItem(
                title = "Database Indexing & Reliability",
                description = "Added SQL indices for fast querying and implemented safe non-destructive database migrations.",
                iconName = "Storage"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.04",
        date = "2026-08-04",
        highlights = listOf(
            ChangelogItem(
                title = "Smooth Notification Scroll",
                description = "Optimized list composition, cached app labels, and debounced tray refreshes for smooth, lag-free scrolling.",
                iconName = "Speed"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.02.02",
        date = "2026-06-24",
        highlights = listOf(
            ChangelogItem(
                title = "Unified Phone Channels",
                description = "Automatically groups Phone, Contacts, and Telecom system channels under a single Phone channel to prevent duplicate dock icons.",
                iconName = "Call"
            ),
            ChangelogItem(
                title = "Work Profile Support",
                description = "Conduit now dynamically scans and launches apps from your Android Work Profile. Managed apps display with briefcase badge overlays.",
                iconName = "Work"
            ),
            ChangelogItem(
                title = "Dynamic System Tray Sync",
                description = "Rebuilt status bar notification tray checks to dynamically filter against enabled channels, automatically supporting all new networks (Facebook, Teams, Messenger, Twitter).",
                iconName = "Refresh"
            )
        )
    )
)

