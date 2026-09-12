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
        versionName = "2.22.00",
        date = "September 12, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Discord Channel Support",
                description = "Added full channel integration for Discord and Discord Canary. Intercepts incoming messages and calls with support for direct chat launches, native action chips (Reply and Mark as Read), unread dock counters, dynamic conversation bubbles, and demo mode previews.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.21.02",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "UI Improvements",
                description = "Increased text size on date headers for better readability.",
                iconName = "FormatSize"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.21.01",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Restricted Settings Helper",
                description = "Intelligent detection for sideloaded installs on Android 13+. Provides a direct 1-tap shortcut to App Info with clear instructions to enable restricted settings, then seamlessly returns to notification listener setup.",
                iconName = "Security"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.21.00",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Trigger Native Mark as Read",
                description = "New setting to trigger third-party apps' native 'Mark as Read' or 'Done' actions when marking notifications as read via swipe, date header, or bulk selection. Unsupported notifications automatically fall back to standard dismissal.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.20.02",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Cleaned Up Search UI",
                description = "Custom view chips are now hidden when searching to provide a clean, distraction-free search experience.",
                iconName = "Search"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.20.01",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Multi-Word & Typo-Tolerant Search",
                description = "Global search now matches multi-word queries in any order and features automatic typo tolerance. If no exact matches are found, it automatically locates and displays results for close matches with an interactive banner.",
                iconName = "Search"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.20.00",
        date = "September 10, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "High-Performance Paginated Search",
                description = "Global search is now powered by Jetpack Paging 3 and Room database queries with a 250ms typing debounce. Results load asynchronously with lower memory usage and buttery smooth scrolling through large archives.",
                iconName = "Search"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.19.06",
        date = "September 7, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Snooze in Selection Mode",
                description = "Added a snooze action button to the selection mode top bar when a single notification is selected, allowing you to quickly snooze notifications via the native snooze sheet.",
                iconName = "Snooze"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.19.05",
        date = "September 7, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Block in Selection Mode",
                description = "Added a block action button to the selection mode top bar when a single notification is selected, allowing you to quickly create filter rules for matching notifications.",
                iconName = "Block"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.19.01",
        date = "September 4, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Bracket Hanger Improvements",
                description = "The Bracket Hanger now dynamically supports all enabled notification channels. Additionally, the 'Group by channel' setting now correctly groups notifications by their actual channel, rather than creating separate groups for every app variation.",
                iconName = "Icons.Filled.DynamicFeed"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.19.00",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Default Notification Filters",
                description = "Conduit now ships with a default set of blocked notification rules to automatically filter out common spam and status updates from Snapchat, Messages, and Textra.",
                iconName = "Icons.Filled.Block"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.13",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "WhatsApp Conversation Fix",
                description = "Fixed a bug where incoming WhatsApp messages could mistakenly be treated as your own replies, causing them to append to an archived notification instead of appearing as a new message.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.12",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "WhatsApp Reply & Dock Fixes",
                description = "Fixed an issue where new WhatsApp messages could be dropped if they shared a prefix with a previous message you replied to. Also fixed a visual issue where small app icons (like WhatsApp and Textra) would appear blurry on the dock by properly caching high-res versions.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.11",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Textra False Positive Fix",
                description = "Fixed an issue where Textra's incoming 1-on-1 messages were incorrectly classified as self-replies because the app mistakenly assigns the contact's name to your display name.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.10",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Textra and WhatsApp Reply Fixes",
                description = "Fixed an issue where Textra messages were sometimes swallowed during self-reply checks, and added Ingestion Diagnostics to Dev Settings to aid future troubleshooting.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.09",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Further Textra and Icon Fixes",
                description = "Bypassed standard grouping logic that was inadvertently filtering Textra notifications. Also updated icon fetching to use native Application Icons for maximum clarity across all devices.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.08",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Sharp Icons and Robust Parsing",
                description = "Fixed an issue causing icons (like WhatsApp and Textra) to look blurry on the dock by fetching the high-resolution device-specific icon. Also added comprehensive parsing fallbacks to guarantee non-standard notifications (like Textra) are always captured.",
                iconName = "Icons.Filled.HighQuality"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.07",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Textra Notifications Fixed",
                description = "Resolved an issue where Textra messages were being ignored by Conduit. Conduit now properly parses their non-standard MessagingStyle formatting so they show up beautifully in your feed.",
                iconName = "Icons.Filled.Sms"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.06",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Optimistic Reply Updates",
                description = "Replying via Conduit's action chips is now instant! Conduit will immediately append your reply and mark the notification as read locally, preventing desyncs with apps (like WhatsApp) that are slow to update their native notifications.",
                iconName = "Icons.Filled.FlashOn"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.05",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Smart Reply Detection Updated",
                description = "When you reply to a message (via Conduit or the Android notification shade), Conduit will now correctly detect your response, append it to the notification with 'You:', and automatically mark the notification as read so it clears from your feed.",
                iconName = "Icons.Filled.MarkChatRead"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.04",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Added Textra Support",
                description = "Conduit now officially supports Textra SMS!",
                iconName = "Icons.Filled.ChatBubble"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.03",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Added Beeper Support",
                description = "Conduit now officially supports Beeper! Your unified chat notifications will now integrate perfectly into your Conduit feed.",
                iconName = "Icons.Filled.ChatBubble"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.02",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Added WhatsApp Support",
                description = "Conduit now officially supports WhatsApp and WhatsApp Business! Notifications from these apps will be fully integrated into your feed.",
                iconName = "Icons.Filled.ChatBubble"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.01",
        date = "September 3, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Outlook Icon Fix",
                description = "Fixed an issue where the Outlook app icon was missing and shown as uninstalled for Android 11+ Work Profile users.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.17.00",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Uninstalled Apps Cleaner",
                description = "Added a new wizard in Settings to help you clean up historical notifications from apps you no longer have installed.",
                iconName = "Icons.Filled.Delete"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.16.02",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Fixed Action Chips (Part 2)",
                description = "Fixed an issue where some action chips would open to a blank screen because the notification was being dismissed too quickly.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.16.01",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Fixed Action Chips",
                description = "Fixed an issue where tapping action chips (like Reply All or Snooze) on some notifications would silently fail to launch.",
                iconName = "Icons.Filled.Build"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.15",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Fixed BlackBerry Inbox Icon",
                description = "Fixed an issue where the BlackBerry Inbox icon would not display properly on notifications.",
                iconName = "Icons.Filled.MailOutline"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.14",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Added BlackBerry Inbox Support",
                description = "Conduit now supports notifications from the BlackBerry Inbox application.",
                iconName = "Icons.Filled.MailOutline"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.13",
        date = "September 2, 2026",
        highlights = listOf(
            ChangelogItem(
                title = "Fixed Swipe-to-Pin",
                description = "Fixed a bug where swiping to trigger the Pin action would not correctly pin the notification.",
                iconName = "Icons.Filled.PushPin"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.05",
        date = "2026-08-29",
        highlights = listOf(
            ChangelogItem(
                title = "Demo Mode",
                description = "Replaced the old test notifications with a robust new Demo Mode in Developer Settings. Turning it on populates your feed with realistic mock notifications spread out over 5 days, perfect for taking clean screenshots.",
                iconName = "Settings"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.04",
        date = "2026-08-26",
        highlights = listOf(
            ChangelogItem(
                title = "Default to Todo Mode",
                description = "Added a new setting to automatically open Conduit in Todo Mode on startup instead of the Unified View.",
                iconName = "Checklist"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.03",
        date = "2026-08-26",
        highlights = listOf(
            ChangelogItem(
                title = "Stale Update Icon Fix",
                description = "Fixed an issue where the update available icon would persist even after successfully updating to the latest version.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.02",
        date = "2026-08-26",
        highlights = listOf(
            ChangelogItem(
                title = "Update Icon Placement",
                description = "Moved the 'Update Available' icon to the left side of the Conduit title for better visibility.",
                iconName = "SystemUpdate"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.01",
        date = "2026-08-25",
        highlights = listOf(
            ChangelogItem(
                title = "Custom View Chip Spacing",
                description = "Adjusted the layout of custom view chips so they are perfectly spaced when wrapping across multiple lines.",
                iconName = "FilterList"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.15.00",
        date = "2026-08-25",
        highlights = listOf(
            ChangelogItem(
                title = "OTA Updates",
                description = "Conduit can now automatically check for updates! You can manually check for updates or change the auto-check interval in Settings.",
                iconName = "SystemUpdate"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.12",
        date = "2026-08-20",
        highlights = listOf(
            ChangelogItem(
                title = "Airbnb Support",
                description = "Added support for Airbnb notifications! You can enable it anytime from the Supported Apps menu in Settings.",
                iconName = "Travel"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.11",
        date = "2026-08-19",
        highlights = listOf(
            ChangelogItem(
                title = "Immediate Block Resolution",
                description = "When a new block rule is added, any existing notifications matching that rule are instantly removed from the app and the system tray.",
                iconName = "Block"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.10",
        date = "2026-08-19",
        highlights = listOf(
            ChangelogItem(
                title = "Widget Titles",
                description = "Updated widget names in the launcher selection screen to clearly distinguish between the Standard Hub and Custom View Hub.",
                iconName = "Widgets"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.09",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Polished Setup UI",
                description = "Enhanced the initial setup screen with a sleek dark background and refined copy.",
                iconName = "Check"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.08",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Startup Notification Import",
                description = "Added a one-time prompt upon first launch offering to import your existing notifications into Conduit.",
                iconName = "Check"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.07",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Full Release History",
                description = "Added a full-screen 'What's New' page in Settings, allowing you to browse the complete, scrollable history of all Conduit updates.",
                iconName = "Info"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.06",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Permission Screen Polish",
                description = "Upgraded the initial permission screen with a premium design and auto-detection, making the first launch experience completely seamless.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.05",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Dock Scroll Indicators",
                description = "Added the ability to customize how the dock indicates scrollable content. Choose from Fading Edges, Scrollbar Track, Initial Bounce, or None.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.01",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "App Filter Enhancements",
                description = "Fixed a bug where a filtered app dock selection wasn't clearing when switching custom views. Old phone notifications now correctly display the dialer icon and open the phone app. Disabled apps no longer clutter the channels list.",
                iconName = "Tune"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.14.00",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Smart Terminology",
                description = "Updated action verbs to match the notification type. You now 'Dismiss' social and system alerts, and 'Mark Read' messages.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.13.01",
        date = "2026-08-18",
        highlights = listOf(
            ChangelogItem(
                title = "Immediate Widget Updates",
                description = "Widgets now update immediately and reliably alongside the main app. Both the main Conduit widget and Custom View widgets correctly synchronize whenever notifications are archived, pinned, or cleared.",
                iconName = "Widgets"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.13.00",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Sync Engine v2",
                description = "Implemented honest read-state tracking, notification classification, and tray reconciliation. Added a new setting to auto-dismiss detached notifications to keep your feed accurate.",
                iconName = "Sync"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.12.05",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Further Simplified Multi-Select",
                description = "Removed the 'Archive' option from the multi-select menu. Archiving notifications is now an individual action.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.12.04",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Simplified Multi-Select",
                description = "Removed the 'Block' option from the multi-select menu. Blocking notifications is now an individual action.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.12.03",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Reduced Accidental Swipes",
                description = "Increased the swipe resistance so fast vertical scrolls no longer accidentally trigger a horizontal swipe.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.12.02",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Swipe Precision Tweaks",
                description = "Fixed a bug where returning a swiped item to its resting position would accidentally trigger the swipe action, and reduced the swipe effort to make intentional flicks easier while blocking accidental scrolls.",
                iconName = "CheckCircle"
            )
        )
    ),
    ChangelogRelease(
        versionName = "2.12.01",
        date = "2026-08-17",
        highlights = listOf(
            ChangelogItem(
                title = "Swipe Sensitivity Adjustments",
                description = "Tweaked the swipe-to-dismiss threshold to require dragging a notification 70% of the way across the screen to trigger its action, reducing accidental swipes.",
                iconName = "CheckCircle"
            )
        )
    ),
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

