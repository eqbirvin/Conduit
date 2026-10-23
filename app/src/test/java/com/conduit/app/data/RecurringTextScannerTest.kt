package com.conduit.app.data

import com.conduit.app.data.RecurringTextScanner.Body
import com.conduit.app.data.RecurringTextScanner.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTextScannerTest {

    private val outlook = "com.microsoft.office.outlook"
    private val gmail = "com.google.android.gm"
    private val sms = "com.google.android.apps.messaging"

    private val chatter = listOf(
        "Are we still on for lunch?", "Running 5 min late", "Can you send the slides",
        "Happy birthday!!", "Call me when you can", "Thanks, got it", "See you at 6",
        "Did you see the game last night", "Pick up milk please", "On my way"
    )

    @Test
    fun findsLeadingTagWithAppCounts() {
        val subjects = listOf(
            "Q3 budget review", "Server maintenance tonight", "Your invoice is ready", "Team offsite agenda",
            "Password expiring soon", "New policy update", "Lunch and learn Friday", "Contract signed",
            "Weekly metrics", "Holiday schedule", "Parking garage closure", "Benefits enrollment"
        )
        val bodies = subjects.map { Body(outlook, "EXTERNAL: $it") } +
            (1..3).map { Body(gmail, "EXTERNAL: Invoice $it attached") } +
            chatter.map { Body(sms, it) }
        val results = RecurringTextScanner.scan(bodies)
        val tag = results.first { it.phrase.startsWith("EXTERNAL:") }
        assertEquals(15, tag.occurrences)
        assertEquals(listOf(outlook to 12, gmail to 3), tag.appCounts)
        assertEquals(Position.START, tag.position)
        assertTrue(tag.example.contains("EXTERNAL:"))
    }

    @Test
    fun findsWholeWarningBannerAsOneBlock() {
        val banner = "CAUTION: This email originated from outside of the organization. Do not click links or open attachments unless you recognize the sender."
        val bodies = (1..8).map { Body(outlook, "$banner\nHi team, notes from meeting $it are attached.") } +
            chatter.map { Body(sms, it) }
        val results = RecurringTextScanner.scan(bodies)
        assertEquals(banner, results.first().phrase)
        assertEquals(8, results.first().occurrences)
        // Fragments of the same banner are not listed separately.
        assertFalse(results.any { it.phrase != banner && banner.contains(it.phrase) })
    }

    @Test
    fun findsTrailingSignature() {
        val bodies = chatter.map { Body(sms, "$it\n\nSent from my iPhone") }
        val signature = RecurringTextScanner.scan(bodies).first()
        assertEquals("Sent from my iPhone", signature.phrase)
        assertEquals(Position.END, signature.position)
        assertEquals(10, signature.occurrences)
    }

    @Test
    fun skipsTextThatIsUsuallyTheWholeMessage() {
        val bodies = (1..10).map { Body(sms, "Sent a photo") } + chatter.map { Body(sms, it) }
        assertTrue(RecurringTextScanner.scan(bodies).none { it.phrase == "Sent a photo" })
    }

    @Test
    fun ignoresTextBelowTheMinimumOccurrences() {
        val bodies = (1..4).map { Body(outlook, "EXTERNAL: Report $it") } + chatter.map { Body(sms, it) }
        assertTrue(RecurringTextScanner.scan(bodies, minOccurrences = 5).isEmpty())
    }

    @Test
    fun alreadyHiddenPhrasesAreNotSuggestedAfterStripping() {
        val rules = listOf(HiddenTextRule(id = "1", phrase = "EXTERNAL:"))
        val bodies = (1..10).map { Body(outlook, "EXTERNAL: Weekly digest $it") }
            .map { it.copy(text = HiddenTextFilter.stripBody(it.packageName, it.text, rules)) }
        assertTrue(RecurringTextScanner.scan(bodies).none { it.phrase.contains("EXTERNAL:") })
    }
}
