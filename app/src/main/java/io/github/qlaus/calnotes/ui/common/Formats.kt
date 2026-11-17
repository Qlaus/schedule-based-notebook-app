package io.github.qlaus.calnotes.ui.common

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Date/time formatting in the device locale (Turkish on the target device); 24-hour clock. */
object Formats {
    private val locale: Locale get() = Locale.getDefault()

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun monthYear(date: LocalDate): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(date).replaceFirstChar { it.titlecase(locale) }

    fun dayLong(date: LocalDate): String =
        DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale).format(date)

    fun dayMedium(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMMM EEEE", locale).format(date)

    fun dayShort(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", locale).format(date)

    fun dateField(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM yyyy, EEE", locale).format(date)

    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) {
            "${start.dayOfMonth} – ${DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(end)}"
        } else {
            "${dayShort(start)} – ${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(end)}"
        }
    }

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Monday-based week start. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Several spellings of a date so searches like "12 kasım", "2026-11" or "12.11.2026" match. */
    fun searchDates(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = date(millis, zone)
        return listOf("d MMMM yyyy EEEE", "d MMM", "yyyy-MM-dd", "dd.MM.yyyy", "d.M.yyyy", "LLLL")
            .joinToString(" ") { DateTimeFormatter.ofPattern(it, locale).format(d) }
    }

    fun parseColor(hex: String?, fallback: Color): Color =
        hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback
}
