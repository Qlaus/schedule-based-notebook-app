package io.github.qlaus.calnotes.core.export

import io.github.qlaus.calnotes.core.ink.InkDoc
import io.github.qlaus.calnotes.core.ink.InkGeometry
import java.util.Base64

/** One note to export; [meta] is a short line such as "Fizik · 17 Kas". Mirrors web/src/export. */
data class ExportNote(val title: String, val markdown: String, val ink: String?, val meta: String)

object ExportMarkdown {
    fun build(notes: List<ExportNote>): String = notes.joinToString("\n\n---\n\n") { n ->
        buildList {
            add("# ${n.title.ifBlank { "Not" }}")
            if (n.meta.isNotBlank()) add("*${n.meta}*")
            if (n.markdown.isNotBlank()) add(n.markdown.trim())
            InkDoc.parse(n.ink)?.takeIf { !it.isEmpty }?.let { doc ->
                val b64 = Base64.getEncoder().encodeToString(InkGeometry.svg(doc).toByteArray(Charsets.UTF_8))
                add("![El yazısı](data:image/svg+xml;base64,$b64)")
            }
        }.joinToString("\n\n")
    } + "\n"

    fun safeFileName(name: String): String = name.trim().ifEmpty { "not" }.replace(Regex("[\\\\/:*?\"<>|]+"), "-").take(80)

    /** Plain-text preview of a Markdown body (lists, cards). Mirrors web/src/core/preview.ts. */
    fun plainPreview(markdown: String): String = markdown
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+\\[( |x|X)]\\s+")) { if (it.groupValues[1].isBlank()) "☐ " else "☑ " }
        .replace(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+"), "")
        .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
        .replace(Regex("(\\*|_)(.+?)\\1"), "$2")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!~])"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}
