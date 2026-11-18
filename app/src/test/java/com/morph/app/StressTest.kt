package com.morph.app

import android.net.TestUri
import com.morph.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.random.Random

/**
 * Stress tests for every feature's pure logic: the whole offered conversion matrix, fuzzed sizes
 * and frame rates, hostile text and broken EPUBs, a queue hammered from 8 threads, a 300-page PDF
 * and every UI string in both languages. Real decoding/encoding needs a phone (debug stress run).
 */
class StressTest {

    // Mirrors FileCategoryManager's lists plus names the picker can hand us ("*/*")
    private val images = listOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "svg", "heic", "heif", "ico", "tiff", "tif")
    private val videos = listOf("mp4", "mkv", "mov", "webm", "avi", "flv", "wmv", "3gp", "m4v", "ts", "ogv")
    private val audios = listOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "aiff", "alac", "amr")
    private val others = listOf("pdf", "epub", "txt", "doc", "docx", "csv", "xlsx", "zip", "kt", "json", "", "unknownext")
    private val allSources = images + videos + audios + others

    @Test
    fun everyOfferedConversionHasAWorkingRoute() {
        var pairs = 0
        for (src in allSources) {
            for (tgt in ConversionCapabilityManager.getTargetFormats("file.$src")) {
                pairs++
                val category = FileCategoryManager.getCategory(src)
                when (MorphEngine.routeFor(src, tgt)) {
                    MorphEngine.Route.MEDIA -> {
                        // An image here gets "-vn" (drop video) and fails every time
                        assertTrue("$src -> $tgt reaches FFmpeg but $src is $category",
                            category == FileCategory.VIDEO || category == FileCategory.AUDIO)
                        assertTrue("$src -> $tgt: FFmpeg has no recipe for $tgt",
                            tgt in setOf("mp4", "mkv", "mov", "webm", "avi", "gif") || ConversionCapabilityManager.isAudioOnlyTarget(tgt))
                    }
                    MorphEngine.Route.IMAGE, MorphEngine.Route.IMAGES_TO_PDF ->
                        assertTrue("$src -> $tgt: Android can't decode $src", MorphEngine.isDecodableImage(src))
                    else -> Unit
                }
            }
        }
        assertTrue("matrix too small: $pairs", pairs > 150)
        // These used to fall through to FFmpeg
        for (src in listOf("gif", "heic", "heif", "ico")) {
            assertEquals(src, MorphEngine.Route.IMAGE, MorphEngine.routeFor(src, "png"))
            assertEquals(src, MorphEngine.Route.IMAGES_TO_PDF, MorphEngine.routeFor(src, "pdf"))
        }
        // No TIFF decoder on Android: offer nothing rather than a conversion that always fails
        assertTrue(ConversionCapabilityManager.getTargetFormats("scan.tiff").isEmpty())
        assertTrue(ConversionCapabilityManager.getTargetFormats("SCAN.TIF").isEmpty())
    }

    @Test
    fun ffmpegCommandMatrixIsWellFormed() {
        val encoders = mapOf(
            "mp4" to listOf("h264_mediacodec", "libopenh264"), "mkv" to listOf("h264_mediacodec", "libopenh264"),
            "mov" to listOf("h264_mediacodec", "libopenh264"), "webm" to listOf("libvpx")
        )
        val shapes = listOf(Triple(0, 0, 0), Triple(90, 720, 30), Triple(270, 480, 24), Triple(180, 1080, 60))
        var commands = 0
        for (src in videos + audios) for (tgt in ConversionCapabilityManager.getTargetFormats("file.$src")) {
            if (MorphEngine.routeFor(src, tgt) != MorphEngine.Route.MEDIA) continue
            for (preset in 0..3) for (encoder in encoders[tgt] ?: listOf(MorphEngine.DEFAULT_ENCODER))
                for (copyAudio in listOf(false, true)) for ((rotation, res, fps) in shapes) {
                    val args = MorphEngine.buildFFmpegQualityArgs(src, tgt, preset, 8_000_000L, rotation, res, fps, encoder, copyAudio)
                    val where = "$src->$tgt p$preset $encoder copy=$copyAudio rot=$rotation res=$res: $args"
                    commands++
                    assertTrue(where, args.isNotBlank())
                    assertEquals("unbalanced quotes $where", 0, args.count { it == '"' } % 2)
                    // libx264 isn't in the LGPL build; libvpx-vp9 output can't be decoded (ffmpeg-kit 8.1.7)
                    assertFalse(where, args.contains("libx264") || args.contains("libvpx-vp9") || args.contains("null") || args.contains("NaN"))
                    if (args.contains("libvorbis")) assertFalse("vorbis bitrate fails for mono $where", args.contains("-b:a"))
                    assertFalse(where, args.contains("-c:a copy") && args.contains("-b:a"))
                    if (ConversionCapabilityManager.isAudioOnlyTarget(tgt)) assertTrue(where, args.contains("-vn"))
                    if (preset == 3) {
                        assertTrue(where, args.contains("copy"))
                    } else if (tgt in encoders) {
                        assertTrue(where, args.contains("-b:v ") && args.contains("-c:v $encoder"))
                        assertTrue("odd sizes break H.264/VP9 $where", args.contains("trunc(iw/2)*2"))
                    }
                    if (tgt == "webm") assertFalse(where, args.contains("-c:a aac"))
                    if (tgt in setOf("mp4", "mov")) assertFalse(where, args.contains("libopus") || args.contains("libvorbis"))
                }
        }
        assertTrue("only $commands commands", commands > 2000)
    }

    @Test
    fun audioCopyNeverBreaksTheRules() {
        val codecs = listOf("aac", "AAC", "mp3", "opus", "vorbis", "flac", "alac", "ac3", "eac3", "dts", "truehd",
            "pcm_s16le", "pcm_s24le", "amr_nb", "wmav2", "")
        val targets = listOf("mp4", "mkv", "mov", "webm", "avi", "gif", "mp3", "m4a", "aac", "opus", "ogg", "flac", "wav")
        for (t in targets) for (c in codecs) for (p in 0..3) for (bps in listOf(null, 0L, 64_000L, 128_000L, 320_000L, 1_500_000L)) {
            val copy = MorphEngine.shouldCopyAudio(t, p, listOf(c), bps)
            if (copy) {
                assertTrue("$t $c", ConversionCapabilityManager.canKeepAudioCodec(t, c))
                assertTrue("$t $c p$p $bps", p == 0 || (bps != null && bps in 1..MorphEngine.audioKbpsFor(t, p) * 1000L))
            }
            if (p == 3) assertFalse("Instant decides on its own: $t $c", copy)
            assertFalse("two tracks $t $c", MorphEngine.shouldCopyAudio(t, p, listOf(c, c), bps))
        }
        // Tracks many phones can't decode never stay inside a video file
        for (t in listOf("mp4", "mkv", "mov", "webm")) for (c in listOf("ac3", "eac3", "dts", "truehd", "pcm_s16le", "flac", "alac")) {
            assertFalse("$t $c", ConversionCapabilityManager.canKeepAudioCodec(t, c))
        }
    }

    @Test
    fun nativeOutputSizeFuzz() {
        val rnd = Random(7)
        val sars = listOf(null, "1:1", "4:3", "32:27", "8:9", "0:1", "N/A", "3:1", "1:3")
        repeat(50_000) {
            val w = rnd.nextInt(2, 8192)
            val h = rnd.nextInt(2, 8192)
            val rotation = listOf(0, 90, 180, 270, -90, 360, 450).random(rnd)
            val preset = rnd.nextInt(0, 3)
            val res = listOf(0, 480, 720, 1080, 2160).random(rnd)
            val sar = sars.random(rnd)
            val (ow, oh) = MorphNative.outputSize(w, h, rotation, preset, res, sar)
            val case = "${w}x$h rot=$rotation p$preset res=$res sar=$sar -> ${ow}x$oh"
            assertTrue(case, ow >= 2 && oh >= 2 && ow % 2 == 0 && oh % 2 == 0)
            // The shape the viewer sees: SAR first, then the turn
            val ratio = sar?.split(':')?.let { p ->
                val n = p.getOrNull(0)?.toDoubleOrNull()
                val d = p.getOrNull(1)?.toDoubleOrNull()
                if (n != null && d != null && n > 0 && d > 0 && n / d in 0.25..4.0) n / d else null
            } ?: 1.0
            val displayW = maxOf(2L, Math.round(w * ratio)).toDouble()
            val (dw, dh) = if (rotation % 180 != 0) h.toDouble() to displayW else displayW to h.toDouble()
            // No preset or resolution option makes a video bigger than it is
            assertTrue("upscaled: $case", ow <= dw + 1 && oh <= dh + 1)
            // Orientation and shape kept; even sizes cost at most ~2 px per side. Below ~8 px the
            // 2 px minimum can't keep any shape (no real video is 6 px wide).
            if (ow > 8 && oh > 8) {
                val err = abs((ow.toDouble() / oh) / (dw / dh) - 1)
                assertTrue("aspect: $case err=$err", err <= 2.5 / ow + 2.5 / oh + 1e-9)
            }
        }
    }

    @Test
    fun lowPresetNeverUpscalesInEitherPath() {
        // A 640x360 clip stays 640x360 at Low instead of growing to 854x480
        assertEquals(640 to 360, MorphNative.outputSize(640, 360, 0, 2, 0))
        assertEquals(360 to 640, MorphNative.outputSize(360, 640, 0, 2, 0))
        assertEquals(854 to 480, MorphNative.outputSize(1920, 1080, 0, 2, 0))
        // The FFmpeg filter must apply the same cap, and GIF must not blow small clips up either
        val filter = MorphEngine.buildVideoFilterArgs(2, 0, 0)
        assertTrue(filter, filter.contains("min(854,iw)") && filter.contains("min(854,ih)"))
        for ((preset, edge) in listOf(0 to 480, 1 to 360, 2 to 240)) {
            val gif = MorphEngine.buildFFmpegQualityArgs("mp4", "gif", preset, 4_000_000L)
            assertTrue(gif, gif.contains("min($edge,iw)") && gif.contains("min($edge,ih)"))
        }
    }

    @Test
    fun frameRateFuzz() {
        val fixed = listOf(null, "", "0/0", "30/1", "30000/1001", "25", "abc", "30/0", "-30/1", "1/0", "2147483647/1",
            "9999999999/1", "60/1/2", "/", "24000/1001 ", " 30/1", "1000/1", "1/1000", "37035000/1401493", "30/-1")
        val rnd = Random(3)
        val random = List(3000) { "${rnd.nextInt(-5, 400_000)}/${rnd.nextInt(-5, 20_000)}" }
        for (rate in fixed + random) for (override in listOf(0, 24, 60)) {
            val (num, den) = MorphNative.frameRate(rate, override)
            val fps = num.toDouble() / den
            assertTrue("$rate/$override -> $num/$den", den > 0 && fps in 1.0..240.0)
            if (override > 0) assertEquals(override to 1, num to den)
        }
    }

    @Test
    fun epubRoundTripSurvivesHostileText() {
        val paragraphs = listOf(
            "Plain ASCII paragraph.", "Émoji 😀👍🏽 and CJK 漢字かな한글", "RTL مرحبا بالعالم עברית",
            "XML specials < > & \" ' and ]]> and <!-- -->", "Control\u0001chars\u0007here\u001F", "Lone surrogate \uD800 end",
            "Zero width \u200B\u200D joiner", "A".repeat(20_000), "Tabs\tand\u000Bvertical", "\uFEFFBOM in the middle"
        )
        // Straight into the builder: as bytes, Java would already turn the lone surrogate into '?'
        val chapters = listOf(EpubBuilder.Chapter("Bab 1 <&>", paragraphs))
        val out = File.createTempFile("stress", ".epub")
        try {
            EpubBuilder.build(chapters, "Tïtle <&> 😀", "id", out)
            val parsed = EpubParser.parseEpubToText(out).squash()
            val expected = listOf("Plain ASCII paragraph.", "Émoji 😀👍🏽 and CJK 漢字かな한글", "RTL مرحبا بالعالم עברית",
                "XML specials < > & \" ' and ]]> and <!-- -->", "Controlcharshere", "Lone surrogate end",
                "A".repeat(20_000), "Tabs andvertical")
            for (p in expected) assertTrue("lost: ${p.take(40)}", parsed.contains(p.squash()))
            assertTrue(parsed.contains("Tïtle <&> 😀"))
        } finally {
            out.delete()
        }
    }

    @Test
    fun textDecodingSurvivesDamagedAndLegacyFiles() {
        val utf8 = "Halo dunia 😀 漢字 café naïve — “quotes” ".repeat(50)
        // One broken byte (e.g. a download cut mid-character) must not turn the whole book into mojibake
        val damaged = utf8.toByteArray(Charsets.UTF_8) + byteArrayOf(0xE2.toByte(), 0x82.toByte())
        val decoded = EpubBuilder.decodeText(damaged)
        assertTrue(decoded.take(60), decoded.startsWith(utf8))
        // A real Windows-1252 file still decodes as Windows-1252
        val legacy = "café naïve Señor".toByteArray(charset("windows-1252"))
        assertEquals("café naïve Señor", EpubBuilder.decodeText(legacy))
        // BOM and CRLF from Notepad: the BOM is dropped and paragraphs still split
        val notepad = ("\uFEFF" + "Satu\r\n\r\nDua\r\n\r\nTiga").toByteArray(Charsets.UTF_8)
        val chapters = EpubBuilder.splitChapters(EpubBuilder.decodeText(notepad)) { "Part $it" }
        assertEquals(listOf("Satu", "Dua", "Tiga"), chapters.single().paragraphs)
        // Empty and whitespace-only input decode without crashing
        assertEquals("", EpubBuilder.decodeText(ByteArray(0)))
        assertTrue(EpubBuilder.splitChapters(EpubBuilder.decodeText(" \n \n ".toByteArray())) { "P" }.isEmpty())
    }

    @Test
    fun bigTextBookBuildsAndParsesQuickly() {
        val filler = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(8)
        val text = (1..12_000).joinToString("\n\n") { if (it % 300 == 1) "Chapter ${it / 300 + 1}" else "$it $filler" } // ~5.6 MB
        val start = System.nanoTime()
        val chapters = EpubBuilder.splitChapters(text) { "Part $it" }
        val out = File.createTempFile("stress-big", ".epub")
        try {
            EpubBuilder.build(chapters, "Big", "en", out)
            val parsed = EpubParser.parseEpubToText(out)
            val seconds = (System.nanoTime() - start) / 1e9
            assertEquals(40, chapters.size)
            assertTrue(parsed.contains("11999 Lorem"))
            assertTrue("5.6 MB book took $seconds s", seconds < 20)
        } finally {
            out.delete()
        }
    }

    @Test
    fun epubParserRejectsBrokenFilesCleanly() {
        val cases = mapOf<String, (ZipOutputStream) -> Unit>(
            "empty zip" to { },
            "no container" to { it.text("mimetype", "application/epub+zip") },
            "container points nowhere" to { it.text("META-INF/container.xml", container("OEBPS/missing.opf")) },
            "opf not xml" to { it.text("META-INF/container.xml", container("a.opf")); it.text("a.opf", "<<<not xml") },
            "empty spine" to { it.text("META-INF/container.xml", container("a.opf")); it.text("a.opf", opf("", "")) },
            "spine to missing file" to {
                it.text("META-INF/container.xml", container("a.opf"))
                it.text("a.opf", opf("""<item id="c1" href="nope.xhtml" media-type="application/xhtml+xml"/>""", """<itemref idref="c1"/>"""))
            },
            "path traversal href" to {
                it.text("META-INF/container.xml", container("a.opf"))
                it.text("a.opf", opf("""<item id="c1" href="../../../../etc/passwd" media-type="application/xhtml+xml"/>""", """<itemref idref="c1"/>"""))
            },
            "doctype with entities" to {
                it.text("META-INF/container.xml", container("a.opf"))
                it.text("a.opf", """<?xml version="1.0"?><!DOCTYPE p [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]>""" +
                    opf("", "").substringAfter("?>"))
            },
            "chapter without text" to {
                it.text("META-INF/container.xml", container("a.opf"))
                it.text("a.opf", opf("""<item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>""", """<itemref idref="c1"/>"""))
                it.text("c1.xhtml", "<html><head><title>x</title></head><body>   <p> </p></body></html>")
            }
        )
        for ((name, fill) in cases) {
            val f = File.createTempFile("broken", ".epub")
            ZipOutputStream(f.outputStream()).use(fill)
            val error = runCatching { EpubParser.parseEpubToText(f) }.exceptionOrNull()
            f.delete()
            assertNotNull("$name must fail", error)
            assertTrue("$name threw ${error!!::class.java.name}, not an Exception", error is Exception)
        }
        repeat(50) { seed ->
            val f = File.createTempFile("random", ".epub")
            f.writeBytes(Random(seed).nextBytes(Random(seed).nextInt(0, 5000)))
            val error = runCatching { EpubParser.parseEpubToText(f) }.exceptionOrNull()
            f.delete()
            assertTrue("seed $seed: $error", error is IllegalArgumentException)
        }
    }

    @Test
    fun htmlCleanerFuzz() {
        val rnd = Random(11)
        val pieces = listOf("<p>", "</p>", "<br/>", "&amp;", "&#x1F600;", "&#99999999;", "&#xZZ;", "&nbsp;", "&", "<", ">",
            "<script>evil()</script>", "<style>p{}</style>", "<!--", "-->", "text", "漢字", "\u0000", "<a href='x'>",
            "&#;", "&#x;", "&lt;", "&unknown;", "<header>", "</head>", "&#55296;")
        repeat(5_000) {
            val html = List(rnd.nextInt(0, 40)) { pieces.random(rnd) }.joinToString("")
            EpubParser.cleanHtmlToPlainText(html)
        }
        val clean = EpubParser.cleanHtmlToPlainText("<p>a</p><script>evil()</script><style>x{}</style><p>b &amp;lt; c</p>")
        assertFalse(clean, clean.contains("evil") || clean.contains("x{}"))
        assertTrue(clean, clean.contains("b &lt; c"))
    }

    @Test
    fun queueSurvivesEightThreadsAtOnce() {
        ConversionQueueManager.clearAll()
        val ids = ConversionQueueManager.addJobs(List(200) {
            ConversionJobRequest(TestUri("content://stress/$it"), "f$it.mp4", "video/mp4", "mkv", it % 3)
        })
        val failure = AtomicReference<Throwable?>(null)
        val pool = Executors.newFixedThreadPool(8)
        val go = CountDownLatch(1)
        repeat(8) { t ->
            pool.execute {
                go.await()
                val rnd = Random(t)
                try {
                    repeat(5_000) {
                        val id = ids.random(rnd)
                        when (rnd.nextInt(6)) {
                            0 -> ConversionQueueManager.updateJobStatus(id, JobStatus.PROCESSING)
                            1 -> ConversionQueueManager.updateJobProgress(id, rnd.nextInt(-10, 120), "p")
                            2 -> ConversionQueueManager.updateJobStatus(id, JobStatus.COMPLETED, filePath = "/out/$id")
                            3 -> ConversionQueueManager.cancelJob(id)
                            4 -> ConversionQueueManager.getQueueSummary()
                            else -> ConversionQueueManager.updateJobStatus(id, JobStatus.FAILED, error = "x")
                        }
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                }
            }
        }
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        assertNull(failure.get())
        val jobs = ConversionQueueManager.queueState.value
        assertEquals("no job lost or duplicated", 200, jobs.size)
        assertEquals(ids.toSet(), jobs.map { it.id }.toSet())
        for (j in jobs) {
            assertTrue(j.progress in 0..100)
            if (j.status == JobStatus.COMPLETED) assertEquals(100, j.progress)
        }
        val s = ConversionQueueManager.getQueueSummary()
        assertEquals(200, s.totalCount)
        assertEquals(200, s.completedCount + s.failedCount + s.cancelledCount + s.waitingCount + (if (s.activeJob != null) jobs.count { it.status == JobStatus.PROCESSING } else 0))
        ConversionQueueManager.clearAll()
    }

    @Test
    fun versionCompareFuzz() {
        val versions = listOf("1.0.0", "1.0.2", "1.1.0", "1.1", "1.10.0", "1.9.9", "v1.1.0", "V2.0", "1.1.0-beta", "1.1.0-rc.1",
            "", "v", "abc", "1..1", "999999999999.0", "1.1.0.1")
        for (a in versions) {
            assertFalse("$a vs itself", UpdateChecker.isNewerVersion(a, a))
            for (b in versions) assertFalse("$a <> $b", UpdateChecker.isNewerVersion(a, b) && UpdateChecker.isNewerVersion(b, a))
        }
        assertTrue(UpdateChecker.isNewerVersion("v1.10.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.1", "1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.1.0.1", "1.1.0"))
    }

    @Test
    fun targetListsAreCleanForAnyName() {
        val names = allSources.flatMap { listOf("a.$it", "A.${it.uppercase()}", "my.video.$it", " spaced .$it ", ".$it") } +
            listOf("", ".", "..", "file.", "no-ext", "ファイル.mp4", "a.b.c.d.e.png", "x".repeat(500) + ".mp3")
        for (n in names) for (mime in listOf(null, "video/mp4", "image/png", "audio/mpeg", "application/pdf", "text/plain", "*/*", "")) {
            val targets = ConversionCapabilityManager.getTargetFormats(n, mime)
            assertEquals("$n $mime", targets.distinct(), targets)
            assertTrue("$n $mime $targets", targets.all { t -> t.isNotBlank() && t == t.lowercase() && t.all(Char::isLetterOrDigit) })
        }
    }

    @Test
    fun jpegPdfWriterStaysValidAtScale() {
        val buf = ByteArrayOutputStream()
        val rnd = Random(9)
        JpegPdfWriter(buf).use { w ->
            repeat(300) {
                w.addPage(rnd.nextBytes(rnd.nextInt(100, 5000)), rnd.nextInt(1, 5000), rnd.nextInt(1, 5000),
                    rnd.nextFloat() * 1000 + 1, rnd.nextFloat() * 1000 + 1)
            }
        }
        val text = String(buf.toByteArray(), Charsets.ISO_8859_1)
        val startxref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.startsWith("xref\n", startxref))
        val xref = text.substring(startxref).split("\n")
        val size = xref[1].split(" ")[1].toInt()
        assertEquals(3 + 300 * 3, size)
        for (id in 1 until size) {
            val offset = xref[2 + id].substring(0, 10).toInt()
            assertTrue("object $id not at its xref offset", text.startsWith("$id 0 obj\n", offset))
        }
        assertTrue(text.contains("/Count 300 "))
        assertTrue(text.endsWith("%%EOF\n"))
    }

    @Test
    fun everyUiStringExistsInBothLanguages() {
        val samples = mapOf<Class<*>, Any>(
            Int::class.javaPrimitiveType!! to 3, String::class.java to "X", Long::class.javaPrimitiveType!! to 5L,
            Double::class.javaPrimitiveType!! to 1.5, Float::class.javaPrimitiveType!! to 1.5f, Boolean::class.javaPrimitiveType!! to true
        )
        var checked = 0
        for (m in Strings::class.java.methods) {
            if (m.returnType != String::class.java) continue
            val args = m.parameterTypes.map { samples[it] }
            if (args.any { it == null }) continue
            for (lang in listOf<Strings>(IndonesianStrings, EnglishStrings)) {
                val s = m.invoke(lang, *args.toTypedArray()) as String
                val who = "${lang::class.simpleName}.${m.name}"
                assertTrue("$who is blank", s.isNotBlank())
                assertFalse("$who has a leftover placeholder: $s", Regex("%[sd]|\\{\\d}|\\$\\{").containsMatchIn(s))
                checked++
            }
        }
        assertTrue("only $checked strings checked", checked > 400)
    }

    private fun String.squash() = replace(Regex("\\s+"), " ").trim()

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun container(opfPath: String) = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
        <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private fun opf(manifest: String, spine: String) = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0">
        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
        <manifest>$manifest</manifest><spine>$spine</spine></package>"""
}
