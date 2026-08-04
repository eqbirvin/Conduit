# Room Database keep rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.Delegate *;
}

# Widget Service & Provider keep rules
-keep class com.conduit.app.widget.ConduitWidgetService { *; }
-keep class com.conduit.app.widget.ConduitWidgetService$* { *; }
-keep class com.conduit.app.widget.ConduitWidgetProvider { *; }
-keep class com.conduit.app.widget.ConduitWidgetClickActivity { *; }

# HubNotification Data Entity & Database keep rules
-keep class com.conduit.app.data.** { *; }

# Notification Listener Service keep rules
-keep class com.conduit.app.HubNotificationListenerService { *; }
