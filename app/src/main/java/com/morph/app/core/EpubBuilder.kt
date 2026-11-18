package com.morph.app.core

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * PBI 5.3: builds an EPUB 2.0.1 book from plain text with java.util.zip only.
 * Pure JVM (no Android APIs) so it is unit-testable.
 */
object EpubBuilder {

    data class Chapter(val title: String, val paragraphs: List<String>)

    private const val MIMETYPE = "application/epub+zip"

    /** Chapters without detectable headings are cut every N paragraphs to keep XHTML files small. */
    private const val PARAGRAPHS_PER_CHUNK = 60

    // "Chapter 1", "CHAPTER IV: Title", "Bab 3", "Bagian II", "Part 2", "Prologue", "Epilog".
    // A number is required so prose like "Part of me..." is never taken as a heading.
    private val headingRegex = Regex(
        "^((chapter|bab|bagian|part)\\s+([0-9]+|[ivxlcdm]+)\\b.{0,80}|prolog(ue)?|epilog(ue)?)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Decodes TXT bytes as UTF-8 (BOM stripped) and falls back to Windows-1252 for legacy
     * ANSI files instead of producing U+FFFD garbage. A UTF-8 file with a few damaged bytes
     * (e.g. cut mid-character) stays UTF-8: only those bytes become U+FFFD, not the whole book.
     */
    fun decodeText(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            val lenient = String(bytes, Charsets.UTF_8)
            val broken = lenient.count { it == '\uFFFD' }
            val validNonAscii = lenient.count { it.code >= 0x80 } - broken
            // Mostly valid multi-byte text = damaged UTF-8; mostly broken = a legacy ANSI file
            if (validNonAscii > broken * 10) lenient else String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("\uFEFF")
    }

    /** Splits text into chapters: by heading lines when there are 2+, otherwise by size. */
    fun splitChapters(text: String, chunkTitle: (Int) -> String): List<Chapter> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = normalized.split(Regex("\n\\s*\n"))
            .map { it.trim('\n', ' ', '\t') }
            .filter { it.isNotBlank() }

        val isHeading = { p: String -> !p.contains('\n') && headingRegex.matches(p.trim()) }
        val headingCount = paragraphs.count(isHeading)

        val chapters = mutableListOf<Chapter>()
        if (headingCount >= 2) {
            var title: String? = null
            var body = mutableListOf<String>()
            for (p in paragraphs) {
                if (isHeading(p)) {
                    if (title != null || body.isNotEmpty()) {
                        chapters += Chapter(title ?: chunkTitle(chapters.size + 1), body)
                    }
                    title = p.trim()
                    body = mutableListOf()
                } else {
                    body += p
                }
            }
            if (title != null || body.isNotEmpty()) chapters += Chapter(title ?: chunkTitle(chapters.size + 1), body)
        } else {
            paragraphs.chunked(PARAGRAPHS_PER_CHUNK).forEachIndexed { i, chunk ->
                chapters += Chapter(chunkTitle(i + 1), chunk)
            }
        }
        return chapters
    }

    /**
     * Writes an EPUB to [output].
     * @param checkCancelled called between chapters; throw (e.g. CancellationException) to abort
     */
    fun build(
        chapters: List<Chapter>,
        title: String,
        language: String,
        output: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        checkCancelled: () -> Unit = {}
    ) {
        require(chapters.isNotEmpty()) { "No chapters" }
        val bookId = "urn:uuid:${UUID.randomUUID()}"
        val safeTitle = escapeXml(title.ifBlank { "Untitled" })
        val lang = escapeXml(language.ifBlank { "en" })

        ZipOutputStream(FileOutputStream(output)).use { zip ->
            // 1) mimetype: first entry, STORED, exact size + CRC (required by the OCF spec)
            val mimeBytes = MIMETYPE.toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.writeText(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""
            )

            chapters.forEachIndexed { i, chapter ->
                checkCancelled()
                zip.writeText("OEBPS/chapter_${i + 1}.xhtml", chapterXhtml(chapter))
                onProgress(i + 1, chapters.size)
            }

            val manifest = chapters.indices.joinToString("\n") { i ->
                """    <item id="chapter_${i + 1}" href="chapter_${i + 1}.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spine = chapters.indices.joinToString("\n") { i -> """    <itemref idref="chapter_${i + 1}"/>""" }
            zip.writeText(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>$safeTitle</dc:title>
    <dc:language>$lang</dc:language>
    <dc:identifier id="BookId">$bookId</dc:identifier>
    <dc:creator>Morph</dc:creator>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifest
  </manifest>
  <spine toc="ncx">
$spine
  </spine>
</package>
"""
            )

            val navPoints = chapters.mapIndexed { i, chapter ->
                """    <navPoint id="navPoint-${i + 1}" playOrder="${i + 1}">
      <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
      <content src="chapter_${i + 1}.xhtml"/>
    </navPoint>"""
            }.joinToString("\n")
            zip.writeText(
                "OEBPS/toc.ncx",
                """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$bookId"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$safeTitle</text></docTitle>
  <navMap>
$navPoints
  </navMap>
</ncx>
"""
            )
        }
    }

    private fun chapterXhtml(chapter: Chapter): String {
        val title = escapeXml(chapter.title)
        val body = chapter.paragraphs.joinToString("\n") { p ->
            // Single line breaks inside a paragraph are kept (poems, lists, dialogue)
            "<p>" + p.lines().joinToString("<br/>") { escapeXml(it.trim()) } + "</p>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title></head>
<body>
<h2>$title</h2>
$body
</body>
</html>
"""
    }

    /**
     * Escapes the five XML specials and drops characters XML 1.0 forbids (control chars,
     * lone surrogates) that would make the whole book fail to open.
     */
    fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length + 16)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c == '\'' -> sb.append("&apos;")
                c == '\t' || c == '\n' -> sb.append(c)
                c < ' ' || c == '￾' || c == '￿' -> Unit
                Character.isHighSurrogate(c) && i + 1 < input.length && Character.isLowSurrogate(input[i + 1]) -> {
                    sb.append(c).append(input[i + 1]); i++
                }
                Character.isSurrogate(c) -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun ZipOutputStream.writeText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
