package com.teneeduu.jo.quotes

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

/** The 40 classical quotes shipped in the APK plus the user's own lines. */
class QuoteLibrary(private val context: Context) {

    private val customFile = File(context.filesDir, "Quotes.txt")

    fun all(): List<Quote> = bundled() + custom()

    fun custom(): List<Quote> =
        if (customFile.exists()) customFile.readLines().mapNotNull(QuoteText::parse) else emptyList()

    fun addCustom(line: String) {
        val quote = QuoteText.parse(line) ?: return
        customFile.appendText(QuoteText.format(quote) + "\n")
    }

    fun removeCustom(quote: Quote) {
        val rest = custom().filter { it != quote }
        customFile.writeText(rest.joinToString("") { QuoteText.format(it) + "\n" })
    }

    private fun bundled(): List<Quote> {
        val array = context.assets.open("quotes.json").bufferedReader().use { JSONArray(it.readText()) }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Quote(item.getString("text"), item.optString("source"))
        }
    }

    companion object {
        /** Stable for a whole calendar day, so reopening Jo shows the same line. */
        fun ofTheDay(quotes: List<Quote>, day: LocalDate = LocalDate.now()): Quote? =
            if (quotes.isEmpty()) null else quotes[Math.floorMod(day.toEpochDay(), quotes.size.toLong()).toInt()]
    }
}
