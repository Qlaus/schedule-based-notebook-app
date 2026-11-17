package io.github.qlaus.calnotes.data

import io.github.qlaus.calnotes.core.format.NoteFileV1
import io.github.qlaus.calnotes.core.format.NoteFormat
import io.github.qlaus.calnotes.core.format.NoteLinkV1
import io.github.qlaus.calnotes.core.model.EventLinkScope
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.remote.drive.DriveApi
import io.github.qlaus.calnotes.data.repo.DriveSync
import io.github.qlaus.calnotes.data.repo.NoteRepository
import io.github.qlaus.calnotes.data.repo.NotebookRepository
import io.github.qlaus.calnotes.data.repo.toRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A tiny in-memory fake of the Drive appDataFolder endpoints used by [DriveSync]. */
private class FakeDrive : Dispatcher() {
    data class File(val id: String, val name: String, var props: Map<String, String>, var content: String)

    val files = LinkedHashMap<String, File>()
    val requests = CopyOnWriteArrayList<String>()
    private var nextId = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun put(name: String, props: Map<String, String>, content: String): File {
        val f = File("f${nextId++}", name, props, content)
        files[f.id] = f
        return f
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        val method = request.method!!
        requests += "$method $path"
        return when {
            method == "GET" && path == "/drive/v3/files" -> ok(
                """{"files":[${files.values.joinToString(",") { f ->
                    """{"id":"${f.id}","name":"${f.name}","appProperties":${json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), propsJson(f.props))}}"""
                }}]}""",
            )
            method == "GET" && path.startsWith("/drive/v3/files/") -> {
                val f = files[path.removePrefix("/drive/v3/files/")] ?: return MockResponse().setResponseCode(404)
                MockResponse().setResponseCode(200).setBody(f.content)
            }
            method == "POST" && path == "/upload/drive/v3/files" -> {
                val (meta, content) = parts(request)
                val f = put(meta["name"]!!.toString().trim('"'), propsOf(meta), content)
                ok("""{"id":"${f.id}","name":"${f.name}"}""")
            }
            method == "PATCH" && path.startsWith("/upload/drive/v3/files/") -> {
                val f = files[path.removePrefix("/upload/drive/v3/files/")] ?: return MockResponse().setResponseCode(404)
                val (meta, content) = parts(request)
                f.props = f.props + propsOf(meta)
                f.content = content
                ok("""{"id":"${f.id}","name":"${f.name}"}""")
            }
            else -> MockResponse().setResponseCode(400)
        }
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun propsJson(p: Map<String, String>) =
        kotlinx.serialization.json.JsonObject(p.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })

    private fun propsOf(meta: kotlinx.serialization.json.JsonObject): Map<String, String> =
        (meta["appProperties"] as? kotlinx.serialization.json.JsonObject)?.mapValues { it.value.toString().trim('"') }.orEmpty()

    /** Splits a multipart/related upload into (metadata JSON, content). */
    private fun parts(request: RecordedRequest): Pair<kotlinx.serialization.json.JsonObject, String> {
        val boundary = request.getHeader("Content-Type")!!.substringAfter("boundary=")
        val body = request.body.readUtf8()
        // Each part: "\r\n<headers>\r\n\r\n<payload>\r\n"; keep empty payloads intact.
        val sections = body.split("--$boundary").drop(1).filter { !it.startsWith("--") }
        fun payload(section: String) = section.removePrefix("\r\n").substringAfter("\r\n\r\n").removeSuffix("\r\n")
        val meta = json.parseToJsonElement(payload(sections[0])) as kotlinx.serialization.json.JsonObject
        return meta to payload(sections[1])
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var drive: FakeDrive
    private lateinit var db: AppDatabase
    private lateinit var sync: DriveSync
    private lateinit var notes: NoteRepository
    private lateinit var notebooks: NotebookRepository
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before
    fun setUp() {
        drive = FakeDrive()
        server = MockWebServer().apply { dispatcher = drive; start() }
        db = TestSupport.db()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriveApi::class.java)
        sync = DriveSync(api, db, json, TestSupport.clock, TestSupport.account)
        notes = NoteRepository(db, TestSupport.clock, TestSupport.account)
        notebooks = NotebookRepository(db, TestSupport.clock, TestSupport.account)
    }

    @After
    fun tearDown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun props(id: String, version: Long, updatedAt: Long, kind: String = "note") =
        mapOf("kind" to kind, "entityId" to id, "version" to "$version", "updatedAt" to "$updatedAt", "deleted" to "false")

    private fun remoteNote(id: String, title: String, body: String, version: Long, updatedAt: Long, hash: String = NoteFormat.sha256(body)) {
        val file = NoteFileV1(
            id = id, title = title, createdAt = NoteFormat.iso(0), updatedAt = NoteFormat.iso(updatedAt),
            version = version, bodySha256 = hash,
            links = listOf(
                NoteLinkV1(
                    id = "l-$id", calendarId = "cal", eventId = "ev", scope = "INSTANCE", eventTitleSnapshot = "Fizik",
                    eventStartSnapshot = NoteFormat.iso(5000), createdAt = NoteFormat.iso(0), updatedAt = NoteFormat.iso(0),
                ),
            ),
        )
        drive.put(NoteFormat.noteBodyName(id), props(id, version, updatedAt), body)
        drive.put(NoteFormat.noteMetaName(id), props(id, version, updatedAt), NoteFormat.encodeNote(file))
    }

    @Test
    fun newLocalNoteIsUploadedBodyFirstThenMetadata() = runBlocking {
        val id = notes.createStandalone(null, "Fizik")
        notes.updateContent(id, "Fizik", "Newton yasaları")

        val result = sync.sync()

        assertEquals(1, result.uploaded)
        val uploads = drive.requests.filter { it.startsWith("POST") }
        assertEquals(2, uploads.size)
        val body = drive.files.values.single { it.name == NoteFormat.noteBodyName(id) }
        val meta = drive.files.values.single { it.name == NoteFormat.noteMetaName(id) }
        assertEquals("Newton yasaları", body.content)
        assertTrue(body.id < meta.id, "body must be written before metadata")
        val file = NoteFormat.decodeNote(meta.content)!!
        assertEquals(NoteFormat.sha256("Newton yasaları"), file.bodySha256)
        assertEquals("2", meta.props["version"])

        val local = db.noteDao().get(id)!!
        assertFalse(local.dirty)
        assertEquals(2, local.remoteVersion)

        // Second run: nothing to do, only the listing request.
        drive.requests.clear()
        assertEquals(0, sync.sync().uploaded)
        assertEquals(listOf("GET /drive/v3/files"), drive.requests.toList())
    }

    @Test
    fun remoteNoteIsDownloadedWithLinks() = runBlocking {
        remoteNote("n1", "Fizik · 17 Kas", "Optik", version = 3, updatedAt = 100)
        assertEquals(1, sync.sync().downloaded)
        val note = notes.observeNote("n1").first()!!
        assertEquals("Optik", note.note.body)
        assertEquals(3, note.note.version)
        assertFalse(note.note.dirty)
        assertEquals("ev", note.links.single().eventId)
    }

    @Test
    fun halfUploadedNoteIsSkippedUntilBodyMatches() = runBlocking {
        remoteNote("n1", "Fizik", "Optik", version = 3, updatedAt = 100, hash = NoteFormat.sha256("something else"))
        val result = sync.sync()
        assertEquals(1, result.skipped)
        assertNull(db.noteDao().get("n1"))
    }

    @Test
    fun remoteEditReplacesUnchangedLocalNote() = runBlocking {
        remoteNote("n1", "Fizik", "v1", version = 1, updatedAt = 100)
        sync.sync()
        val meta = drive.files.values.single { it.name == NoteFormat.noteMetaName("n1") }
        val body = drive.files.values.single { it.name == NoteFormat.noteBodyName("n1") }
        body.content = "v2 from tablet B"
        meta.props = props("n1", 2, 200)
        meta.content = NoteFormat.encodeNote(NoteFormat.decodeNote(meta.content)!!.copy(version = 2, bodySha256 = NoteFormat.sha256(body.content)))

        sync.sync()
        assertEquals("v2 from tablet B", db.noteDao().get("n1")!!.body)
    }

    @Test
    fun conflictNewerRemoteWinsAndLocalIsBackedUp() = runBlocking {
        remoteNote("n1", "Fizik", "base", version = 1, updatedAt = 100)
        sync.sync()
        // Local edit at clock time (2026-11-16T08:00Z) …
        notes.updateContent("n1", "Fizik", "local edit")
        // … and a later remote edit.
        val later = TestSupport.clock.millis() + 60_000
        val meta = drive.files.values.single { it.name == NoteFormat.noteMetaName("n1") }
        val body = drive.files.values.single { it.name == NoteFormat.noteBodyName("n1") }
        body.content = "remote edit"
        meta.props = props("n1", 2, later)
        meta.content = NoteFormat.encodeNote(
            NoteFormat.decodeNote(meta.content)!!.copy(version = 2, updatedAt = NoteFormat.iso(later), bodySha256 = NoteFormat.sha256("remote edit")),
        )

        val result = sync.sync()
        assertEquals(1, result.conflicts)
        assertEquals("remote edit", db.noteDao().get("n1")!!.body)
        val backup = db.noteBackupDao().observeForNote("n1").first().single()
        assertEquals("local edit", backup.body)
    }

    @Test
    fun conflictNewerLocalWinsAndRemoteIsBackedUp() = runBlocking {
        remoteNote("n1", "Fizik", "base", version = 1, updatedAt = 100)
        sync.sync()
        val meta = drive.files.values.single { it.name == NoteFormat.noteMetaName("n1") }
        val body = drive.files.values.single { it.name == NoteFormat.noteBodyName("n1") }
        body.content = "older remote edit"
        meta.props = props("n1", 2, 200) // long before the local edit below
        meta.content = NoteFormat.encodeNote(
            NoteFormat.decodeNote(meta.content)!!.copy(version = 2, updatedAt = NoteFormat.iso(200), bodySha256 = NoteFormat.sha256(body.content)),
        )
        notes.updateContent("n1", "Fizik", "newer local edit")

        val result = sync.sync()
        assertEquals(1, result.conflicts)
        assertEquals("newer local edit", body.content)
        assertEquals("3", meta.props["version"]) // max(local 2, remote 2) + 1
        assertEquals("older remote edit", db.noteBackupDao().observeForNote("n1").first().single().body)
        assertFalse(db.noteDao().get("n1")!!.dirty)
    }

    @Test
    fun deleteTravelsAsTombstone() = runBlocking {
        val id = notes.createStandalone(null, "Silinecek")
        sync.sync()
        notes.delete(id)
        sync.sync()
        val meta = drive.files.values.single { it.name == NoteFormat.noteMetaName(id) }
        assertEquals("true", meta.props["deleted"])
        assertNotNull(NoteFormat.decodeNote(meta.content)!!.deletedAt)

        // A fresh device learns about the deletion and does not show the note.
        val other = TestSupport.db()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(DriveApi::class.java)
            DriveSync(api, other, json, TestSupport.clock, TestSupport.account).sync()
            assertNotNull(other.noteDao().get(id)!!.deletedAt)
            assertNull(NoteRepository(other, TestSupport.clock, TestSupport.account).observeNote(id).first())
        } finally {
            other.close()
        }
    }

    @Test
    fun notebooksAndSeriesLinksSyncToAnotherDevice() = runBlocking {
        val nb = notebooks.create("Fizik", "#3B6FE0")
        val lecture = TestSupport.event("lec_1", 1_000_000, recurringEventId = "lec")
        notebooks.linkEvent(lecture, EventLinkScope.SERIES, nb)
        notes.createForEvent(lecture, EventLinkScope.INSTANCE, "Ders 1", nb)
        sync.sync()

        val other = TestSupport.db()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(DriveApi::class.java)
            val result = DriveSync(api, other, json, TestSupport.clock, TestSupport.account).sync()
            assertEquals(2, result.downloaded)
            val otherNotebooks = NotebookRepository(other, TestSupport.clock, TestSupport.account)
            assertEquals("Fizik", otherNotebooks.notebooks.first().single().notebook.title)
            val nextWeek = TestSupport.event("lec_2", 90_000_000, recurringEventId = "lec")
            assertEquals(nb, otherNotebooks.notebookIdFor(nextWeek.toRef()))
            assertEquals(1, otherNotebooks.notebooks.first().single().noteCount)
        } finally {
            other.close()
        }
    }
}
