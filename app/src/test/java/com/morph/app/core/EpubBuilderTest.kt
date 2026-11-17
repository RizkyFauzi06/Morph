package com.morph.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubBuilderTest {

    @Test
    fun buildsValidEpubStructure() {
        val text = "Chapter 1\n\nTom & \"Jerry\" <3 it's\nsecond line\n\nChapter 2\n\nEnd.\u0001"
        val chapters = EpubBuilder.splitChapters(text) { "Part $it" }
        assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })

        val out = File.createTempFile("morph", ".epub")
        try {
            EpubBuilder.build(chapters, "A & B", "en", out)
            ZipFile(out).use { zip ->
                val first = zip.entries().nextElement()
                assertEquals("mimetype", first.name)
                assertEquals(ZipEntry.STORED, first.method)
                assertEquals("application/epub+zip", zip.getInputStream(first).readBytes().toString(Charsets.US_ASCII))

                listOf("META-INF/container.xml", "OEBPS/content.opf", "OEBPS/toc.ncx",
                    "OEBPS/chapter_1.xhtml", "OEBPS/chapter_2.xhtml").forEach {
                    assertTrue("missing $it", zip.getEntry(it) != null)
                }
                val ch1 = zip.getInputStream(zip.getEntry("OEBPS/chapter_1.xhtml")).readBytes().toString(Charsets.UTF_8)
                assertTrue(ch1.contains("Tom &amp; &quot;Jerry&quot; &lt;3 it&apos;s<br/>second line"))
                val ch2 = zip.getInputStream(zip.getEntry("OEBPS/chapter_2.xhtml")).readBytes().toString(Charsets.UTF_8)
                assertFalse("control char must be stripped", ch2.contains('\u0001'))
                assertTrue(zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes()
                    .toString(Charsets.UTF_8).contains("<dc:title>A &amp; B</dc:title>"))
            }
        } finally {
            out.delete()
        }
    }

    @Test
    fun proseIsNotAHeadingAndLongTextIsChunked() {
        val text = (1..130).joinToString("\n\n") { "Part of paragraph $it." }
        val chapters = EpubBuilder.splitChapters(text) { "Section $it" }
        assertEquals(3, chapters.size) // 60 + 60 + 10
        assertEquals("Section 1", chapters.first().title)
    }

    @Test
    fun decodesLegacyAnsiText() {
        val ansi = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte()) // "Café" in Windows-1252
        assertEquals("Café", EpubBuilder.decodeText(ansi))
        assertEquals("Hi", EpubBuilder.decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x48, 0x69)))
    }
}
