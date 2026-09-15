package com.conduit.app

object ChannelRegistry {

    /**
     * Map of supported Android package names to their channel preference key and user-facing channel name.
     * Multiple package names can map to the same channel key (e.g. dialers, forks, lite/regional variants).
     */
    val supportedApps: Map<String, Pair<String, String>> = mapOf(
        // Core Messaging & Communication
        "com.google.android.apps.messaging" to Pair("channel_messages", "Google Messages"),
        "com.google.android.gm" to Pair("channel_gmail", "Gmail"),
        "com.readdle.spark" to Pair("channel_spark", "Spark"),
        "com.microsoft.office.outlook" to Pair("channel_outlook", "Outlook"),

        // Email Clients
        "net.thunderbird.android" to Pair("channel_thunderbird", "Thunderbird"),
        "net.thunderbird.android.beta" to Pair("channel_thunderbird", "Thunderbird"),
        "eu.faircode.email" to Pair("channel_fairemail", "FairEmail"),
        "com.fsck.k9" to Pair("channel_k9_mail", "K-9 Mail"),
        "com.easilydo.mail" to Pair("channel_edison_mail", "Email by Edison"),
        "ch.protonmail.android" to Pair("channel_proton_mail", "Proton Mail"),
        "com.yahoo.mobile.client.android.mail" to Pair("channel_yahoo_mail", "Yahoo Mail"),
        "com.yahoo.mobile.client.android.mail.go" to Pair("channel_yahoo_mail", "Yahoo Mail"),
        "com.pingapp.app" to Pair("channel_spike", "Spike Email"),
        "com.snapchat.android" to Pair("channel_snapchat", "Snapchat"),
        "com.linkedin.android" to Pair("channel_linkedin", "LinkedIn"),
        "com.instagram.android" to Pair("channel_instagram", "Instagram"),

        // Phone & Dialers
        "com.google.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.samsung.android.dialer" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.android.phone" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.android.server.telecom" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.android.contacts" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.google.android.apps.tycho" to Pair("channel_phone", "Phone (Google Dialer)"),
        "com.truecaller" to Pair("channel_truecaller", "Truecaller"),

        // Messaging, Social & Community
        "org.telegram.messenger" to Pair("channel_telegram", "Telegram"),
        "org.thunderdog.challegram" to Pair("channel_telegram_x", "Telegram X"),
        "com.reddit.frontpage" to Pair("channel_reddit", "Reddit"),
        "com.valvesoftware.android.steam.community" to Pair("channel_steam", "Steam"),
        "com.valvesoftware.android.steam.friendsui" to Pair("channel_steam_chat", "Steam Chat"),
        "com.facebook.katana" to Pair("channel_facebook", "Facebook"),
        "com.facebook.orca" to Pair("channel_messenger", "Messenger"),
        "com.twitter.android" to Pair("channel_twitter", "Twitter (X)"),
        "com.microsoft.teams" to Pair("channel_teams", "Microsoft Teams"),
        "com.airbnb.android" to Pair("channel_airbnb", "Airbnb"),
        "com.blackberry.hub" to Pair("channel_blackberry_inbox", "BlackBerry Inbox"),
        "com.whatsapp" to Pair("channel_whatsapp", "WhatsApp"),
        "com.whatsapp.w4b" to Pair("channel_whatsapp_business", "WhatsApp Business"),
        "com.beeper.chat" to Pair("channel_beeper", "Beeper"),
        "com.beeper.ima" to Pair("channel_beeper", "Beeper"),
        "com.beeper.android" to Pair("channel_beeper", "Beeper"),
        "com.textra" to Pair("channel_textra", "Textra"),
        "com.discord" to Pair("channel_discord", "Discord"),
        "com.discord.canary" to Pair("channel_discord", "Discord"),

        // Work & Productivity
        "com.Slack" to Pair("channel_slack", "Slack"),
        "com.google.android.apps.dynamite" to Pair("channel_google_chat", "Google Chat"),
        "us.zoom.videomeetings" to Pair("channel_zoom", "Zoom"),
        "com.google.android.apps.tachyon" to Pair("channel_google_meet", "Google Meet"),
        "com.google.android.apps.meetings" to Pair("channel_google_meet", "Google Meet"),

        // General Messaging & Chat
        "org.thoughtcrime.securesms" to Pair("channel_signal", "Signal"),
        "com.viber.voip" to Pair("channel_viber", "Viber"),
        "com.groupme.android" to Pair("channel_groupme", "GroupMe"),
        "com.skype.raider" to Pair("channel_skype", "Skype"),
        "com.skype.m2" to Pair("channel_skype", "Skype"),
        "com.bluebubbles.messaging" to Pair("channel_bluebubbles", "BlueBubbles"),
        "me.tagavari.airmessage" to Pair("channel_airmessage", "AirMessage"),
        "com.rebelvox.voxer" to Pair("channel_voxer", "Voxer"),
        "com.loudtalks" to Pair("channel_zello", "Zello"),
        "net.loudtalks" to Pair("channel_zello", "Zello"),

        // SMS / MMS / RCS Replacements
        "xyz.klinker.messenger" to Pair("channel_pulse_sms", "Pulse SMS"),
        "com.p1.chompsms" to Pair("channel_chomp_sms", "Chomp SMS"),
        "com.moez.QKSMS" to Pair("channel_qksms", "QKSMS"),
        "dev.octoshrimpy.quik" to Pair("channel_quik", "QUIK SMS"),
        "dev.octoshrimpy.quik.fdroid" to Pair("channel_quik", "QUIK SMS"),
        "com.anindra.messages" to Pair("channel_anindra_messages", "Messages (F-Droid)"),
        "com.simplemobiletools.smsmessenger" to Pair("channel_simple_sms", "Simple SMS Messenger"),
        "org.fossify.messages" to Pair("channel_simple_sms", "Simple SMS Messenger"),
        "com.calea.echo" to Pair("channel_mood_messenger", "Mood Messenger"),
        "rpkandrodev.yaata" to Pair("channel_yaata", "YAATA"),

        // Social & Entertainment
        "com.zhiliaoapp.musically" to Pair("channel_tiktok", "TikTok"),
        "com.ss.android.ugc.trill" to Pair("channel_tiktok", "TikTok"),
        "com.zhiliaoapp.musically.go" to Pair("channel_tiktok", "TikTok"),
        "tv.twitch.android.viewer" to Pair("channel_twitch", "Twitch"),
        "tv.twitch.android.app" to Pair("channel_twitch", "Twitch")
    )
}
