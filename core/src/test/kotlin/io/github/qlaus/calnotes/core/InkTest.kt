package io.github.qlaus.calnotes.core

import io.github.qlaus.calnotes.core.export.ExportMarkdown
import io.github.qlaus.calnotes.core.export.ExportNote
import io.github.qlaus.calnotes.core.ink.InkDoc
import io.github.qlaus.calnotes.core.ink.InkGeometry
import io.github.qlaus.calnotes.core.ink.InkStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InkTest {
    private val line = InkStroke("a", "pen", "#000000", 2f, listOf(0f, 0f, 0.5f, 100f, 0f, 0.5f))
    private val far = InkStroke("b", "pen", "#000000", 2f, listOf(0f, 200f, 0.5f, 100f, 200f, 0.5f))
    private val doc = InkDoc(strokes = listOf(line, far))

    @Test
    fun encodesLikeTheWebApp() {
        // Same compact JSON the web app writes (encodeInk in web/src/editor/ink.ts).
        val text = InkDoc(strokes = listOf(line)).encode()
        assertEquals(
            """{"formatVersion":2,"pageWidth":640,"strokes":[{"id":"a","tool":"pen","color":"#000000","size":2.0,"points":[0.0,0.0,0.5,100.0,0.0,0.5]}]}""",
            text,
        )
        assertEquals(InkDoc(strokes = listOf(line)), InkDoc.parse(text))
        // Web numbers ("size":2) parse too.
        assertEquals(2f, InkDoc.parse("""{"formatVersion":2,"pageWidth":640,"strokes":[{"id":"a","tool":"pen","color":"#000","size":2,"points":[1,2,0.5]}]}""")!!.strokes[0].size)
    }

    @Test
    fun rejectsNewerFormats() {
        assertNull(InkDoc.parse("""{"formatVersion":3,"pageWidth":640,"strokes":[]}"""))
        assertNull(InkDoc.parse("garbage"))
    }

    @Test
    fun eraserRemovesOnlyStrokesItTouches() {
        val after = InkGeometry.eraseAt(doc, 50f, 3f, 4f)
        assertEquals(listOf("b"), after.strokes.map { it.id })
        assertSame(doc, InkGeometry.eraseAt(doc, 50f, 100f, 4f))
    }

    @Test
    fun bottomAndSvg() {
        assertEquals(202f, doc.bottom())
        val svg = InkGeometry.svg(doc)
        assertTrue(svg.startsWith("<svg") && svg.contains("<path"))
    }

    @Test
    fun markdownExportAndPreview() {
        val md = ExportMarkdown.build(listOf(ExportNote("Fizik", "## Yasa\n\n- [x] bitti", doc.encode(), "Fizik · 17 Kas")))
        assertTrue(md.startsWith("# Fizik\n\n*Fizik · 17 Kas*\n\n## Yasa"))
        assertTrue(md.contains("data:image/svg+xml;base64,"))
        assertEquals("Başlık Bir kalın ve italik. madde ☑ bitti ☐ yap bir",
            ExportMarkdown.plainPreview("## Başlık\n\nBir **kalın** ve *italik*.\n\n- madde\n- [x] bitti\n- [ ] yap\n1. bir"))
    }
}
