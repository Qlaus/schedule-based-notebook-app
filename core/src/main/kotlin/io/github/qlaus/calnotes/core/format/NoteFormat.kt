package io.github.qlaus.calnotes.core.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.time.Instant

/**
 * Platform-independent storage format (see docs/note-format.md). Shared by every client that
 * reads the Google Drive appDataFolder, so it must stay backward compatible: add optional fields
 * only, and bump [FORMAT_VERSION] for anything a v1 reader could misinterpret.
 */
object NoteFormat {
    /** Highest version this client reads. Notes with handwriting are written as v2, others as v1. */
    const val FORMAT_VERSION = 2

    fun formatVersionFor(hasInk: Boolean) = if (hasInk) 2 else 1

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun noteMetaName(id: String) = "note-$id.json"
    fun noteBodyName(id: String) = "note-$id.md"
    fun notebookName(id: String) = "notebook-$id.json"
    fun noteInkName(id: String) = "note-$id.ink.json"

    fun encodeNote(file: NoteFileV1): String = json.encodeToString(NoteFileV1.serializer(), file)
    fun encodeNotebook(file: NotebookFileV1): String = json.encodeToString(NotebookFileV1.serializer(), file)

    /** Returns null for files written by a newer, unknown format version (they must not be overwritten). */
    fun decodeNote(text: String): NoteFileV1? = decodeIfSupported(text) { json.decodeFromString(NoteFileV1.serializer(), it) }

    fun decodeNotebook(text: String): NotebookFileV1? =
        decodeIfSupported(text) { json.decodeFromString(NotebookFileV1.serializer(), it) }

    private fun <T> decodeIfSupported(text: String, decode: (String) -> T): T? {
        val version = (json.parseToJsonElement(text) as? JsonObject)?.get("formatVersion")?.jsonPrimitive?.intOrNull ?: return null
        if (version > FORMAT_VERSION) return null
        return decode(text)
    }

    fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()
    fun isoOrNull(millis: Long?): String? = millis?.let(::iso)
    fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()
    fun millisOrNull(iso: String?): Long? = iso?.let(::millis)

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

@Serializable
data class NoteFileV1(
    val formatVersion: Int = 1,
    val id: String,
    val notebookId: String? = null,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null,
    val bodySha256: String,
    /** v2: SHA-256 of note-<id>.ink.json when the note has handwriting, else null. */
    val inkSha256: String? = null,
    val links: List<NoteLinkV1> = emptyList(),
)

@Serializable
data class NoteLinkV1(
    val id: String,
    val calendarId: String,
    val eventId: String,
    val recurringEventId: String? = null,
    val instanceStartTime: String? = null,
    /** INSTANCE | SERIES */
    val scope: String,
    val eventTitleSnapshot: String,
    val eventStartSnapshot: String,
    val eventDeleted: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class NotebookFileV1(
    val formatVersion: Int = 1,
    val id: String,
    val title: String,
    val color: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null,
    val eventLinks: List<NotebookLinkV1> = emptyList(),
)

@Serializable
data class NotebookLinkV1(
    val id: String,
    val calendarId: String,
    val eventId: String,
    val recurringEventId: String? = null,
    /** INSTANCE | SERIES */
    val scope: String,
    val eventTitleSnapshot: String,
    val createdAt: String,
    val deletedAt: String? = null,
)
