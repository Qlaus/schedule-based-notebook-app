package io.github.qlaus.calnotes.core.calendar

/**
 * Marks Google Calendar events that have notes, without ever damaging the user's own text:
 * - title: an optional "📝 " prefix,
 * - description: an optional delimited block appended at the very end.
 * Both are added and removed only by these functions, so they are always recognisable.
 */
object CalendarMarker {
    const val TITLE_PREFIX = "📝 "
    const val DESCRIPTION_BLOCK = "📝 Notes attached (Not Takvimi)"

    /** Private extended property holding the attached note ids (comma separated). */
    const val NOTE_IDS_PROPERTY = "calnotesNoteIds"

    /** Google limits a private property value to 1024 characters. */
    private const val MAX_PROPERTY_LENGTH = 1024

    data class Stripped(val text: String, val hadMarker: Boolean)

    fun stripTitle(title: String): Stripped =
        if (title.startsWith(TITLE_PREFIX)) Stripped(title.removePrefix(TITLE_PREFIX), true) else Stripped(title, false)

    fun applyTitle(title: String, marked: Boolean): String {
        val base = stripTitle(title).text
        return if (marked) TITLE_PREFIX + base else base
    }

    fun stripDescription(description: String): Stripped {
        val trimmedEnd = description.trimEnd()
        if (!trimmedEnd.endsWith(DESCRIPTION_BLOCK)) return Stripped(description, false)
        var rest = trimmedEnd.removeSuffix(DESCRIPTION_BLOCK)
        // Remove the separator we added (plain newlines, or <br> after Google's web editor converted it).
        while (true) {
            val next = rest.trimEnd(' ', '\n', '\r').removeSuffix("<br>").removeSuffix("<br/>").removeSuffix("<br />")
            if (next == rest) break
            rest = next
        }
        return Stripped(rest, true)
    }

    fun applyDescription(description: String, marked: Boolean): String {
        val base = stripDescription(description).text
        return when {
            !marked -> base
            base.isEmpty() -> DESCRIPTION_BLOCK
            else -> base + "\n\n" + DESCRIPTION_BLOCK
        }
    }

    /** Joins note ids for the extended property, dropping ids that would exceed Google's size limit. */
    fun encodeNoteIds(ids: List<String>): String {
        val sb = StringBuilder()
        for (id in ids.sorted()) {
            val extra = if (sb.isEmpty()) id.length else id.length + 1
            if (sb.length + extra > MAX_PROPERTY_LENGTH) break
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(id)
        }
        return sb.toString()
    }
}
