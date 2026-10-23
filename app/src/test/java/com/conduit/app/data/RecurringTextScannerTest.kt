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

    // Distinct message text whose first and last words vary, so only the planted block recurs.
    private val openers = listOf("Quick", "Reminder", "Heads-up", "Please", "Notes", "Agenda", "Draft", "Final", "Hello", "Status")
    private val closers = listOf("today", "tomorrow", "soon", "attached", "below", "thanks", "asap")
    private fun message(i: Int) = "${openers[i % openers.size]} item $i ${closers[i % closers.size]}"

    @Test
    fun findsLeadingTagWithAppCounts() {
        val tag = "[External sender] Use caution:"
        val bodies = (1..30).map { Body(outlook, "$tag ${message(it)}") } +
            (31..35).map { Body(gmail, "$tag ${message(it)}") } +
            chatter.map { Body(sms, it) }
        val result = RecurringTextScanner.scan(bodies).single()
        assertEquals(tag, result.phrase)
        assertEquals(35, result.occurrences)
        assertEquals(listOf(outlook to 30, gmail to 5), result.appCounts)
        assertEquals(Position.START, result.position)
        assertTrue(result.example.contains(tag))
    }

    @Test
    fun findsWholeWarningBannerAsOneBlock() {
        val banner = "CAUTION: This email originated from outside of the organization. Do not click links or open attachments unless you recognize the sender."
        val bodies = (1..30).map { Body(outlook, "$banner\n${message(it)}") } + chatter.map { Body(sms, it) }
        val results = RecurringTextScanner.scan(bodies)
        assertEquals(banner, results.first().phrase)
        assertEquals(30, results.first().occurrences)
        // Fragments of the same banner are not listed separately.
        assertFalse(results.any { it.phrase != banner && banner.contains(it.phrase) })
    }

    @Test
    fun findsTrailingSignature() {
        val bodies = (1..30).map { Body(sms, "${message(it)}\n\nSent from my iPhone") }
        val signature = RecurringTextScanner.scan(bodies).single()
        assertEquals("Sent from my iPhone", signature.phrase)
        assertEquals(Position.END, signature.position)
        assertEquals(30, signature.occurrences)
    }

    @Test
    fun skipsPhrasesShorterThanFourWords() {
        val bodies = (1..40).map { Body(sms, "Thanks so much! ${message(it)}") } +
            (1..40).map { Body(sms, "${message(it)} EXTERNAL:") }
        assertTrue(RecurringTextScanner.scan(bodies).isEmpty())
    }

    @Test
    fun skipsTextSeenInFewerThan25Notifications() {
        val bodies = (1..24).map { Body(outlook, "[External sender] Use caution: ${message(it)}") } + chatter.map { Body(sms, it) }
        assertTrue(RecurringTextScanner.scan(bodies).isEmpty())
    }

    @Test
    fun skipsTextThatIsUsuallyTheWholeMessage() {
        val bodies = (1..30).map { Body(sms, "You sent a photo message") } + chatter.map { Body(sms, it) }
        assertTrue(RecurringTextScanner.scan(bodies).isEmpty())
    }

    @Test
    fun ordersByOccurrencesDescending() {
        val longBanner = "CAUTION: This email originated from outside of the organization. Do not click links."
        val bodies = (1..30).map { Body(outlook, "$longBanner\n${message(it)}") } +
            (1..60).map { Body(sms, "${message(it + 100)}\nSent from my iPhone") }
        val results = RecurringTextScanner.scan(bodies)
        assertEquals(listOf("Sent from my iPhone", longBanner), results.map { it.phrase })
    }

    @Test
    fun alreadyHiddenPhrasesAreNotSuggestedAfterStripping() {
        val rules = listOf(HiddenTextRule(id = "1", phrase = "[External sender] Use caution:"))
        val bodies = (1..30).map { Body(outlook, "[External sender] Use caution: ${message(it)}") }
            .map { it.copy(text = HiddenTextFilter.stripBody(it.packageName, it.text, rules)) }
        assertTrue(RecurringTextScanner.scan(bodies).none { it.phrase.contains("Use caution") })
    }
}
