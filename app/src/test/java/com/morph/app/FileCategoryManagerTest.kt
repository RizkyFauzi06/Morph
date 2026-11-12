package com.morph.app

import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import org.junit.Assert.*
import org.junit.Test

class FileCategoryManagerTest {

    @Test
    fun testAudioClassificationIncludesFlacAndWav() {
        // FLAC, WAV, MP3, AAC, M4A, OGG, OPUS must all be classified as AUDIO
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("flac"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("FLAC"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory(".flac"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("wav"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("WAV"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("mp3"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("aac"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("m4a"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("ogg"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("opus"))

        assertTrue(FileCategoryManager.isAudio("flac"))
        assertTrue(FileCategoryManager.isAudio("wav"))
        assertTrue(FileCategoryManager.isAudio("mp3"))
    }

    @Test
    fun testVideoClassification() {
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("mp4"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("MKV"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("mov"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("webm"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("avi"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("ts"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("m4v"))

        assertTrue(FileCategoryManager.isVideo("mp4"))
        assertTrue(FileCategoryManager.isVideo("mkv"))
    }

    @Test
    fun testImageClassificationIncludesBmpAndSvg() {
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("png"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("jpg"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("jpeg"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("webp"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("bmp"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("BMP"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("svg"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("gif"))

        assertTrue(FileCategoryManager.isImage("png"))
        assertTrue(FileCategoryManager.isImage("bmp"))
        assertTrue(FileCategoryManager.isImage("svg"))
    }

    @Test
    fun testDocumentClassificationIncludesEpubAndCsv() {
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("pdf"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("txt"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("epub"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("EPUB"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("csv"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("doc"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("docx"))

        assertTrue(FileCategoryManager.isDocument("pdf"))
        assertTrue(FileCategoryManager.isDocument("epub"))
    }

    @Test
    fun testMimeTypeFallbackWhenExtensionMissing() {
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("", "audio/flac"))
        assertEquals(FileCategory.AUDIO, FileCategoryManager.getCategory("", "audio/mpeg"))
        assertEquals(FileCategory.VIDEO, FileCategoryManager.getCategory("", "video/mp4"))
        assertEquals(FileCategory.IMAGE, FileCategoryManager.getCategory("", "image/png"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("", "application/pdf"))
        assertEquals(FileCategory.DOCUMENT, FileCategoryManager.getCategory("", "application/epub+zip"))
    }

    @Test
    fun testCategoryBadgeColorsAreValid() {
        for (category in FileCategory.values()) {
            val color = FileCategoryManager.getCategoryColor(category)
            assertNotNull(color)
        }
    }
}
