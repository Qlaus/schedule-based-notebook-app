package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.search.TextSearch
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextSearchTest {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val haystack = "Fizik · 17 Kas | 17 Kasım 2026 Pazartesi 2026-11-17 17.11.2026 | Işık ve optik"

    @Test
    fun allWordsMustMatch() {
        assertTrue(TextSearch.matches(haystack, "fizik kasım", tr))
        assertFalse(TextSearch.matches(haystack, "fizik aralık", tr))
    }

    @Test
    fun turkishCharactersAreFolded() {
        assertTrue(TextSearch.matches(haystack, "kasim", tr))
        assertTrue(TextSearch.matches(haystack, "ISIK", tr))
        assertTrue(TextSearch.matches(haystack, "ışık", Locale.ENGLISH))
    }

    @Test
    fun dateFormats() {
        assertTrue(TextSearch.matches(haystack, "2026-11", tr))
        assertTrue(TextSearch.matches(haystack, "17.11.2026", tr))
        assertTrue(TextSearch.matches(haystack, "17 kasım", tr))
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertFalse(TextSearch.matches(haystack, "   ", tr))
    }
}
