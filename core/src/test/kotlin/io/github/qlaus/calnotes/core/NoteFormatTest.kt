package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.format.NoteFileV1
import io.github.qlaus.calnotes.core.format.NoteFormat
import io.github.qlaus.calnotes.core.format.NoteLinkV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteFormatTest {
    private val file = NoteFileV1(
        id = "n1", notebookId = null, title = "Fizik · 17 Kas",
        createdAt = NoteFormat.iso(0), updatedAt = NoteFormat.iso(1000), version = 3,
        bodySha256 = NoteFormat.sha256("Newton"),
        links = listOf(
            NoteLinkV1(
                id = "l1", calendarId = "primary", eventId = "ev", scope = "INSTANCE",
                eventTitleSnapshot = "Fizik", eventStartSnapshot = NoteFormat.iso(5000),
                createdAt = NoteFormat.iso(0), updatedAt = NoteFormat.iso(0),
            ),
        ),
    )

    @Test
    fun roundTrip() {
        val text = NoteFormat.encodeNote(file)
        assertTrue(text.contains("\"formatVersion\": 1"))
        assertEquals(file, NoteFormat.decodeNote(text))
    }

    @Test
    fun unknownFieldsFromNewerMinorWritersAreIgnored() {
        val text = NoteFormat.encodeNote(file).replaceFirst("{", "{\n  \"futureField\": 42,")
        assertEquals(file, NoteFormat.decodeNote(text))
    }

    @Test
    fun newerFormatVersionIsRejected() {
        val text = NoteFormat.encodeNote(file).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertNull(NoteFormat.decodeNote(text))
    }

    @Test
    fun isoTimestampsRoundTrip() {
        assertEquals(1_763_366_400_000, NoteFormat.millis(NoteFormat.iso(1_763_366_400_000)))
    }

    @Test
    fun sha256IsStable() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", NoteFormat.sha256(""))
    }
}
