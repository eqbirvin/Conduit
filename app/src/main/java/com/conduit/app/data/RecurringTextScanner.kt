package com.conduit.app.data

// Finds text blocks that recur across many notification bodies (e.g. "EXTERNAL:" tags, email
// warning banners, "Sent from my iPhone" signatures) so they can be suggested as hidden text.
// Pure Kotlin with no Android dependencies; run it off the main thread.
object RecurringTextScanner {

    data class Body(val packageName: String, val text: String)

    enum class Position { START, END, MIDDLE }

    data class RecurringText(
        val phrase: String,
        // Number of notifications whose body contains the phrase.
        val occurrences: Int,
        // Package name to occurrence count, most frequent first.
        val appCounts: List<Pair<String, Int>>,
        val position: Position,
        // A short excerpt of one matching body, the phrase included verbatim.
        val example: String
    )

    const val DEFAULT_MIN_OCCURRENCES = 25
    // Shorter phrases are mostly everyday wording ("Thanks so much") rather than boilerplate.
    const val MIN_PHRASE_WORDS = 4
    private const val MIN_PHRASE_CHARS = 6
    private const val MAX_PHRASE_CHARS = 1000
    private const val MAX_EDGE_TOKENS = 80
    // A shared prefix/suffix stops growing once no single continuation keeps this share of its notifications.
    private const val CONTINUE_RATIO = 0.6
    // Within this ratio, a shorter phrase inside a longer one is the same block and is dropped.
    private const val SAME_BLOCK_RATIO = 0.8
    private const val MAX_CANDIDATES_TO_MEASURE = 80
    private const val EXAMPLE_CONTEXT_CHARS = 80
    private val tokenRegex = Regex("\\S+")

    fun scan(
        bodies: List<Body>,
        minOccurrences: Int = DEFAULT_MIN_OCCURRENCES,
        maxResults: Int = 30
    ): List<RecurringText> {
        val docs = bodies.filter { it.text.isNotBlank() }
        if (docs.size < minOccurrences) return emptyList()

        // Candidate phrase -> estimated occurrences.
        val candidates = HashMap<String, Int>()
        fun offer(phrase: String, estimate: Int) {
            if (isPlausible(phrase)) candidates.merge(phrase, estimate, ::maxOf)
        }

        val texts = docs.map { it.text }
        val tokens = texts.map { text -> tokenRegex.findAll(text).map { it.range }.toList() }
        collectEdgeBlocks(texts, tokens, fromStart = true, minOccurrences, ::offer)
        collectEdgeBlocks(texts, tokens, fromStart = false, minOccurrences, ::offer)
        collectRecurringLines(texts, minOccurrences, ::offer)

        // Measure the most promising candidates exactly (estimated by how much text hiding each would remove).
        val measured = candidates.entries
            .sortedByDescending { it.value.toLong() * it.key.length }
            .take(MAX_CANDIDATES_TO_MEASURE)
            .mapNotNull { measure(it.key, docs, minOccurrences) }
            .sortedByDescending { it.phrase.length }

        val kept = mutableListOf<RecurringText>()
        for (candidate in measured) {
            val partOfLonger = kept.any { longer ->
                longer.phrase.contains(candidate.phrase) && longer.occurrences >= candidate.occurrences * SAME_BLOCK_RATIO
            }
            if (!partOfLonger) kept += candidate
        }
        // Most frequent first; longer blocks first among equals.
        return kept
            .sortedWith(compareByDescending<RecurringText> { it.occurrences }.thenByDescending { it.phrase.length })
            .take(maxResults)
    }

