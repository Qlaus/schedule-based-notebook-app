package io.github.qlaus.calnotes.data

import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.data.repo.toRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotebookRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var notes: NoteRepository

    private val monday = TestSupport.event("lec_1", 1_000_000, recurringEventId = "lec")
    private val tuesday = TestSupport.event("lec_2", 90_000_000, recurringEventId = "lec")
    private val other = TestSupport.event("meeting", 5_000_000)

    @Before
    fun setUp() {
        db = TestSupport.db()
        notebooks = NotebookRepository(db, TestSupport.clock, TestSupport.account)
        notes = NoteRepository(db, TestSupport.clock, TestSupport.account)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seriesLinkPlacesEveryOccurrenceInNotebook() = runBlocking {
        val nb = notebooks.create("Fizik")
        notebooks.linkEvent(monday, EventLinkScope.SERIES, nb)
        assertEquals(nb, notebooks.notebookIdFor(tuesday.toRef()))
        assertNull(notebooks.notebookIdFor(other.toRef()))
        assertEquals("Fizik", notebooks.observeLinkFor(tuesday.toRef()).first()!!.notebook.title)
    }

    @Test
    fun instanceLinkOnlyAppliesToThatOccurrence() = runBlocking {
        val nb = notebooks.create("Sınav")
        notebooks.linkEvent(monday, EventLinkScope.INSTANCE, nb)
        assertEquals(nb, notebooks.notebookIdFor(monday.toRef()))
        assertNull(notebooks.notebookIdFor(tuesday.toRef()))
    }

    @Test
    fun notesInNotebookAreListed() = runBlocking {
        val nb = notebooks.create("Fizik")
        notes.createForEvent(monday, EventLinkScope.INSTANCE, "Ders 1", nb)
        notes.createStandalone(nb, "Özet")
        notes.createStandalone(null, "Serbest")
        assertEquals(setOf("Ders 1", "Özet"), notes.observeNotesIn(nb).first().map { it.note.title }.toSet())
        assertEquals(listOf("Serbest"), notes.observeNotesIn(null).first().map { it.note.title })
        assertEquals(2, notebooks.notebooks.first().single().noteCount)
    }

    @Test
    fun deletingNotebookKeepsNotesAndRemovesLinks() = runBlocking {
        val nb = notebooks.create("Fizik")
        notebooks.linkEvent(monday, EventLinkScope.SERIES, nb)
        val noteId = notes.createForEvent(monday, EventLinkScope.INSTANCE, "Ders 1", nb)
        notebooks.delete(nb)
        assertTrue(notebooks.notebooks.first().isEmpty())
        assertNull(notebooks.notebookIdFor(tuesday.toRef()))
        val note = db.noteDao().get(noteId)!!
        assertNull(note.notebookId)
        assertNull(note.deletedAt)
    }

    @Test
    fun moveNoteBetweenNotebooks() = runBlocking {
        val a = notebooks.create("A")
        val id = notes.createStandalone(null)
        notes.moveToNotebook(id, a)
        assertEquals(a, db.noteDao().get(id)!!.notebookId)
        assertEquals(2, db.noteDao().get(id)!!.version)
    }
}
