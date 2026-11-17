package io.github.qlaus.calnotes.data

import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.repo.NoteRepository
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
class NoteRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository

    private val monday = TestSupport.event("lec_1", 1_000_000, recurringEventId = "lec")
    private val tuesday = TestSupport.event("lec_2", 90_000_000, recurringEventId = "lec")

    @Before
    fun setUp() {
        db = TestSupport.db()
        repo = NoteRepository(db, TestSupport.clock, TestSupport.account)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun instanceNoteOnlyShowsOnThatOccurrence() = runBlocking {
        repo.createForEvent(monday, EventLinkScope.INSTANCE, "Fizik")
        assertEquals(1, repo.observeNotesForEvent(monday.toRef()).first().size)
        assertTrue(repo.observeNotesForEvent(tuesday.toRef()).first().isEmpty())
    }

    @Test
    fun seriesNoteShowsOnEveryOccurrence() = runBlocking {
        val id = repo.createForEvent(monday, EventLinkScope.SERIES, "Fizik")
        assertEquals(listOf(id), repo.observeNotesForEvent(tuesday.toRef()).first().map { it.id })
        val link = repo.observeNote(id).first()!!.links.single()
        assertEquals("lec", link.eventId)
        assertEquals(EventLinkScope.SERIES, link.scope)
    }

    @Test
    fun seriesScopeOnSingleEventFallsBackToInstance() = runBlocking {
        val single = TestSupport.event("meeting", 5_000_000)
        val id = repo.createForEvent(single, EventLinkScope.SERIES, "Toplantı")
        assertEquals(EventLinkScope.INSTANCE, repo.observeNote(id).first()!!.links.single().scope)
    }

    @Test
    fun multipleNotesPerEvent() = runBlocking {
        repo.createForEvent(monday, EventLinkScope.INSTANCE, "A")
        repo.createForEvent(monday, EventLinkScope.INSTANCE, "B")
        assertEquals(listOf("A", "B"), repo.observeNotesForEvent(monday.toRef()).first().map { it.title })
    }

    @Test
    fun updateBumpsVersionOnlyWhenChanged() = runBlocking {
        val id = repo.createForEvent(monday, EventLinkScope.INSTANCE, "Fizik")
        repo.updateContent(id, "Fizik", "")
        assertEquals(1, db.noteDao().get(id)!!.version)
        repo.updateContent(id, "Fizik", "Newton yasaları")
        val note = db.noteDao().get(id)!!
        assertEquals(2, note.version)
        assertEquals("Newton yasaları", note.body)
        assertTrue(note.dirty)
    }

    @Test
    fun deleteIsSoftAndHidesNote() = runBlocking {
        val id = repo.createForEvent(monday, EventLinkScope.INSTANCE, "Fizik")
        repo.delete(id)
        assertNull(repo.observeNote(id).first())
        assertTrue(repo.observeNotesForEvent(monday.toRef()).first().isEmpty())
        val tombstone = db.noteDao().get(id)!!
        assertTrue(tombstone.deletedAt != null)
        assertEquals(2, tombstone.version)
    }
}
