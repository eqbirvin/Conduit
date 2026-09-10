package com.conduit.app.data

import kotlin.math.min

object FuzzySearchEngine {

    /**
     * Calculates the Damerau-Levenshtein distance between two strings,
     * supporting insertions, deletions, substitutions, and adjacent transpositions.
     */
    fun damerauLevenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val d = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j

        for (i in 1..len1) {
            val c1 = s1[i - 1]
            for (j in 1..len2) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1

                var minDistance = min(
                    min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )

                if (i > 1 && j > 1 && c1 == s2[j - 2] && s1[i - 2] == c2) {
                    minDistance = min(minDistance, d[i - 2][j - 2] + cost)
                }

                d[i][j] = minDistance
            }
        }
        return d[len1][len2]
    }

    /**
     * Extracts distinct normalized words (length >= 3) from text content.
     */
    fun extractWords(vararg texts: String?): Set<String> {
        val words = mutableSetOf<String>()
        val regex = Regex("[^a-zA-Z0-9]+")
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val tokens = text.split(regex)
            for (token in tokens) {
                val clean = token.trim().lowercase()
                if (clean.length >= 3) {
                    words.add(clean)
                }
            }
        }
        return words
    }

    /**
     * Attempts to find a typo correction for the given query against known vocabulary words.
     * Returns the full corrected query string, or null if no viable correction exists.
     */
    fun findCorrection(query: String, vocabulary: Set<String>): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || vocabulary.isEmpty()) return null

        val words = trimmed.split(Regex("\\s+"))
        var anyCorrected = false
        val correctedWords = mutableListOf<String>()

        for (word in words) {
            val lower = word.lowercase()
            if (lower.length < 3 || lower in vocabulary) {
                correctedWords.add(word)
                continue
            }

            val maxDistance = if (lower.length in 3..6) 1 else 2
            var bestCandidate: String? = null
            var bestDistance = maxDistance + 1

            for (vocab in vocabulary) {
                // Quick length difference check before calculating edit distance
                if (kotlin.math.abs(vocab.length - lower.length) > maxDistance) continue

                val dist = damerauLevenshteinDistance(lower, vocab)
                if (dist <= maxDistance && dist < bestDistance) {
                    bestDistance = dist
                    bestCandidate = vocab
                    if (dist == 1 && lower.length <= 5) break // Early exit on close match for short words
                }
            }

            if (bestCandidate != null) {
                correctedWords.add(bestCandidate)
                anyCorrected = true
            } else {
                correctedWords.add(word)
            }
        }

        return if (anyCorrected) correctedWords.joinToString(" ") else null
    }
}