    // Walks shared leading (or trailing) words level by level, like a trie restricted to frequent
    // branches, and offers each shared block at the point where the notifications diverge.
    private fun collectEdgeBlocks(
        texts: List<String>,
        tokens: List<List<IntRange>>,
        fromStart: Boolean,
        minOccurrences: Int,
        offer: (String, Int) -> Unit
    ) {
        // Blocks stay within the first (or last) line, so a banner line is not merged into whatever follows it.
        fun tokenAt(doc: Int, depth: Int): String? {
            val ranges = tokens[doc]
            if (depth >= ranges.size) return null
            val text = texts[doc]
            val range: IntRange
            val gap: IntRange
            if (fromStart) {
                range = ranges[depth]
                gap = if (depth == 0) IntRange.EMPTY else (ranges[depth - 1].last + 1) until range.first
            } else {
                range = ranges[ranges.size - 1 - depth]
                gap = if (depth == 0) IntRange.EMPTY else (range.last + 1) until ranges[ranges.size - depth].first
            }
            if (!gap.isEmpty() && text.substring(gap).contains('\n')) return null
            return text.substring(range)
        }

        fun verbatim(doc: Int, depth: Int): String {
            val ranges = tokens[doc]
            return if (fromStart) {
                texts[doc].substring(ranges[0].first, ranges[depth - 1].last + 1)
            } else {
                texts[doc].substring(ranges[ranges.size - depth].first, ranges.last().last + 1)
            }
        }

        fun expand(group: List<Int>, depth: Int) {
            val children = group.groupBy { tokenAt(it, depth) }
            val frequent = children.filter { (token, docs) -> token != null && docs.size >= minOccurrences }
            val largestChild = frequent.values.maxOfOrNull { it.size } ?: 0
            val continues = largestChild >= group.size * CONTINUE_RATIO
            // Also offer it when enough notifications share exactly this block before the largest branch carries on.
            if (depth > 0 && (!continues || group.size - largestChild >= minOccurrences)) {
                // Spacing between words can differ; offer the most common exact form.
                val phrase = group.groupingBy { verbatim(it, depth) }.eachCount().maxByOrNull { it.value }!!.key
                offer(phrase, group.size)
            }
            if (depth < MAX_EDGE_TOKENS) {
                frequent.values.forEach { expand(it, depth + 1) }
            }
        }

        expand(texts.indices.toList(), 0)
    }

    // Whole lines (e.g. a banner on its own line in the middle of an email) that repeat.
    private fun collectRecurringLines(texts: List<String>, minOccurrences: Int, offer: (String, Int) -> Unit) {
        val lineCounts = HashMap<String, Int>()
        for (text in texts) {
            if (!text.contains('\n')) continue
            text.split('\n')
                .map { it.trim() }
                .filter { it.length >= MIN_PHRASE_CHARS }
                .toSet()
                .forEach { lineCounts.merge(it, 1, Int::plus) }
        }
        lineCounts.forEach { (line, count) -> if (count >= minOccurrences) offer(line, count) }
    }

    private fun isPlausible(phrase: String): Boolean =
        phrase.length in MIN_PHRASE_CHARS..MAX_PHRASE_CHARS &&
            phrase.any { it.isLetterOrDigit() } &&
            tokenRegex.findAll(phrase).take(MIN_PHRASE_WORDS).count() >= MIN_PHRASE_WORDS

    private fun measure(phrase: String, docs: List<Body>, minOccurrences: Int): RecurringText? {
        var occurrences = 0
        var wholeMessage = 0
        val appCounts = HashMap<String, Int>()
        val positions = IntArray(Position.values().size)
        var example: String? = null
        for (doc in docs) {
            val text = doc.text
            val index = text.indexOf(phrase)
            if (index < 0) continue
            occurrences++
            appCounts.merge(doc.packageName, 1, Int::plus)
            val trimmed = text.trim()
            when {
                trimmed == phrase -> wholeMessage++
                trimmed.startsWith(phrase) -> positions[Position.START.ordinal]++
                trimmed.endsWith(phrase) -> positions[Position.END.ordinal]++
                else -> positions[Position.MIDDLE.ordinal]++
            }
            if (example == null && trimmed != phrase) example = excerpt(text, index, phrase.length)
        }
        if (occurrences < minOccurrences) return null
        // Text that is usually the entire message (e.g. "Sent a photo") would leave it blank.
        if (wholeMessage * 2 > occurrences || example == null) return null
        return RecurringText(
            phrase = phrase,
            occurrences = occurrences,
            appCounts = appCounts.entries.sortedByDescending { it.value }.map { it.key to it.value },
            position = Position.values()[positions.indices.maxByOrNull { positions[it] }!!],
            example = example
        )
    }

    private fun excerpt(text: String, index: Int, length: Int): String {
        val start = maxOf(0, index - EXAMPLE_CONTEXT_CHARS)
        val end = minOf(text.length, index + length + EXAMPLE_CONTEXT_CHARS)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end) + suffix
    }
}
