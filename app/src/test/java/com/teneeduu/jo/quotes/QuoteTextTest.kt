package com.teneeduu.jo.quotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteTextTest {

    @Test
    fun `splits the source off after a long dash`() {
        assertEquals(Quote("千里之行，始于足下。", "老子"), QuoteText.parse("千里之行，始于足下。 —— 老子"))
    }

    @Test
    fun `accepts a plain double hyphen`() {
        assertEquals(Quote("Stay hungry", "Jobs"), QuoteText.parse("Stay hungry -- Jobs"))
    }

    @Test
    fun `a line without a source keeps all its words`() {
        assertEquals(Quote("今天也要加油", ""), QuoteText.parse("  今天也要加油  "))
    }

    @Test
    fun `blank lines are skipped`() {
        assertNull(QuoteText.parse("   "))
    }

    @Test
    fun `only the last dash separates the source`() {
        assertEquals(Quote("一——二", "三"), QuoteText.parse("一——二——三"))
    }

    @Test
    fun `a leading dash is not treated as a separator`() {
        assertEquals(Quote("—— 只有出处", ""), QuoteText.parse("—— 只有出处"))
    }

    @Test
    fun `format and parse round trip`() {
        val quote = Quote("业精于勤，荒于嬉", "韩愈")
        assertEquals(quote, QuoteText.parse(QuoteText.format(quote)))
    }
}
