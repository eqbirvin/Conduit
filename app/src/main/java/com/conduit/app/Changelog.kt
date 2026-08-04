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
