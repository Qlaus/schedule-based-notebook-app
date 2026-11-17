package io.github.qlaus.calnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun eventLinkDao(): EventLinkDao
    abstract fun pendingEventOpDao(): PendingEventOpDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "calnotes.db"
    }
}
