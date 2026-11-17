package io.github.qlaus.calnotes.data

import io.github.qlaus.calnotes.core.calendar.CalendarMarker
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.remote.calendar.CalendarApi
import io.github.qlaus.calnotes.data.repo.MarkerSync
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.ZoneProvider
import io.github.qlaus.calnotes.data.settings.AppSettings
import io.github.qlaus.calnotes.data.settings.SettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkerSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var sync: MarkerSync
    private lateinit var notes: NoteRepository
    private val settings = MutableStateFlow(AppSettings(titleMarker = true, descriptionMarker = false))
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val event = TestSupport.event("a", 1_000_000)

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
        val source = object : SettingsSource {
            override val settings = this@MarkerSyncTest.settings
        }
        sync = MarkerSync(api, db, source, TestSupport.clock, ZoneProvider { ZoneOffset.UTC })
        notes = NoteRepository(db, TestSupport.clock, TestSupport.account)
        runBlocking {
            db.calendarDao().upsertAll(listOf(TestSupport.calendar()))
            db.eventDao().upsertAll(listOf(event))
        }
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun eventJson(summary: String, organizerSelf: Boolean = true) =
        """{"id":"a","status":"confirmed","summary":"$summary","description":"Konu: Optik",
           "organizer":{"email":"x@y.z","self":$organizerSelf},
           "start":{"dateTime":"2026-11-17T09:00:00Z"},"end":{"dateTime":"2026-11-17T10:00:00Z"}}"""

    @Test
    fun addsTitlePrefixAndNoteIdsThenRemovesThem() = runBlocking {
        val noteId = notes.createForEvent(event, EventLinkScope.INSTANCE, "Fizik")

        server.enqueue(ok(eventJson("Fizik")))      // GET
        server.enqueue(ok(eventJson("📝 Fizik")))   // PATCH
        sync.reconcile()
        server.takeRequest() // GET
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        val body = patch.body.readUtf8()
        assertTrue(body.contains("\"summary\":\"📝 Fizik\""), body)
        assertTrue(body.contains("\"${CalendarMarker.NOTE_IDS_PROPERTY}\":\"$noteId\""), body)
        assertTrue(!body.contains("description"), "user description must not be touched: $body")
        assertTrue(db.eventDao().get("cal", "a")!!.markerInTitle)

        // Nothing changed -> no requests.
        val before = server.requestCount
        sync.reconcile()
        assertEquals(before, server.requestCount)

        notes.delete(noteId)
        server.enqueue(ok(eventJson("📝 Fizik")))
        server.enqueue(ok(eventJson("Fizik")))
        sync.reconcile()
        server.takeRequest()
        val unmark = server.takeRequest().body.readUtf8()
        assertTrue(unmark.contains("\"summary\":\"Fizik\""), unmark)
        assertTrue(unmark.contains("\"${CalendarMarker.NOTE_IDS_PROPERTY}\":null"), unmark)
        assertEquals(emptyList(), db.eventMarkerDao().all())
    }

    @Test
    fun descriptionBlockKeepsUserText() = runBlocking {
        settings.value = AppSettings(titleMarker = false, descriptionMarker = true)
        notes.createForEvent(event, EventLinkScope.INSTANCE, "Fizik")
        server.enqueue(ok(eventJson("Fizik")))
        server.enqueue(ok(eventJson("Fizik")))
        sync.reconcile()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Konu: Optik\\n\\n${CalendarMarker.DESCRIPTION_BLOCK}"), body)
        assertTrue(!body.contains("summary"), body)
    }

    @Test
    fun eventsWeCannotEditAreSkippedOnce() = runBlocking {
        notes.createForEvent(event, EventLinkScope.INSTANCE, "Fizik")
        server.enqueue(ok(eventJson("Fizik", organizerSelf = false)))
        sync.reconcile()
        assertEquals(1, server.requestCount) // GET only, no PATCH
        assertTrue(db.eventMarkerDao().all().single().skipped)
        sync.reconcile()
        assertEquals(1, server.requestCount)
    }
}
