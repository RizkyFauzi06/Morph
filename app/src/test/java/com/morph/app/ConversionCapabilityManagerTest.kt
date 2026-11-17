package com.morph.app

import com.morph.app.core.ConversionCapabilityManager
import org.junit.Assert.*
import org.junit.Test

class ConversionCapabilityManagerTest {

    /** The pair is offered in the BottomSheet (the real source of truth since the old table was removed). */
    private fun offers(src: String, target: String) =
        ConversionCapabilityManager.getTargetFormats("file.$src").contains(target)

    @Test
    fun testPreSprint4ChecklistMatrixCapabilities() {
        // Video V01 - V07
        assertTrue("V01: MP4 -> MKV", offers("mp4", "mkv"))
        assertTrue("V02: MP4 -> MOV", offers("mp4", "mov"))
        assertTrue("V03: MP4 -> WEBM", offers("mp4", "webm"))
        assertTrue("V04: MP4 -> GIF", offers("mp4", "gif"))
        assertTrue("V05: MKV -> MP4", offers("mkv", "mp4"))
        assertTrue("V06: MKV -> MOV", offers("mkv", "mov"))
        assertTrue("V07: MKV -> WEBM", offers("mkv", "webm"))

        // Audio A01 - A05
        assertTrue("A01: FLAC -> WAV", offers("flac", "wav"))
        assertTrue("A02: FLAC -> MP3", offers("flac", "mp3"))
        assertTrue("A03: FLAC -> AAC", offers("flac", "aac"))
        assertTrue("A04: WAV -> FLAC", offers("wav", "flac"))
        assertTrue("A05: WAV -> MP3", offers("wav", "mp3"))

        // Raster Image I01 - I05
        assertTrue("I01: PNG -> JPG", offers("png", "jpg"))
        assertTrue("I02: PNG -> WEBP", offers("png", "webp"))
        assertTrue("I03: PNG -> BMP", offers("png", "bmp"))
        assertTrue("I04: JPG -> PNG", offers("jpg", "png"))
        assertTrue("I05: JPG -> WEBP", offers("jpg", "webp"))

        // Vector S01 - S02
        assertTrue("S01: SVG -> PNG", offers("svg", "png"))
        assertTrue("S02: SVG -> JPG", offers("svg", "jpg"))

        // PDF P01 - P02
        assertTrue("P01: PDF -> JPG", offers("pdf", "jpg"))
        assertTrue("P02: PDF -> PNG", offers("pdf", "png"))

        // E-Book E01 - E02
        assertTrue("E01: EPUB -> TXT", offers("epub", "txt"))
        assertTrue("E02: TXT -> EPUB (PBI 5.3)", offers("txt", "epub"))
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

        // TXT -> EPUB only; unknown types get no fake targets
        assertEquals(listOf("epub"), ConversionCapabilityManager.getTargetFormats("notes.txt", "text/plain"))
        assertTrue(ConversionCapabilityManager.getTargetFormats("archive.zip", "application/zip").isEmpty())
    }

    @Test
    fun testStreamCopyCodecGuard() {
        val guard = ConversionCapabilityManager::isStreamCopyCodecCompatible
        // BUG-03: MKV with AC3 audio must not be remuxed into MP4 (silent output)
        assertFalse(guard("mp4", listOf("h264"), listOf("ac3")))
        assertTrue(guard("mp4", listOf("h264"), listOf("aac")))
        assertTrue(guard("mov", listOf("hevc"), emptyList()))
        assertFalse(guard("mp4", listOf("vp9"), listOf("opus")))
        assertTrue(guard("webm", listOf("vp9"), listOf("opus")))
        assertFalse(guard("webm", listOf("h264"), listOf("aac")))
        assertTrue(guard("mkv", listOf("h264"), listOf("dts")))
        assertFalse("no video probed", guard("mkv", emptyList(), listOf("aac")))
        // Audio-only targets: codec must already match the container
        assertTrue(guard("m4a", listOf("h264"), listOf("aac")))
        assertFalse(guard("flac", emptyList(), listOf("aac")))
        assertFalse("no audio stream", guard("mp3", listOf("h264"), emptyList()))
        assertTrue(guard("ogg", emptyList(), listOf("opus")))
    }
}
