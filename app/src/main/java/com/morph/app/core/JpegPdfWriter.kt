package com.morph.app.core

import java.io.Closeable
import java.io.OutputStream
import java.util.Locale

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Minimal PDF 1.4 writer whose pages are full-page JPEG images (DCTDecode).
 *
 * Why not android.graphics.pdf.PdfDocument: Skia stores every drawn Bitmap as raw pixels with
 * Flate, so a "compressed" PDF built that way is often larger than the original. Embedding the
 * JPEG bytes as-is keeps the JPEG compression. Pure JVM, streams page by page (low memory).
 */
class JpegPdfWriter(output: OutputStream) : Closeable {

    private val out = CountingOutputStream(output)
    private val offsets = mutableMapOf<Int, Long>()
    private val pageIds = mutableListOf<Int>()
    private var nextId = 3 // 1 = Catalog, 2 = Pages (written at close, once Kids are known)
    private var closed = false

    init {
        out.writeAscii("%PDF-1.4\n%âãÏÓ\n")
    }

    /**
     * Adds one page showing [jpeg] stretched to [pageWidthPt] x [pageHeightPt] points.
     * [pixelWidth]/[pixelHeight] must be the JPEG's real pixel size.
     */
    fun addPage(jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int, pageWidthPt: Float, pageHeightPt: Float) {
        check(!closed) { "Writer already closed" }
        require(pixelWidth > 0 && pixelHeight > 0 && pageWidthPt > 0f && pageHeightPt > 0f)
        val imageId = nextId++
        val contentId = nextId++
        val pageId = nextId++
        val w = fmt(pageWidthPt)
        val h = fmt(pageHeightPt)

        beginObject(imageId)
        out.writeAscii(
            "<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n"
        )
        out.write(jpeg)
        out.writeAscii("\nendstream\nendobj\n")

        val content = "q $w 0 0 $h 0 0 cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
        beginObject(contentId)
        out.writeAscii("<< /Length ${content.size} >>\nstream\n")
        out.write(content)
        out.writeAscii("\nendstream\nendobj\n")

        beginObject(pageId)
        out.writeAscii(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                "/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
        )
        pageIds += pageId
    }

    val pageCount: Int get() = pageIds.size

    override fun close() {
        if (closed) return
        closed = true
        beginObject(1)
        out.writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        beginObject(2)
        out.writeAscii("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n")

        val xrefOffset = out.count
        val size = nextId
        val sb = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (id in 1 until size) {
            sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets.getValue(id)))
        }
        sb.append("trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.writeAscii(sb.toString())
        out.close()
    }

    private fun beginObject(id: Int) {
        offsets[id] = out.count
        out.writeAscii("$id 0 obj\n")
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    private class CountingOutputStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) { target.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { target.write(b, off, len); count += len }
        override fun flush() = target.flush()
        override fun close() = target.close()

        fun writeAscii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }
}
