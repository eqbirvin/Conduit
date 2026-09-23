package com.conduit.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore

object ComposeActionLauncher {

    private val KNOWN_CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.oppo.alarmclock",
        "com.miui.deskclock",
        "com.asus.deskclock",
        "com.motorola.blur.alarmclock"
    )

    private val KNOWN_RECORDER_PACKAGES = listOf(
        "com.google.android.apps.recorder",
        "com.sec.android.app.voicenote",
        "com.samsung.android.voicenote",
        "com.android.soundrecorder",
        "com.oneplus.soundrecorder",
        "com.coloros.soundrecorder"
    )

    fun createIntent(context: Context, action: String): Intent? {
        val pm = context.packageManager
        return when (action) {
            "conduit.action.SMS" -> {
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            }
            "conduit.action.EMAIL" -> {
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            }
            "conduit.action.DIAL" -> {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            }
            "conduit.action.CONTACT" -> {
                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            }
            "conduit.action.NOTE" -> {
                val primaryNoteIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent("android.intent.action.CREATE_NOTE")
                } else {
                    Intent("com.google.android.gms.actions.CREATE_NOTE")
                }
                if (primaryNoteIntent.resolveActivity(pm) != null) {
                    primaryNoteIntent
                } else {
                    val gmsIntent = Intent("com.google.android.gms.actions.CREATE_NOTE")
                    if (gmsIntent.resolveActivity(pm) != null) {
                        gmsIntent
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "")
                        }
                    }
                }
            }
            "conduit.action.EVENT" -> {
                Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                }
            }
            "conduit.action.TIMER" -> {
                resolveTimerIntent(context)
            }
            "conduit.action.ALARM" -> {
                resolveAlarmIntent(context)
            }
            "conduit.action.RECORD" -> {
                resolveRecordIntent(context)
            }
            else -> null
        }
    }

    private fun resolveTimerIntent(context: Context): Intent {
        val pm = context.packageManager

        // 1. Prefer SHOW_TIMERS on Android O+ (API 26+) as it directly opens the Timers interface without requiring duration extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val showTimersIntent = Intent(AlarmClock.ACTION_SHOW_TIMERS)
            val handlers = pm.queryIntentActivities(showTimersIntent, 0)
            val clockHandler = handlers.firstOrNull { it.activityInfo.packageName in KNOWN_CLOCK_PACKAGES }
            if (clockHandler != null) {
                showTimersIntent.setPackage(clockHandler.activityInfo.packageName)
                return showTimersIntent
            } else if (handlers.isNotEmpty()) {
                return showTimersIntent
            }
        }

        // 2. Try standard ACTION_SET_TIMER with UI confirmation enabled
        val setTimerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val setHandlers = pm.queryIntentActivities(setTimerIntent, 0)
        val clockSetHandler = setHandlers.firstOrNull { it.activityInfo.packageName in KNOWN_CLOCK_PACKAGES }
        if (clockSetHandler != null) {
            setTimerIntent.setPackage(clockSetHandler.activityInfo.packageName)
            return setTimerIntent
        } else if (setHandlers.isNotEmpty()) {
            return setTimerIntent
        }

        // 3. Fallback to direct launch intent of any installed known clock package
        for (pkg in KNOWN_CLOCK_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntent
            }
        }

        return setTimerIntent
    }

    private fun resolveAlarmIntent(context: Context): Intent {
        val pm = context.packageManager
        val setAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

        // Prioritize dedicated clock apps to prevent to-do/reminder apps (e.g. TickTick) from hijacking alarms
        val setHandlers = pm.queryIntentActivities(setAlarmIntent, 0)
        val clockHandler = setHandlers.firstOrNull { it.activityInfo.packageName in KNOWN_CLOCK_PACKAGES }
        if (clockHandler != null) {
            setAlarmIntent.setPackage(clockHandler.activityInfo.packageName)
            return setAlarmIntent
        }

        // If no known clock package handled SET_ALARM, check SHOW_ALARMS on known clock apps
        val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val showHandlers = pm.queryIntentActivities(showAlarmsIntent, 0)
        val showClockHandler = showHandlers.firstOrNull { it.activityInfo.packageName in KNOWN_CLOCK_PACKAGES }
        if (showClockHandler != null) {
            showAlarmsIntent.setPackage(showClockHandler.activityInfo.packageName)
            return showAlarmsIntent
        }

        // If there's an installed clock package, launch it directly
        for (pkg in KNOWN_CLOCK_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntent
            }
        }

        // Otherwise return setAlarmIntent (handles custom alarm apps like Sleep as Android or shows system chooser)
        return setAlarmIntent
    }

    private fun resolveRecordIntent(context: Context): Intent {
        val pm = context.packageManager
        val recordIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        if (recordIntent.resolveActivity(pm) != null) {
            return recordIntent
        }
        for (pkg in KNOWN_RECORDER_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                return launchIntent
            }
        }
        return recordIntent
    }
}
