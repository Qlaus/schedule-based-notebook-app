package io.github.qlaus.calnotes.data

import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.remote.calendar.CalendarApi
import io.github.qlaus.calnotes.data.remote.calendar.EventDraft
import io.github.qlaus.calnotes.data.repo.CalendarRepository
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.ZoneProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: CalendarRepository
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val from = java.time.Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
    private val to = java.time.Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = TestSupport.db()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CalendarApi::class.java)
        repo = CalendarRepository(api, db, json, TestSupport.clock, TestSupport.account, ZoneProvider { ZoneOffset.UTC })
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun eventJson(id: String, start: String, end: String, title: String = "Fizik") =
        """{"id":"$id","status":"confirmed","summary":"$title","start":{"dateTime":"$start"},"end":{"dateTime":"$end"}}"""

    private val draft = EventDraft(
        calendarId = "cal", title = "Yeni", description = "", location = "", allDay = false,
        startDate = LocalDate.of(2026, 11, 17), startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 11, 17), endTime = LocalTime.of(10, 0),
    )

    @Test
    fun refreshCachesCalendarsAndEventsAndRemovesStaleOnes() = runBlocking {
        server.enqueue(ok("""{"items":[{"id":"cal","summary":"Ders","backgroundColor":"#ff0000","accessRole":"owner","primary":true}]}"""))
        server.enqueue(ok("""{"items":[${eventJson("a", "2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z")},${eventJson("b", "2026-11-18T09:00:00Z", "2026-11-18T10:00:00Z")}]}"""))
        repo.refreshCalendars()
        repo.refreshRange(from, to)
        assertEquals(listOf("a", "b"), repo.observeEvents(from, to).first().map { it.event.id })

        server.enqueue(ok("""{"items":[${eventJson("b", "2026-11-18T09:00:00Z", "2026-11-18T10:00:00Z")}]}"""))
        repo.refreshRange(from, to)
        assertEquals(listOf("b"), repo.observeEvents(from, to).first().map { it.event.id })
    }

    @Test
    fun deletedLinkedEventMarksLinkButKeepsNote() = runBlocking {
        db.calendarDao().upsertAll(listOf(TestSupport.calendar()))
        val ev = TestSupport.event("a", java.time.Instant.parse("2026-11-17T09:00:00Z").toEpochMilli())
        db.eventDao().upsertAll(listOf(ev))
        val notes = NoteRepository(db, TestSupport.clock, TestSupport.account)
        val noteId = notes.createForEvent(ev, EventLinkScope.INSTANCE, "Fizik")

        server.enqueue(ok("""{"items":[]}"""))                   // event gone from list
        server.enqueue(MockResponse().setResponseCode(404))     // and from GET
        repo.refreshRange(from, to)

        val note = notes.observeNote(noteId).first()!!
        assertTrue(note.links.single().eventDeleted)
    }

    @Test
    fun offlineCreateIsQueuedThenUploadedAndRemapped() = runBlocking {
        db.calendarDao().upsertAll(listOf(TestSupport.calendar()))
        val localId = repo.createEvent(draft)
        val local = db.eventDao().get("cal", localId)!!
        assertTrue(local.pendingSync)
        assertEquals(1, repo.pendingOpCount.first())

        // Attach a note to the not-yet-uploaded event.
        val notes = NoteRepository(db, TestSupport.clock, TestSupport.account)
        val noteId = notes.createForEvent(local, EventLinkScope.INSTANCE, "Yeni")

        server.enqueue(ok(eventJson("srv1", "2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z", "Yeni")))
        assertTrue(repo.flushOutbox())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"summary\":\"Yeni\""))
        assertEquals(0, repo.pendingOpCount.first())
        assertEquals(null, db.eventDao().get("cal", localId))
        assertFalse(db.eventDao().get("cal", "srv1")!!.pendingSync)
        assertEquals("srv1", notes.observeNote(noteId).first()!!.links.single().eventId)
    }

    @Test
    fun serverErrorKeepsOpButRejectionDropsIt() = runBlocking {
        db.calendarDao().upsertAll(listOf(TestSupport.calendar()))
        repo.createEvent(draft)

        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(repo.flushOutbox())
        assertEquals(1, repo.pendingOpCount.first())

        server.enqueue(MockResponse().setResponseCode(400))
        assertTrue(repo.flushOutbox())
        assertEquals(0, repo.pendingOpCount.first())
        assertEquals("HTTP 400", repo.lastOutboxError.value)
    }

    @Test
    fun allDayPatchSendsExplicitNullDateTime() = runBlocking {
        db.calendarDao().upsertAll(listOf(TestSupport.calendar()))
        val ev = TestSupport.event("a", java.time.Instant.parse("2026-11-17T09:00:00Z").toEpochMilli())
        db.eventDao().upsertAll(listOf(ev))
        repo.updateEvent("a", draft.copy(allDay = true), wholeSeries = false)

        server.enqueue(ok("""{"id":"a","summary":"Yeni","start":{"date":"2026-11-17"},"end":{"date":"2026-11-18"}}"""))
        assertTrue(repo.flushOutbox())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"date\":\"2026-11-17\""), body)
        assertTrue(body.contains("\"dateTime\":null"), body)
        assertTrue(db.eventDao().get("cal", "a")!!.allDay)
    }
}
