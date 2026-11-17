package com.morph.app

import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.FileCategory
import com.morph.app.core.FileCategoryManager
import com.morph.app.core.JpegPdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ConversionMappingTest {

    private val audioOutputs = listOf("mp3", "m4a", "wav", "flac", "ogg", "opus")
    private val videoInputs = listOf("mp4", "mkv", "mov", "webm", "avi")
    private val audioInputs = listOf("mp3", "m4a", "wav", "flac", "ogg", "opus", "aac")

    @Test
    fun audioToAudioOffersEveryOtherAudioFormat() {
        for (src in audioInputs) {
            val targets = ConversionCapabilityManager.getTargetFormats("song.$src", "audio/*")
            for (out in audioOutputs - src) {
                assertTrue("$src -> $out missing", targets.contains(out))
            }
            assertFalse("$src -> $src must not be offered", targets.contains(src))
        }
    }

    @Test
    fun videoToAudioOffersEveryAudioFormat() {
        for (src in videoInputs) {
            val targets = ConversionCapabilityManager.getTargetFormats("clip.$src", "video/*")
            for (out in audioOutputs) {
                assertTrue("$src -> $out missing", targets.contains(out))
            }
        }
    }

    @Test
    fun audioTargetsUseTheRightEncoderAndDropVideo() {
        val expected = mapOf(
            "mp3" to "libmp3lame", "m4a" to "-c:a aac", "wav" to "pcm_s16le",
            "flac" to "-c:a flac", "ogg" to "libvorbis", "opus" to "libopus"
        )
        for ((target, codec) in expected) {
            for (preset in 0..2) {
                val args = MorphEngine.buildFFmpegQualityArgs("mp4", target, preset, 4_000_000L)
                assertTrue("$target/$preset: $args", args.contains(codec))
                assertTrue("$target must drop video: $args", args.contains("-vn"))
            }
            assertTrue(ConversionCapabilityManager.isAudioOnlyTarget(target.uppercase()))
        }
        assertFalse(ConversionCapabilityManager.isAudioOnlyTarget("mp4"))
    }

    @Test
    fun videoTargetsOnlyUseEncodersShippedInFfmpegKitFull() {
        // Regression: the LGPL build has no libx264, so asking for it failed every MP4/MKV/MOV encode
        for (target in listOf("mp4", "mkv", "mov")) {
            val hw = MorphEngine.buildFFmpegQualityArgs("webm", target, 0, 4_000_000L, videoEncoder = "h264_mediacodec")
            val sw = MorphEngine.buildFFmpegQualityArgs("webm", target, 0, 4_000_000L, videoEncoder = "libopenh264")
            // Each encoder gets only its own flags and pixel format; never libx264
            assertTrue(hw, hw.contains("-c:v h264_mediacodec -bitrate_mode cbr -g 60"))
            assertTrue(hw, hw.contains("format=nv12"))
            assertFalse(hw, hw.contains("yuv420p") || hw.contains("libopenh264"))
            assertTrue(sw, sw.contains("-c:v libopenh264"))
            assertTrue(sw, sw.contains("format=yuv420p"))
            assertFalse(sw, sw.contains("nv12") || sw.contains("mediacodec") || sw.contains("bitrate_mode"))
            assertFalse(hw + sw, (hw + sw).contains("libx264"))
            // Native x264 never reaches an FFmpeg command line: it maps to the FFmpeg software encoder
            val nativeName = MorphEngine.buildFFmpegQualityArgs("webm", target, 0, 4_000_000L, videoEncoder = MorphEngine.X264_NATIVE)
            assertEquals(sw, nativeName)
            // Never tagged as RGB ("gbr"): BT.709 tags + matrix conversion inside the filter graph
            for (args in listOf(hw, sw)) {
                assertTrue(args, args.contains("-colorspace bt709 -color_primaries bt709 -color_trc bt709"))
                assertTrue(args, args.contains("out_color_matrix=bt709"))
                // Even-dimension guard is present even at Original resolution
                assertTrue(args, args.contains("scale='trunc(iw/2)*2':'trunc(ih/2)*2'"))
            }
        }
        assertFalse("AVI keeps FFmpeg defaults", MorphEngine.buildFFmpegQualityArgs("mp4", "avi", 0, 4_000_000L).contains("-c:v"))
    }

    @Test
    fun webmTriesHardwareVp9ThenFastRealtimeLibvpx() {
        val hw = MorphEngine.buildFFmpegQualityArgs("mp4", "webm", 1, 4_000_000L, videoEncoder = "vp9_mediacodec")
        assertTrue(hw, hw.contains("-c:v vp9_mediacodec -bitrate_mode cbr -g 60"))
        assertTrue(hw, hw.contains("format=nv12"))
        for (preset in 0..2) {
            val sw = MorphEngine.buildFFmpegQualityArgs("mp4", "webm", preset, 4_000_000L, videoEncoder = "libvpx-vp9")
            // Profile 0 (8-bit 4:2:0) avoids the black-screen playback of 10-bit sources
            assertTrue(sw, sw.contains("format=yuv420p"))
            // Speed 4 ran at 3.7 fps on the Redmi; realtime >= 5 uses libvpx's fast mode search
            assertTrue(sw, sw.contains("-deadline realtime -cpu-used ${MorphEngine.vp9Speed(preset)}"))
            assertTrue(sw, MorphEngine.vp9Speed(preset) >= 5)
            assertTrue(sw, sw.contains("-row-mt 1 -tile-columns 2 -lag-in-frames 0"))
            assertFalse("'good' deadline is slower, not faster", sw.contains("-deadline good"))
            assertTrue(sw, sw.contains("-c:a libopus"))
        }
    }

    @Test
    fun hardwareBitrateUnderflowIsDetected() {
        // 30 s at 18.7 Mbps should be ~70 MB; the Redmi driver produced 7.1 MB
        assertTrue(MorphEngine.isBitrateUnderflow(7_100_000L, 18_683_000L, 30_000L))
        assertFalse(MorphEngine.isBitrateUnderflow(60_000_000L, 18_683_000L, 30_000L))
        // Unknown duration or bitrate: never trigger a slow software redo
        assertFalse(MorphEngine.isBitrateUnderflow(1L, 18_683_000L, 0L))
        assertFalse(MorphEngine.isBitrateUnderflow(1L, 0L, 30_000L))
    }

    @Test
    fun encoderOrderIsHardwareThenNativeX264ThenOpenh264() {
        val noCap: (String) -> Long? = { null }
        assertEquals(
            listOf("h264_mediacodec", MorphEngine.X264_NATIVE, "libopenh264"),
            MorphEngine.videoEncoderOrder("mp4", 11_000_000L, noCap, nativeX264 = true)
        )
        // Without libmorph-native (JVM tests, x86_64, no x264 source): libopenh264 stays the fallback
        assertEquals(listOf("h264_mediacodec", "libopenh264"), MorphEngine.videoEncoderOrder("mkv", 11_000_000L, noCap, nativeX264 = false))
        assertEquals(listOf("vp9_mediacodec", "libvpx-vp9"), MorphEngine.videoEncoderOrder("webm", 11_000_000L, noCap, nativeX264 = true))
        assertEquals(listOf(MorphEngine.DEFAULT_ENCODER), MorphEngine.videoEncoderOrder("gif", 11_000_000L, noCap, nativeX264 = true))
        assertEquals(listOf(MorphEngine.DEFAULT_ENCODER), MorphEngine.videoEncoderOrder("mp3", 11_000_000L, noCap, nativeX264 = true))
    }

    @Test
    fun recordedHardwareCapSkipsHardwareOnlyWhenTheTargetNeedsMore() {
        // Redmi 15 h264_mediacodec delivered ~1.48 Mbps whatever it was asked for
        val caps: (String) -> Long? = { if (it == "h264_mediacodec") 1_480_000L else null }
        // Medium 1080p (11.2 Mbps): hardware would be thrown away anyway, go straight to software
        assertEquals(
            listOf(MorphEngine.X264_NATIVE, "libopenh264"),
            MorphEngine.videoEncoderOrder("mp4", 11_209_000L, caps, nativeX264 = true)
        )
        // 480p Low (~1.3 Mbps): the cap is enough, keep the fast hardware encoder
        assertEquals("h264_mediacodec", MorphEngine.videoEncoderOrder("mp4", 1_300_000L, caps, nativeX264 = true).first())
        // A cap on H.264 says nothing about the VP9 block
        assertEquals("vp9_mediacodec", MorphEngine.videoEncoderOrder("webm", 11_209_000L, caps, nativeX264 = true).first())
    }

    @Test
    fun noHardwareDecoderIsRequestedAndMkvFlushesClustersOften() {
        val out = java.io.File("out.mkv")
        val cmd = MorphEngine.buildFFmpegCommand("saf:7.mp4", out, "mp4", "mkv", 1, 8_000_000L, 0, 0, 0, "h264_mediacodec")
        // Software decoding: hevc_mediacodec lost the colour range on the Redmi (dark output)
        assertTrue(cmd, cmd.startsWith("-y -i \"saf:7.mp4\""))
        assertFalse(cmd, cmd.contains("_mediacodec -i"))
        // Live size check needs bytes on disk: clusters are written every second, not every 5 s
        assertTrue(cmd, cmd.contains("-cluster_time_limit 1000"))
        assertFalse(MorphEngine.buildFFmpegQualityArgs("mp4", "mp4", 0, 8_000_000L).contains("cluster_time_limit"))
    }

    @Test
    fun namesWithAnExtensionAreLeftAlone() {
        // MimeTypeMap is an Android stub in JVM tests, so only the no-op paths are checked here
        assertEquals("clip.mp4", withExtensionFromMime("clip.mp4", "video/mp4"))
        assertEquals("IMG-2026", withExtensionFromMime("IMG-2026", null))
        assertEquals("IMG-2026", withExtensionFromMime("IMG-2026", ""))
    }

    @Test
    fun firmwareIsFlaggedOnlyWhenSoftwareProvesTheDriverWrong() {
        // Redmi: hardware ~0.19 bytes/ms-scale vs software near target => driver ignored the bitrate
        assertTrue(MorphEngine.confirmsHwIgnoredBitrate(hwBytesPerMs = 190.0, swBytesPerMs = 2_200.0))
        // Static screen recording: software is small too => content, not the driver; keep hardware
        assertFalse(MorphEngine.confirmsHwIgnoredBitrate(hwBytesPerMs = 190.0, swBytesPerMs = 250.0))
        assertFalse(MorphEngine.confirmsHwIgnoredBitrate(hwBytesPerMs = 0.0, swBytesPerMs = 250.0))
    }

    @Test
    fun earlyCheckCatchesTheRedmiCaseAtHighAndMedium() {
        // High: ~5.8 MB for a ~69 MB target over ~30 s
        val high = 18_683_000L + 256_000L
        assertTrue(MorphEngine.isBitrateUnderflow(outputBytes = 970_000L, targetBitsPerSec = high, durationMs = 5_000L))
        assertFalse(MorphEngine.isBitrateUnderflow(outputBytes = 11_000_000L, targetBitsPerSec = high, durationMs = 5_000L))
        // Medium: 1479 kb/s delivered for an 11.2 Mbps target (79 MB -> 5.5 MB) is now caught too
        val medium = MorphEngine.videoBitrateForPreset(1, 18_683_000L) + MorphEngine.audioKbpsFor("mp4", 1) * 1000L
        assertTrue(MorphEngine.isBitrateUnderflow(outputBytes = 1_479_000L / 8 * 5, targetBitsPerSec = medium, durationMs = 5_000L))
    }

    @Test
    fun taggedHeapPointerIsAValidNativeHandle() {
        // Redmi 15 bug: Android 11+ arm64 tags heap pointers in the top byte (Scudo, 0xB4), so a
        // real Session* is a NEGATIVE jlong. The old "handle <= 0" check called it a failure.
        val tagged = 0xB400007BF8A12345UL.toLong()
        assertTrue("sanity: tagged pointer is negative", tagged < 0)
        assertFalse(MorphNative.isOpenError(tagged))
        assertFalse(MorphNative.isOpenError(0x00000077A1B2C3D0L)) // untagged pointer
        // Real errors are the small codes from morph_native.cpp (-1..-8) and 0
        for (code in -8L..-1L) assertTrue("code $code", MorphNative.isOpenError(code))
        assertTrue(MorphNative.isOpenError(0L))
    }

    @Test
    fun nativeX264PlanMatchesTheFfmpegFilters() {
        // No .so in a JVM test: the bridge must report "not available" instead of crashing
        assertFalse(MorphNative.isAvailable)
        assertEquals("veryfast", MorphNative.x264PresetFor(0))
        assertEquals("superfast", MorphNative.x264PresetFor(1))
        assertEquals("ultrafast", MorphNative.x264PresetFor(2))
        // Portrait phone clip stored 1920x1080 + rotate 90: frames reach x264 upright
        assertEquals(1080 to 1920, MorphNative.outputSize(1920, 1080, 90, 0, 0))
        // PBI 5.2 short side, never upscaled
        assertEquals(1280 to 720, MorphNative.outputSize(1920, 1080, 0, 0, 720))
        assertEquals(720 to 1280, MorphNative.outputSize(1920, 1080, 270, 0, 720))
        assertEquals(640 to 480, MorphNative.outputSize(640, 480, 0, 0, 1080))
        // Low preset: long side 854 like the FFmpeg filter; odd sizes rounded down to even
        assertEquals(854 to 480, MorphNative.outputSize(1920, 1080, 0, 2, 0))
        assertEquals(1080 to 1920, MorphNative.outputSize(1081, 1921, 0, 0, 0))
        // ffprobe rates
        assertEquals(30000 to 1001, MorphNative.frameRate("30000/1001", 0))
        assertEquals(30 to 1, MorphNative.frameRate("0/0", 0))
        assertEquals(30 to 1, MorphNative.frameRate(null, 0))
        assertEquals(24 to 1, MorphNative.frameRate("30/1", 24))
    }

    @Test
    fun instantRulesForPhoneHevcVideo() {
        // HEVC + AAC can be stream-copied into MP4, MOV and MKV
        for (target in listOf("mp4", "mov", "mkv")) {
            assertTrue(target, ConversionCapabilityManager.isStreamCopyCodecCompatible(target, listOf("hevc"), listOf("aac")))
        }
        // ...but a rotated (portrait) video copied into MKV/WebM would play sideways
        assertTrue(ConversionCapabilityManager.rotationBlocksStreamCopy(90, "mkv"))
        assertTrue(ConversionCapabilityManager.rotationBlocksStreamCopy(270, "WEBM"))
        assertFalse(ConversionCapabilityManager.rotationBlocksStreamCopy(90, "mp4"))
        assertFalse(ConversionCapabilityManager.rotationBlocksStreamCopy(0, "mkv"))
        assertTrue(ConversionCapabilityManager.isLosslessCompatible("mp4", "mkv"))
    }

    @Test
    fun jpgAndJpegGroupsCanMergeIntoOnePdf() {
        val groups = ConversionCapabilityManager.pdfCrossMergeGroups(
            mapOf("jpg" to "pdf", "jpeg" to "pdf", "mp4" to "mkv")
        )
        assertEquals(setOf("jpg", "jpeg"), groups.toSet())
        // One image group alone: its own merge switch is enough
        assertTrue(ConversionCapabilityManager.pdfCrossMergeGroups(mapOf("jpg" to "pdf")).isEmpty())
        // Not targeting PDF: nothing to merge
        assertTrue(ConversionCapabilityManager.pdfCrossMergeGroups(mapOf("jpg" to "png", "jpeg" to "png")).isEmpty())
    }

    @Test
    fun videoExtensionsAreVideoInAnyCase() {
        for (ext in videoInputs.flatMap { listOf(it, it.uppercase(), it.replaceFirstChar(Char::uppercase)) }) {
            val category = FileCategoryManager.getCategory(ext)
            assertEquals(ext, FileCategory.VIDEO, category)
            assertNotEquals(ext, FileCategory.DOCUMENT, category)
            assertTrue(FileCategoryManager.isVideo(ext))
        }
    }

    @Test
    fun jpegPdfWriterProducesAValidXrefStructure() {
        val bytes = ByteArrayOutputStream()
        JpegPdfWriter(bytes).use { w ->
            w.addPage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2), 100, 141, 595f, 842f)
            w.addPage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 3), 141, 100, 842f, 595f)
        }
        val pdf = bytes.toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(pdf.startsWith("%PDF-1.4"))
        assertTrue(pdf.contains("/Count 2"))
        assertEquals(2, Regex("/DCTDecode").findAll(pdf).count())
        val startXref = Regex("startxref\n(\\d+)").find(pdf)!!.groupValues[1].toInt()
        assertTrue("startxref must point at the xref table", pdf.startsWith("xref", startXref))
        // Every xref offset must land on "<id> 0 obj"
        val entries = Regex("(\\d{10}) 00000 n").findAll(pdf).map { it.groupValues[1].toInt() }.toList()
        entries.forEachIndexed { i, offset -> assertTrue(pdf.startsWith("${i + 1} 0 obj", offset)) }
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
    }
}
