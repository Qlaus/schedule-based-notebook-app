package io.github.qlaus.calnotes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.local.CalendarEntity
import io.github.qlaus.calnotes.data.local.EventEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

object TestSupport {
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-16T08:00:00Z"), ZoneOffset.UTC)
    val account = object : CurrentAccount {
        override val accountId = "acc"
    }

    fun db(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    fun calendar(id: String = "cal", visible: Boolean = true) =
        CalendarEntity(id, "acc", "Ders", "#3B6FE0", "owner", isPrimary = true, visible = visible)

    fun event(
        id: String,
        start: Long,
        end: Long = start + 3_600_000,
        recurringEventId: String? = null,
        calendarId: String = "cal",
        title: String = "Fizik",
    ) = EventEntity(
        calendarId = calendarId, id = id, recurringEventId = recurringEventId,
        originalStartMillis = if (recurringEventId != null) start else null,
        title = title, description = "", location = "", startMillis = start, endMillis = end,
        allDay = false, timeZone = "UTC", colorId = null, status = "confirmed", canEdit = true,
        htmlLink = null, updated = null, pendingSync = false,
    )
}
