package io.github.qlaus.calnotes.core.search

import java.util.Locale

/**
 * Simple multi-word search: every word of the query must appear somewhere in the haystack.
 * Case-insensitive with locale-aware lower-casing (Turkish İ/ı) and Turkish characters folded,
 * so "kasim" finds "Kasım" and "ISIK" finds "ışık".
 */
object TextSearch {
    fun normalize(text: String, locale: Locale): String =
        text.lowercase(locale)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace("i̇", "i") // dotted capital I lower-cased in non-Turkish locales

    fun tokens(query: String, locale: Locale): List<String> =
        normalize(query, locale).split(Regex("[\\s,]+")).filter { it.isNotBlank() }

    fun matches(haystack: String, query: String, locale: Locale): Boolean {
        val terms = tokens(query, locale)
        if (terms.isEmpty()) return false
        val text = normalize(haystack, locale)
        return terms.all { text.contains(it) }
    }
}
