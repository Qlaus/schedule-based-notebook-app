package io.github.qlaus.calnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotebookEntity::class,
        NoteEntity::class,
        EventLinkEntity::class,
        NoteBackupEntity::class,
        CalendarEntity::class,
        EventEntity::class,
        PendingEventOpEntity::class,
        SyncStateEntity::class,
        NotebookLinkEntity::class,
        EventMarkerEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun eventLinkDao(): EventLinkDao
    abstract fun pendingEventOpDao(): PendingEventOpDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun notebookDao(): NotebookDao
    abstract fun eventMarkerDao(): EventMarkerDao
    abstract fun noteBackupDao(): NoteBackupDao

    companion object {
        const val NAME = "calnotes.db"

        /** v1 → v2 (Phase 2): notebook links, calendar markers, marker flags on cached events. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notebook_links` (`id` TEXT NOT NULL, `notebookId` TEXT NOT NULL, " +
                        "`accountId` TEXT NOT NULL, `calendarId` TEXT NOT NULL, `eventId` TEXT NOT NULL, " +
                        "`recurringEventId` TEXT, `scope` TEXT NOT NULL, `eventTitleSnapshot` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notebook_links_notebookId` ON `notebook_links` (`notebookId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_markers` (`calendarId` TEXT NOT NULL, `eventId` TEXT NOT NULL, " +
                        "`titleApplied` INTEGER NOT NULL, `descriptionApplied` INTEGER NOT NULL, `noteIds` TEXT NOT NULL, " +
                        "`skipped` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`calendarId`, `eventId`))",
                )
                db.execSQL("ALTER TABLE `events` ADD COLUMN `markerInTitle` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `markerInDescription` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3 (Phase 3): Drive sync bookkeeping for notebooks. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `remoteVersion` INTEGER")
            }
        }

        /** v3 → v4 (Phase 5): handwriting layer on notes. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `ink` TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
