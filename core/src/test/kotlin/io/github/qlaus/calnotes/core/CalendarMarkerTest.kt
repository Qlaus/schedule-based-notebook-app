package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.calendar.CalendarMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarMarkerTest {
    @Test
    fun titlePrefixIsAddedOnceAndRemovedCleanly() {
        val marked = CalendarMarker.applyTitle("Fizik", true)
        assertEquals("📝 Fizik", marked)
        assertEquals("📝 Fizik", CalendarMarker.applyTitle(marked, true))
        assertEquals("Fizik", CalendarMarker.applyTitle(marked, false))
        assertEquals(CalendarMarker.Stripped("Fizik", true), CalendarMarker.stripTitle(marked))
    }

    @Test
    fun userEmojiInMiddleOfTitleIsUntouched() {
        assertEquals("Ders 📝 notlar", CalendarMarker.applyTitle("Ders 📝 notlar", false))
    }

    @Test
    fun descriptionBlockKeepsUserText() {
        val user = "Sınav hazırlığı\nKonu: Optik"
        val marked = CalendarMarker.applyDescription(user, true)
        assertTrue(marked.startsWith(user))
        assertTrue(marked.endsWith(CalendarMarker.DESCRIPTION_BLOCK))
        assertEquals(user, CalendarMarker.applyDescription(marked, false))
        assertEquals(marked, CalendarMarker.applyDescription(marked, true))
    }

    @Test
    fun descriptionBlockOnEmptyDescription() {
        assertEquals(CalendarMarker.DESCRIPTION_BLOCK, CalendarMarker.applyDescription("", true))
        assertEquals("", CalendarMarker.applyDescription(CalendarMarker.DESCRIPTION_BLOCK, false))
    }

    @Test
    fun descriptionBlockAfterGoogleHtmlConversion() {
        val html = "Konu: Optik<br><br>" + CalendarMarker.DESCRIPTION_BLOCK
        val stripped = CalendarMarker.stripDescription(html)
        assertTrue(stripped.hadMarker)
        assertEquals("Konu: Optik", stripped.text)
    }

    @Test
    fun descriptionWithoutBlockIsNotMarked() {
        assertFalse(CalendarMarker.stripDescription("Notes attached elsewhere").hadMarker)
    }

    @Test
    fun noteIdsAreSortedAndBounded() {
        assertEquals("a,b", CalendarMarker.encodeNoteIds(listOf("b", "a")))
        val many = (1..100).map { "%036d".format(it) }
        assertTrue(CalendarMarker.encodeNoteIds(many).length <= 1024)
    }
}
