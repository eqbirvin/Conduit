package com.conduit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HiddenTextFilterTest {

    private val outlook = "com.microsoft.office.outlook"
    private val sms = "com.google.android.apps.messaging"

    private fun rule(
        phrase: String,
        matchCase: Boolean = false,
        target: HiddenTextTarget = HiddenTextTarget.BOTH,
        packages: List<String> = emptyList()
    ) = HiddenTextRule(id = phrase, phrase = phrase, matchCase = matchCase, target = target, packageNames = packages)

    private fun notif(pkg: String, title: String?, text: String?) = HubNotification(
        packageName = pkg, notificationKey = "k", title = title, text = text, timestamp = 0L, channel = "c"
    )

    @Test
    fun removesPhraseAndTidiesSpacing() {
        val result = HiddenTextFilter.apply(notif(outlook, "EXTERNAL: Quarterly report", "Hi team"), listOf(rule("EXTERNAL:")))
        assertEquals("Quarterly report", result.title)
        assertEquals("Hi team", result.text)
    }

    @Test
    fun anyCaseByDefaultButMatchCaseIsStrict() {
        val n = notif(outlook, "external: hello", null)
        assertEquals("hello", HiddenTextFilter.apply(n, listOf(rule("EXTERNAL:"))).title)
        assertEquals("external: hello", HiddenTextFilter.apply(n, listOf(rule("EXTERNAL:", matchCase = true))).title)
    }

    @Test
    fun respectsTitleOrBodyTarget() {
        val n = notif(outlook, "EXTERNAL: Subject", "EXTERNAL: Body")
        val titleOnly = HiddenTextFilter.apply(n, listOf(rule("EXTERNAL:", target = HiddenTextTarget.TITLE)))
        assertEquals("Subject", titleOnly.title)
        assertEquals("EXTERNAL: Body", titleOnly.text)
        val bodyOnly = HiddenTextFilter.apply(n, listOf(rule("EXTERNAL:", target = HiddenTextTarget.BODY)))
        assertEquals("EXTERNAL: Subject", bodyOnly.title)
        assertEquals("Body", bodyOnly.text)
    }

    @Test
    fun appScopedRuleOnlyAffectsChosenApps() {
        val rules = listOf(rule("EXTERNAL:", packages = listOf(outlook)))
        assertEquals("Hi", HiddenTextFilter.apply(notif(outlook, "EXTERNAL: Hi", null), rules).title)
        assertEquals("EXTERNAL: Hi", HiddenTextFilter.apply(notif(sms, "EXTERNAL: Hi", null), rules).title)
    }

    @Test
    fun collapsesBlankLinesLeftByARemovedBanner() {
        val banner = "CAUTION: This email originated from outside the organization."
        val text = "$banner\n\nHello there,\n\nSee attached."
        val result = HiddenTextFilter.apply(notif(outlook, "Subject", text), listOf(rule(banner)))
        assertEquals("Hello there,\n\nSee attached.", result.text)
    }

    @Test
    fun unchangedNotificationIsReturnedAsIs() {
        val n = notif(outlook, "Hello", "World")
        assertSame(n, HiddenTextFilter.apply(n, listOf(rule("EXTERNAL:"))))
        assertSame(n, HiddenTextFilter.apply(n, emptyList()))
    }

    @Test
    fun nullTitleAndTextStayNull() {
        val result = HiddenTextFilter.apply(notif(outlook, null, null), listOf(rule("EXTERNAL:")))
        assertEquals(null, result.title)
        assertEquals(null, result.text)
    }
}
