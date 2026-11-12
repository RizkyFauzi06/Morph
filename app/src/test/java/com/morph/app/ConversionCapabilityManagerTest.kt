package com.morph.app

import com.morph.app.core.CapabilityStatus
import com.morph.app.core.ConversionCapabilityManager
import org.junit.Assert.*
import org.junit.Test

class ConversionCapabilityManagerTest {

    @Test
    fun testPreSprint4ChecklistMatrixCapabilities() {
        // Video V01 - V07
        assertTrue("V01: MP4 -> MKV", ConversionCapabilityManager.isSupported("mp4", "mkv"))
        assertTrue("V02: MP4 -> MOV", ConversionCapabilityManager.isSupported("mp4", "mov"))
        assertTrue("V03: MP4 -> WEBM", ConversionCapabilityManager.isSupported("mp4", "webm"))
        assertTrue("V04: MP4 -> GIF", ConversionCapabilityManager.isSupported("mp4", "gif"))
        assertTrue("V05: MKV -> MP4", ConversionCapabilityManager.isSupported("mkv", "mp4"))
        assertTrue("V06: MKV -> MOV", ConversionCapabilityManager.isSupported("mkv", "mov"))
        assertTrue("V07: MKV -> WEBM", ConversionCapabilityManager.isSupported("mkv", "webm"))

        // Audio A01 - A05
        assertTrue("A01: FLAC -> WAV", ConversionCapabilityManager.isSupported("flac", "wav"))
        assertTrue("A02: FLAC -> MP3", ConversionCapabilityManager.isSupported("flac", "mp3"))
        assertTrue("A03: FLAC -> AAC", ConversionCapabilityManager.isSupported("flac", "aac"))
        assertTrue("A04: WAV -> FLAC", ConversionCapabilityManager.isSupported("wav", "flac"))
        assertTrue("A05: WAV -> MP3", ConversionCapabilityManager.isSupported("wav", "mp3"))

        // Raster Image I01 - I05
        assertTrue("I01: PNG -> JPG", ConversionCapabilityManager.isSupported("png", "jpg"))
        assertTrue("I02: PNG -> WEBP", ConversionCapabilityManager.isSupported("png", "webp"))
        assertTrue("I03: PNG -> BMP", ConversionCapabilityManager.isSupported("png", "bmp"))
        assertTrue("I04: JPG -> PNG", ConversionCapabilityManager.isSupported("jpg", "png"))
        assertTrue("I05: JPG -> WEBP", ConversionCapabilityManager.isSupported("jpg", "webp"))

        // Vector S01 - S02
        assertTrue("S01: SVG -> PNG", ConversionCapabilityManager.isSupported("svg", "png"))
        assertTrue("S02: SVG -> JPG", ConversionCapabilityManager.isSupported("svg", "jpg"))

        // PDF P01 - P02
        assertTrue("P01: PDF -> JPG", ConversionCapabilityManager.isSupported("pdf", "jpg"))
        assertTrue("P02: PDF -> PNG", ConversionCapabilityManager.isSupported("pdf", "png"))

        // E-Book E01 - E02
        assertTrue("E01: EPUB -> TXT", ConversionCapabilityManager.isSupported("epub", "txt"))
        assertFalse("E02: TXT -> EPUB (unsupported in Pre-Sprint 4)", ConversionCapabilityManager.isSupported("txt", "epub"))
    }

    @Test
    fun testLosslessStreamCopyRules() {
        // Video container remuxing
        assertTrue(ConversionCapabilityManager.isLosslessCompatible("mp4", "mkv"))
        assertTrue(ConversionCapabilityManager.isLosslessCompatible("mkv", "mp4"))
        assertTrue(ConversionCapabilityManager.isLosslessCompatible("mp4", "mov"))
        assertTrue(ConversionCapabilityManager.isLosslessCompatible("mkv", "mov"))

        // Video transcode (WebM needs VP9, not stream copy of H.264)
        assertFalse(ConversionCapabilityManager.isLosslessCompatible("mp4", "webm"))
        assertFalse(ConversionCapabilityManager.isLosslessCompatible("mp4", "gif"))

        // Audio transcoding (FLAC -> WAV changes container & encoding, not stream copy)
        assertFalse(ConversionCapabilityManager.isLosslessCompatible("flac", "wav"))
        assertFalse(ConversionCapabilityManager.isLosslessCompatible("wav", "mp3"))
    }

    @Test
    fun testTargetFormatsResolution() {
        // FLAC audio file targets must include WAV, MP3, AAC, FLAC
        val flacTargets = ConversionCapabilityManager.getTargetFormats("song.flac", "*/*")
        assertTrue("WAV in FLAC targets", flacTargets.contains("wav"))
        assertTrue("MP3 in FLAC targets", flacTargets.contains("mp3"))
        assertTrue("AAC in FLAC targets", flacTargets.contains("aac"))

        // Video targets must include WEBM, MKV, MP4, GIF
        val videoTargets = ConversionCapabilityManager.getTargetFormats("video.mp4", "video/mp4")
        assertTrue("WEBM in video targets", videoTargets.contains("webm"))
        assertTrue("MKV in video targets", videoTargets.contains("mkv"))
        assertTrue("GIF in video targets", videoTargets.contains("gif"))

        // SVG targets
        val svgTargets = ConversionCapabilityManager.getTargetFormats("icon.svg", "image/svg+xml")
        assertTrue("PNG in SVG targets", svgTargets.contains("png"))
        assertTrue("JPG in SVG targets", svgTargets.contains("jpg"))

        // EPUB targets
        val epubTargets = ConversionCapabilityManager.getTargetFormats("book.epub", "application/epub+zip")
        assertTrue("TXT in EPUB targets", epubTargets.contains("txt"))
    }
}
