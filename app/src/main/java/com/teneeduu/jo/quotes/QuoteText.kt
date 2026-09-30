package com.teneeduu.jo.quotes

data class Quote(val text: String, val source: String)

/**
 * One quote per line, source after a dash: `句子 —— 出处`. The same format the
 * iOS version reads from Quotes.txt, so the two can share a file later.
 */
object QuoteText {
    private val separators = listOf("——", "--", "—")

    fun parse(raw: String): Quote? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        for (separator in separators) {
            // The last dash wins, so a dash inside the sentence itself survives.
            val at = line.lastIndexOf(separator)
            if (at < 0) continue
            // Stop at the first separator kind found: falling through to "—" would
            // split a leading "——" down the middle.
            val text = line.substring(0, at).trim()
            val source = line.substring(at + separator.length).trim()
            return if (text.isEmpty()) Quote(line, "") else Quote(text, source)
        }
        return Quote(line, "")
    }

    fun format(quote: Quote): String =
        if (quote.source.isBlank()) quote.text else "${quote.text} —— ${quote.source}"
}
