package io.github.qlaus.calnotes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.qlaus.calnotes.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Builds a database exactly as Room v1 (Phase 1) created it, with user data, then opens it with
 * the current Room schema. Room runs MIGRATION_1_2 and validates every table against the entities,
 * so a mistake in the migration fails this test instead of wiping notes on the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `notebooks` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `title` TEXT NOT NULL, `color` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `version` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `remoteFileId` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `notebookId` TEXT, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `version` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `remoteFileId` TEXT, `remoteVersion` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_notes_notebookId` ON `notes` (`notebookId`)",
        "CREATE TABLE IF NOT EXISTS `event_links` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `accountId` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `recurringEventId` TEXT, `instanceStartTime` INTEGER, `scope` TEXT NOT NULL, `eventTitleSnapshot` TEXT NOT NULL, `eventStartSnapshot` INTEGER NOT NULL, `eventDeleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_event_links_noteId` ON `event_links` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_event_links_calendarId_eventId` ON `event_links` (`calendarId`, `eventId`)",
        "CREATE INDEX IF NOT EXISTS `index_event_links_calendarId_recurringEventId` ON `event_links` (`calendarId`, `recurringEventId`)",
        "CREATE TABLE IF NOT EXISTS `note_backups` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `version` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `metaJson` TEXT NOT NULL, `reason` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_note_backups_noteId` ON `note_backups` (`noteId`)",
        "CREATE TABLE IF NOT EXISTS `calendars` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `summary` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `accessRole` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `visible` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `events` (`calendarId` TEXT NOT NULL, `id` TEXT NOT NULL, `recurringEventId` TEXT, `originalStartMillis` INTEGER, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `location` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `allDay` INTEGER NOT NULL, `timeZone` TEXT, `colorId` TEXT, `status` TEXT NOT NULL, `canEdit` INTEGER NOT NULL, `htmlLink` TEXT, `updated` TEXT, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`calendarId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_events_startMillis` ON `events` (`startMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_events_calendarId_recurringEventId` ON `events` (`calendarId`, `recurringEventId`)",
        "CREATE TABLE IF NOT EXISTS `pending_event_ops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `eventId` TEXT NOT NULL, `payloadJson` TEXT, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT)",
        "CREATE TABLE IF NOT EXISTS `sync_state` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-test')",
    )

    @Test
    fun migratesV1ToLatestAndKeepsNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO notes VALUES ('n1','acc',NULL,'Fizik · 17 Kas','Newton yasaları',1,2,NULL,3,1,NULL,NULL)",
            )
            db.execSQL(
                "INSERT INTO event_links VALUES ('l1','n1','acc','cal','ev1',NULL,10,'INSTANCE','Fizik',10,0,1,1,NULL)",
            )
            db.execSQL("INSERT INTO notebooks VALUES ('nb1','acc','Fizik','#3B6FE0',1,1,NULL,1,1,NULL)")
            db.execSQL(
                "INSERT INTO events VALUES ('cal','ev1',NULL,NULL,'Fizik','','',10,20,0,'UTC',NULL,'confirmed',1,NULL,NULL,0)",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val note = room.noteDao().get("n1")
            assertNotNull(note)
            assertEquals("Newton yasaları", note.body)
            assertEquals(3, note.version)
            val event = room.eventDao().get("cal", "ev1")
            assertNotNull(event)
            assertFalse(event.markerInTitle)
            assertEquals(emptyList(), room.notebookDao().activeLinks())
            assertEquals(emptyList(), room.eventMarkerDao().all())
            val notebook = room.notebookDao().get("nb1")
            assertNotNull(notebook)
            assertEquals(null, notebook.remoteVersion)
        } finally {
            room.close()
        }
    }
}
