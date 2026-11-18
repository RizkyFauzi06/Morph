package com.morph.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StreamInformation
import com.morph.app.core.BmpWriter
import com.morph.app.core.ConversionCapabilityManager
import com.morph.app.core.EpubBuilder
import com.morph.app.core.EpubParser
import com.morph.app.core.MorphOutputFile
import com.morph.app.core.OutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.random.Random

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * Debug-only stress run. Generates varied media on the phone (FFmpegKit lavfi, Bitmap, PdfDocument,
 * EpubBuilder), runs every conversion the UI offers through MorphEngine.executeConversion, checks
 * each output and deletes it again. Results: logcat tag "MorphStress" and
 * Android/data/com.morph.app/files/stress_report.txt.
 *
 *   adb shell am start -n com.morph.app/.StressRunActivity [--es only video|audio|image|doc|special]
 */
class StressRunActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val text = TextView(this).apply { textSize = 11f; setPadding(24, 48, 24, 48) }
        setContentView(ScrollView(this).apply { addView(text) })
        val only = intent.getStringExtra("only")
        scope.launch {
            withContext(Dispatchers.IO) {
                StressRun(applicationContext, only) { line -> runOnUiThread { text.append(line + "\n") } }.run()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private class StressRun(private val ctx: Context, private val only: String?, private val show: (String) -> Unit) {
    private val root = File(ctx.getExternalFilesDir(null), "stress_in").apply { deleteRecursively(); mkdirs() }
    private val report = File(ctx.getExternalFilesDir(null), "stress_report.txt").apply { delete() }
    private val probeCache = mutableMapOf<String, MediaInformation?>()
    private val problems = mutableListOf<String>()
    private var passed = 0

    private class Case(
        val input: File, val target: String, val preset: Int = 0, val res: Int = 0, val fps: Int = 0,
        val expectFail: Boolean = false, val check: ((File) -> String?)? = null
    )

    private fun log(line: String) {
        Log.i("MorphStress", line)
        report.appendText(line + "\n")
        show(line)
    }

    private fun wants(group: String) = only == null || only == group

    suspend fun run() {
        val fdsBefore = openFds()
        val threadsBefore = Thread.getAllStackTraces().size
        val heapBefore = Debug.getNativeHeapAllocatedSize()
        val start = SystemClock.elapsedRealtime()
        log("=== Morph stress run on ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), x264-native=${MorphNative.isAvailable}")

        if (wants("video")) videoCases()
        if (wants("audio")) audioCases()
        if (wants("image")) imageCases()
        if (wants("doc")) documentCases()
        if (wants("special")) specialCases()
        if (only == "diag") diagCases()
        if (only == "vp9hw") vp9HardwareCases()
        if (only == "fps") fpsCases()

        val minutes = (SystemClock.elapsedRealtime() - start) / 60000.0
        log("=== Leaks: fds ${fdsBefore} -> ${openFds()}, threads $threadsBefore -> ${Thread.getAllStackTraces().size}, " +
            "native heap ${heapBefore / 1_048_576} MB -> ${Debug.getNativeHeapAllocatedSize() / 1_048_576} MB")
        log("=== SUMMARY: ${passed} passed, ${problems.size} failed in ${"%.1f".format(minutes)} min")
        problems.forEach { log("FAILED: $it") }
        log("=== DONE")
        root.deleteRecursively()
    }

    // ---------------------------------------------------------------- inputs

    private fun ffmpeg(args: String): Boolean {
        val s = FFmpegKit.execute("-y -hide_banner $args")
        if (!ReturnCode.isSuccess(s.returnCode)) log("GEN-FAILED: $args :: ${s.allLogsAsString?.takeLast(300)}")
        return ReturnCode.isSuccess(s.returnCode)
    }

    private fun gen(name: String, args: String): File? {
        val f = File(root, name)
        return f.takeIf { ffmpeg("$args \"${f.absolutePath}\"") && f.length() > 0 }
    }

    private fun video(size: String, rate: Int, seconds: Int) = "-f lavfi -i testsrc2=size=$size:rate=$rate:duration=$seconds"
    private fun tone(seconds: Number, freq: Int = 440) = "-f lavfi -i sine=frequency=$freq:sample_rate=48000:duration=$seconds"

    // ---------------------------------------------------------------- cases

    private suspend fun videoCases() {
        log("--- video inputs")
        val h264 = "-c:v libopenh264 -b:v 2M"
        val av = "-map 0:v -map 1:a"
        val inputs = listOfNotNull(
            gen("v_h264_aac.mp4", "${video("1280x720", 30, 4)} ${tone(4)} $av $h264 -c:a aac -ac 2 -b:a 192k"),
            hevcSource(),
            gen("v_land.mp4", "${video("1280x720", 30, 4)} ${tone(4)} $av $h264 -c:a aac -ac 2")?.let { land ->
                File(root, "v_portrait.mp4").takeIf { ffmpeg("-display_rotation 90 -i \"${land.absolutePath}\" -c copy \"${it.absolutePath}\"") }
                    .also { land.delete() }
            },
            gen("v_odd.mkv", "${video("641x361", 25, 3)} ${tone(3)} $av -c:v ffv1 -c:a flac"),
            gen("v_anamorphic.mkv", "${video("720x480", 30, 3)} ${tone(3)} $av -vf setsar=32/27 -c:v mpeg4 -q:v 4 -c:a ac3 -b:a 192k"),
            gen("v_noaudio.mp4", "${video("854x480", 30, 3)} $h264"),
            gen("v_twoaudio.mkv", "${video("854x480", 30, 3)} ${tone(3)} ${tone(3, 880)} -map 0:v -map 1:a -map 2:a $h264 -c:a:0 aac -c:a:1 libopus -ac 2"),
            gen("v_vfr.mp4", "${video("854x480", 30, 4)} ${tone(4)} $av -vf \"select='not(eq(mod(n\\,7)\\,3))*not(eq(mod(n\\,11)\\,5))'\" -fps_mode vfr $h264 -c:a aac -ac 2"),
            gen("v_vp8_vorbis.webm", "${video("854x480", 30, 3)} ${tone(3)} $av -c:v libvpx -deadline realtime -cpu-used 8 -b:v 1M -c:a libvorbis -ac 2"),
            gen("v_mpeg4_mp3.avi", "${video("640x480", 25, 3)} ${tone(3)} $av -c:v mpeg4 -q:v 5 -c:a libmp3lame -b:a 128k"),
            gen("v_pcm.mov", "${video("640x360", 30, 3)} ${tone(3)} $av $h264 -c:a pcm_s16le -ac 2"),
            gen("v_av1_10bit.mkv", "${video("320x240", 24, 2)} ${tone(2)} $av -c:v libaom-av1 -cpu-used 8 -row-mt 1 -pix_fmt yuv420p10le -b:v 400k -c:a libopus -ac 2"),
            gen("v_flv.flv", "${video("640x360", 30, 3)} ${tone(3)} $av $h264 -c:a aac -ac 2"),
            gen("v_3gp.3gp", "${video("352x288", 15, 3)} ${tone(3)} $av -c:v libopenh264 -b:v 300k -c:a aac -ac 1 -ar 16000 -b:a 32k"),
            gen("v_ts.ts", "${video("640x360", 30, 3)} ${tone(3)} $av $h264 -c:a aac -ac 2"),
            gen("v_wmv.wmv", "${video("640x360", 25, 3)} ${tone(3)} $av -c:v wmv2 -b:v 1M -c:a wmav2 -ac 2"),
            gen("v_theora.ogv", "${video("640x360", 25, 3)} ${tone(3)} $av -c:v libtheora -q:v 6 -c:a libvorbis -ac 2"),
            gen("v_itunes.m4v", "${video("640x360", 30, 3)} ${tone(3)} $av $h264 -c:a aac -ac 2 -f mp4"),
            gen("v_tiny_1s.mp4", "${video("320x240", 30, 1)} ${tone(1)} $av -c:v libopenh264 -b:v 500k -c:a aac -ac 2"),
            gen("v_small_360.mp4", "${video("640x360", 30, 3)} ${tone(3)} $av $h264 -c:a aac -ac 2")
        )
        log("generated ${inputs.size} video inputs")
        // Every offered target on High
        for (input in inputs) for (target in ConversionCapabilityManager.getTargetFormats(input.name)) {
            val noTrack = input.name == "v_noaudio.mp4" && ConversionCapabilityManager.isAudioOnlyTarget(target)
            runCase(Case(input, target, expectFail = noTrack))
        }
        // Presets, Instant, resolution and FPS on the most common sources
        for (name in listOf("v_h264_aac.mp4", "v_hevc_1080.mp4", "v_portrait.mp4", "v_twoaudio.mkv", "v_small_360.mp4")) {
            val input = inputs.find { it.name == name } ?: continue
            for (target in listOf("mp4", "mkv", "webm")) for (preset in 1..3) runCase(Case(input, target, preset))
        }
        inputs.find { it.name == "v_hevc_1080.mp4" }?.let {
            runCase(Case(it, "mp4", 0, res = 480, fps = 24))
            runCase(Case(it, "mov", 1, res = 720, fps = 60))
            runCase(Case(it, "webm", 1, res = 720, fps = 30))
        }
    }

    private suspend fun audioCases() {
        log("--- audio inputs")
        val inputs = listOfNotNull(
            gen("a.mp3", "${tone(8)} -c:a libmp3lame -b:a 192k -ac 2"),
            gen("a.wav", "${tone(4)} -c:a pcm_s16le -ac 2"),
            gen("a.flac", "${tone(4)} -c:a flac"),
            gen("a.m4a", "${tone(8)} -c:a aac -b:a 256k -ac 2"),
            gen("a.ogg", "${tone(4)} -c:a libvorbis -ac 2"),
            gen("a.opus", "${tone(4)} -c:a libopus -b:a 96k"),
            gen("a.aac", "${tone(4)} -c:a aac -b:a 128k -f adts"),
            gen("a.amr", "${tone(4)} -c:a libopencore_amrnb -ar 8000 -ac 1 -b:a 12.2k"),
            gen("a.aiff", "${tone(3)} -c:a pcm_s16be"),
            gen("a.wma", "${tone(3)} -c:a wmav2 -b:a 128k"),
            gen("a_short.mp3", "${tone(0.3)} -c:a libmp3lame -b:a 128k")
        )
        log("generated ${inputs.size} audio inputs")
        for (input in inputs) {
            val ext = input.extension
            for (target in ConversionCapabilityManager.getTargetFormats(input.name)) {
                runCase(Case(input, target, 0))
                runCase(Case(input, target, 2))
                if (ConversionCapabilityManager.isLosslessCompatible(ext, target)) runCase(Case(input, target, 3))
            }
        }
    }

    private suspend fun imageCases() {
        log("--- image inputs")
        val photo = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 800f, 600f, Color.TRANSPARENT, Color.BLUE, Shader.TileMode.CLAMP) })
            c.drawCircle(400f, 300f, 150f, Paint().apply { color = Color.RED })
        }
        fun save(name: String, write: (File) -> Unit) = File(root, name).also(write).takeIf { it.length() > 0 }
        val inputs = listOfNotNull(
            save("i_alpha.png") { f -> f.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) } },
            save("i.jpg") { f -> f.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) } },
            save("i.webp") { f -> f.outputStream().use { photo.compress(MorphEngine.compressFormatFor("webp", 90), 90, it) } },
            save("i.bmp") { f -> BmpWriter.writeBitmapAsBmp(photo, f) },
            save("i_tiny.png") { f -> f.outputStream().use { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) } },
            gen("i_anim.gif", "-f lavfi -i testsrc2=size=160x120:rate=5:duration=1"),
            gen("i.ico", "-f lavfi -i testsrc2=size=64x64:rate=1:duration=1 -frames:v 1 -pix_fmt rgba -c:v png -f ico"),
            gen("i_huge.jpg", "-f lavfi -i testsrc2=size=8600x4800:rate=1:duration=1 -frames:v 1 -q:v 6"),
            File(root, "i.svg").apply {
                writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="300" height="200"><rect width="300" height="200" fill="#09f"/><circle cx="150" cy="100" r="60" fill="#f30"/><text x="20" y="190">Morph</text></svg>""")
            }
        )
        gen("i_scan.tiff", "-f lavfi -i testsrc2=size=320x240:rate=1:duration=1 -frames:v 1 -c:v tiff")?.let {
            val offered = ConversionCapabilityManager.getTargetFormats(it.name)
            record("i_scan.tiff offers nothing", if (offered.isEmpty()) null else "offers $offered but Android can't decode TIFF", 0, 0)
        }
        log("generated ${inputs.size} image inputs")
        val whiteBehindAlpha: (File) -> String? = { out ->
            val bmp = BitmapFactory.decodeFile(out.absolutePath)
            val px = bmp?.getPixel(2, 2)
            if (px != null && Color.red(px) > 240 && Color.green(px) > 240 && Color.blue(px) > 240) null else "transparent corner is not white: ${px?.let { Integer.toHexString(it) }}"
        }
        for (input in inputs) for (target in ConversionCapabilityManager.getTargetFormats(input.name)) {
            val check = if (input.name == "i_alpha.png" && target in setOf("jpg", "bmp")) whiteBehindAlpha else null
            runCase(Case(input, target, 0, check = check))
            if (target in setOf("jpg", "webp")) runCase(Case(input, target, 2))
        }
        // Many photos into one PDF: size compared to the photos themselves
        val dir = File(root, "album").apply { mkdirs() }
        if (ffmpeg("-f lavfi -i testsrc2=size=1600x1200:rate=30:duration=1 -q:v 4 \"${dir.absolutePath}/p_%02d.jpg\"")) {
            val photos = dir.listFiles().orEmpty().sortedBy { it.name }
            val inBytes = photos.sumOf { it.length() }
            val before = outputKeys()
            val t0 = SystemClock.elapsedRealtime()
            val r = runCatching { MorphEngine.mergeImagesToPdf(ctx, photos.map(::uriOf), "album.jpg", 0) { _, _ -> } }
            val ms = SystemClock.elapsedRealtime() - t0
            val created = outputs().filter { it.uri.toString() !in before }
            val outBytes = created.sumOf { it.size }
            val ratio = if (inBytes > 0) outBytes.toDouble() / inBytes else 0.0
            record("${photos.size} JPGs -> 1 PDF", when {
                r.isFailure -> "FAILED: ${r.exceptionOrNull()?.message}"
                ratio > 3 -> "PDF is ${"%.1f".format(ratio)}x the photos (${inBytes / 1024} KB -> ${outBytes / 1024} KB)"
                else -> null
            }, ms, outBytes, "ratio ${"%.2f".format(ratio)}")
            created.forEach { OutputManager.deleteOutputFile(ctx, it) }
        }
    }

    private suspend fun documentCases() {
        log("--- documents")
        val pdf = File(root, "doc.pdf").also { writePdf(it, pages = 4, scanPage = true) }
        val many = File(root, "many_pages.pdf").also { writePdf(it, pages = 40, scanPage = false) }
        for (input in listOf(pdf, many)) for (target in ConversionCapabilityManager.getTargetFormats(input.name)) {
            // The text-only PDF can't get smaller: Morph keeps it and says so
            if (target == "pdf") for (preset in 0..2) runCase(Case(input, target, preset, expectFail = input == many))
            else runCase(Case(input, target))
        }
        val chapters = (1..40).joinToString("\n\n") { c -> "Chapter $c\n\n" + (1..120).joinToString("\n\n") { "Paragraf $c.$it " + "kata ".repeat(40) } }
        val texts = listOf(
            File(root, "big_book.txt").apply { writeText(chapters) } to "Chapter 40",
            File(root, "notepad.txt").apply { writeBytes(("\uFEFF" + "Satu baris\r\n\r\nDua baris").toByteArray()) } to "Dua baris",
            File(root, "legacy.txt").apply { writeBytes("café naïve Señor".toByteArray(charset("windows-1252"))) } to "café naïve",
            File(root, "damaged.txt").apply { writeBytes(("Halo 漢字 😀 ".repeat(200)).toByteArray() + byteArrayOf(0xE2.toByte(), 0x82.toByte())) } to "漢字 😀"
        )
        for ((file, marker) in texts) {
            runCase(Case(file, "epub", check = { out ->
                val text = runCatching { EpubParser.parseEpubToText(out) }.getOrElse { return@Case "unreadable EPUB: ${it.message}" }
                if (text.contains(marker)) null else "\"$marker\" missing (mojibake?)"
            }))
        }
        runCase(Case(File(root, "empty.txt").apply { writeBytes(ByteArray(0)) }, "epub", expectFail = true))
        runCase(Case(File(root, "blank.txt").apply { writeText("  \n\n \t ") }, "epub", expectFail = true))

        val book = File(root, "book.epub")
        EpubBuilder.build(EpubBuilder.splitChapters(chapters) { "Part $it" }, "Buku <Stress> & 😀", "id", book)
        runCase(Case(book, "txt", check = { out -> if (out.readText().contains("Paragraf 40.120")) null else "last paragraph missing" }))
        runCase(Case(File(root, "random.epub").apply { writeBytes(Random(1).nextBytes(4096)) }, "txt", expectFail = true))
        // External entity: the output must never contain the app's own files
        val xxe = File(root, "xxe.epub").also {
            writeEpub(it, """<?xml version="1.0"?><!DOCTYPE package [<!ENTITY x SYSTEM "file:///proc/self/status">]>""", "&x;")
        }
        runCase(Case(xxe, "txt", check = { out -> if (out.readText().contains("TracerPid")) "XXE: /proc/self/status leaked into the TXT" else null }))
    }

    private suspend fun specialCases() {
        log("--- special")
        val base = hevcSource() ?: return
        val clip = File(root, "s_hevc_12s.mp4").takeIf { ffmpeg("-stream_loop 1 -i \"${base.absolutePath}\" -c copy -t 12 \"${it.absolutePath}\"") } ?: return
        encoderPaths()
        // Instant vs High into M4A (same command): alternate so neither always gets the warm file cache
        for (preset in listOf(3, 0, 3, 0)) runCase(Case(clip, "m4a", preset))
        // Cancel at different moments, then the next conversion must still work
        for (delayMs in listOf(300L, 2_000L, 5_000L)) {
            val before = outputKeys()
            val result = coroutineScope {
                val job = async {
                    runCatching { MorphEngine.executeConversion(ctx, uriOf(clip), "mp4", clip.name, 0, { _, _ -> }) }
                }
                delay(delayMs)
                val t0 = SystemClock.elapsedRealtime()
                MorphEngine.cancelCurrentConversion()
                job.await() to SystemClock.elapsedRealtime() - t0
            }
            val (r, stopMs) = result
            outputs().filter { it.uri.toString() !in before }.forEach { OutputManager.deleteOutputFile(ctx, it) }
            record("cancel after ${delayMs} ms", when {
                r.isSuccess -> "finished instead of cancelling"
                stopMs > 3_000 -> "took $stopMs ms to stop"
                else -> null
            }, stopMs, 0)
            runCase(Case(clip, "mkv", 2)) // works right after a cancel
        }
        // A 12 s 1080p HEVC clip on High, as in the device QA
        runCase(Case(clip, "mp4", 0))
        runCase(Case(clip, "mkv", 0))
        // Entity expansion ("billion laughs", kept small): must fail or stay small, never blow up
        val bomb = File(root, "laughs.epub").also {
            val ents = (1..5).joinToString("") { i -> "<!ENTITY e$i \"" + "&e${i - 1};".repeat(10) + "\">" }
            writeEpub(it, """<?xml version="1.0"?><!DOCTYPE package [<!ENTITY e0 "ha">$ents]>""", "&e5;")
        }
        runCase(Case(bomb, "txt", check = { out ->
            if (out.length() > 100_000) "entities expanded to ${out.length() / 1024} KB: a real bomb would exhaust memory" else null
        }))
    }

    /** Targeted FFmpeg experiments (only = "diag"): which encoder/decoder pairs really work here. */
    private fun diagCases() {
        log("--- diag")
        fun attempt(name: String, args: String) {
            val s = FFmpegKit.execute("-y -hide_banner $args")
            val ok = ReturnCode.isSuccess(s.returnCode)
            if (ok) passed++ else problems += name
            log("${if (ok) "PASS" else "FAIL"} $name" + if (ok) "" else " :: " + (s.allLogsAsString ?: "").takeLast(500).replace('\n', ' '))
        }
        val src = "-f lavfi -i testsrc2=size=640x360:rate=30:duration=2"
        val p = root.absolutePath
        attempt("vp9 libvpx realtime encode", "$src -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -b:v 1M \"$p/rt.webm\"")
        attempt("vp9 libvpx realtime -> native vp9 decoder", "-i \"$p/rt.webm\" -f null -")
        attempt("vp9 libvpx realtime -> libvpx-vp9 decoder", "-c:v libvpx-vp9 -i \"$p/rt.webm\" -f null -")
        attempt("vp9 libvpx realtime row-mt tiles (Morph args) encode", "$src -c:v libvpx-vp9 -deadline realtime -cpu-used 6 -row-mt 1 -tile-columns 2 -lag-in-frames 0 -b:v 1M -pix_fmt yuv420p \"$p/morph.webm\"")
        attempt("vp9 Morph args -> native decoder", "-i \"$p/morph.webm\" -f null -")
        attempt("vp9 libvpx good encode", "$src -c:v libvpx-vp9 -deadline good -cpu-used 4 -b:v 1M \"$p/good.webm\"")
        attempt("vp9 libvpx good -> native decoder", "-i \"$p/good.webm\" -f null -")
        attempt("vp9 libvpx -> ivf (no WebM muxer)", "$src -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -b:v 1M \"$p/rt.ivf\"")
        attempt("vp9 ivf -> native decoder", "-i \"$p/rt.ivf\" -f null -")
        attempt("vp9 libvpx -> vp9_mediacodec (hardware) decoder", "-c:v vp9_mediacodec -i \"$p/rt.webm\" -f null -")
        attempt("vp9 libvpx 1 thread no alt-ref encode", "$src -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -threads 1 -row-mt 0 -auto-alt-ref 0 -lag-in-frames 0 -b:v 1M \"$p/t1.webm\"")
        attempt("vp9 1 thread no alt-ref -> native decoder", "-i \"$p/t1.webm\" -f null -")
        attempt("vp9 libvpx lossless encode", "$src -frames:v 5 -c:v libvpx-vp9 -lossless 1 \"$p/ll.webm\"")
        attempt("vp9 lossless -> native decoder", "-i \"$p/ll.webm\" -f null -")
        attempt("vp9 libvpx crf encode", "$src -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -crf 32 -b:v 0 \"$p/crf.webm\"")
        attempt("vp9 crf -> native decoder", "-i \"$p/crf.webm\" -f null -")
        attempt("vp9_mediacodec encode", "$src -pix_fmt nv12 -c:v vp9_mediacodec -b:v 1M \"$p/hw.webm\"")
        attempt("vp9_mediacodec -> native decoder", "-i \"$p/hw.webm\" -f null -")
        attempt("vp8 libvpx encode", "$src -c:v libvpx -deadline realtime -cpu-used 8 -b:v 1M \"$p/vp8.webm\"")
        attempt("vp8 libvpx -> native decoder", "-i \"$p/vp8.webm\" -f null -")
        attempt("av1 -> dav1d decoder", "$src -c:v libaom-av1 -cpu-used 8 -b:v 500k \"$p/av1.mkv\" ")
        attempt("av1 decode", "-i \"$p/av1.mkv\" -f null -")
        val mono = "-f lavfi -i sine=frequency=440:sample_rate=48000:duration=2"
        val mono8k = "-f lavfi -i sine=frequency=440:sample_rate=8000:duration=2"
        attempt("vorbis mono 48k -b:a 256k", "$mono -c:a libvorbis -b:a 256k \"$p/a1.ogg\"")
        attempt("vorbis mono 48k -b:a 160k", "$mono -c:a libvorbis -b:a 160k \"$p/a2.ogg\"")
        attempt("vorbis mono 48k -b:a 112k", "$mono -c:a libvorbis -b:a 112k \"$p/a3.ogg\"")
        attempt("vorbis mono 8k -b:a 112k", "$mono8k -c:a libvorbis -b:a 112k \"$p/a4.ogg\"")
        attempt("vorbis mono 48k -q:a 6", "$mono -c:a libvorbis -q:a 6 \"$p/a5.ogg\"")
        attempt("vorbis mono 8k -q:a 2", "$mono8k -c:a libvorbis -q:a 2 \"$p/a6.ogg\"")
        attempt("vorbis stereo 48k -b:a 256k", "$mono -ac 2 -c:a libvorbis -b:a 256k \"$p/a7.ogg\"")
        attempt("opus mono 8k -b:a 160k", "$mono8k -c:a libopus -b:a 160k \"$p/a8.opus\"")
        attempt("aac mono 8k -b:a 256k", "$mono8k -c:a aac -b:a 256k \"$p/a9.m4a\"")
        attempt("mp3 mono 8k -b:a 320k", "$mono8k -c:a libmp3lame -b:a 320k \"$p/a10.mp3\"")
        attempt("ico rgba png", "-f lavfi -i testsrc2=size=64x64:rate=1:duration=1 -frames:v 1 -pix_fmt rgba -c:v png -f ico \"$p/i.ico\"")
    }

    /** FPS option through the real engine (x264-native on this phone): frame counts per container and rate. */
    private suspend fun fpsCases() {
        log("--- fps")
        val src = hevcSource() ?: return
        val small = gen("f_360_30.mp4", "${video("640x360", 30, 4)} ${tone(4)} -map 0:v -map 1:a -c:v libopenh264 -b:v 2M -c:a aac -ac 2") ?: return
        for (input in listOf(small, src)) for (target in listOf("mp4", "mov", "mkv")) for (fps in listOf(24, 30, 50, 60)) {
            runCase(Case(input, target, 1, res = if (input == src) 720 else 0, fps = fps))
        }
    }

    /** Frames that really decode vs frames sent, for vp9_mediacodec at the settings Morph uses. */
    private fun vp9HardwareCases() {
        log("--- vp9 hardware")
        fun decodedFrames(f: File): Int {
            val s = com.arthenica.ffmpegkit.FFprobeKit.execute("-v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames -of csv=p=0 \"${f.absolutePath}\"")
            return s.output?.trim()?.lines()?.lastOrNull()?.trim()?.toIntOrNull() ?: -1
        }
        val encoders = listOf(
            "vp8_mediacodec" to "-vf format=nv12 -c:v vp8_mediacodec -bitrate_mode cbr -g 60",
            "libvpx(vp8)" to "-vf format=yuv420p -c:v libvpx -deadline realtime -cpu-used 6 -lag-in-frames 0",
            "vp9_mediacodec g=1" to "-vf format=nv12 -c:v vp9_mediacodec -bitrate_mode cbr -g 1"
        )
        for ((name, args) in encoders) for (size in listOf("640x360", "1920x1080")) for (seconds in listOf(1, 3, 10)) for (kbps in listOf(700, 4000)) {
            val out = File(root, "enc_${size}_${seconds}_$kbps.webm")
            val t0 = SystemClock.elapsedRealtime()
            val ok = ReturnCode.isSuccess(FFmpegKit.execute("-y -f lavfi -i testsrc2=size=$size:rate=30:duration=$seconds $args -b:v ${kbps}k \"${out.absolutePath}\"").returnCode)
            val ms = SystemClock.elapsedRealtime() - t0
            val got = if (ok) decodedFrames(out) else -1
            record("$name $size ${seconds}s ${kbps}k", if (!ok) "encode failed" else if (got != seconds * 30) "decoded $got of ${seconds * 30} frames" else null, ms, out.length())
            out.delete()
        }
        val sizes = listOf<String>()
        for (size in sizes) for (seconds in listOf(1, 3, 6)) for (kbps in listOf(300, 700, 2000)) for (mode in listOf("cbr", "vbr")) {
            val out = File(root, "hw_${size}_${seconds}_${kbps}_$mode.webm")
            val cmd = "-y -f lavfi -i testsrc2=size=$size:rate=30:duration=$seconds -vf format=nv12 -c:v vp9_mediacodec -bitrate_mode $mode -g 60 -b:v ${kbps}k \"${out.absolutePath}\""
            val ok = ReturnCode.isSuccess(FFmpegKit.execute(cmd).returnCode)
            val sent = seconds * 30
            val got = if (ok) decodedFrames(out) else -1
            val problem = when {
                !ok -> "encode failed"
                got != sent -> "decoded $got of $sent frames"
                else -> null
            }
            record("vp9_mediacodec $size ${seconds}s ${kbps}k $mode", problem, 0, out.length())
            out.delete()
        }
        // Same check for the H.264 hardware encoder, for comparison
        for (size in listOf("320x240", "854x480")) for (kbps in listOf(300, 2000)) {
            val out = File(root, "h264hw_${size}_$kbps.mp4")
            val ok = ReturnCode.isSuccess(FFmpegKit.execute("-y -f lavfi -i testsrc2=size=$size:rate=30:duration=3 -vf format=nv12 -c:v h264_mediacodec -bitrate_mode cbr -g 60 -b:v ${kbps}k \"${out.absolutePath}\"").returnCode)
            val got = if (ok) decodedFrames(out) else -1
            record("h264_mediacodec $size 3s ${kbps}k cbr", if (!ok) "encode failed" else if (got != 90) "decoded $got of 90 frames" else null, 0, out.length())
            out.delete()
        }
    }

    // ---------------------------------------------------------------- run + check

    private suspend fun runCase(c: Case) {
        val label = "${c.input.name} -> ${c.target} p${c.preset}" + (if (c.res > 0) " ${c.res}p" else "") + (if (c.fps > 0) " ${c.fps}fps" else "")
        val before = outputKeys()
        var detail: String? = null
        val t0 = SystemClock.elapsedRealtime()
        val result = withTimeoutOrNull(300_000) {
            runCatching {
                MorphEngine.executeConversion(ctx, uriOf(c.input), c.target, c.input.name, c.preset, { _, _ -> }, c.res, c.fps) { detail = it }
            }
        } ?: run {
            MorphEngine.cancelCurrentConversion()
            Result.failure(IllegalStateException("timeout after 5 min"))
        }
        val ms = SystemClock.elapsedRealtime() - t0
        val created = outputs().filter { it.uri.toString() !in before }
        val out = created.firstOrNull()?.let(::localCopy)
        val problem = try {
            when {
                c.expectFail && result.isSuccess -> "should have failed"
                c.expectFail -> if (result.exceptionOrNull()?.message.isNullOrBlank()) "failed without a message" else null
                result.isFailure -> "FAILED: ${result.exceptionOrNull()?.message?.take(200)}"
                out == null || out.length() <= 0 -> "no output file"
                created.size > 1 && !(c.input.extension == "pdf" && c.target != "pdf") -> "${created.size} outputs for one conversion"
                else -> c.check?.invoke(out) ?: checkOutput(c, out)
            }
        } catch (e: Exception) {
            "check crashed: $e"
        }
        record(label, problem, ms, out?.length() ?: 0, listOfNotNull(detail, mediaNote(c, out)).joinToString("; "))
        created.forEach { OutputManager.deleteOutputFile(ctx, it) }
        if (out != null && out.parentFile == root) out.delete()
    }

    private fun record(label: String, problem: String?, ms: Long, bytes: Long, note: String = "") {
        val line = "%s %6d ms %8d KB  %s%s".format(if (problem == null) "PASS" else "FAIL", ms, bytes / 1024, label,
            listOfNotNull(problem?.let { " !! $it" }, note.takeIf { it.isNotBlank() }?.let { " | $it" }).joinToString(""))
        if (problem == null) passed++ else problems += "$label: $problem"
        log(line)
    }

    private val videoTargets = setOf("mp4", "mkv", "mov", "webm", "avi", "gif")

    private fun checkOutput(c: Case, out: File): String? {
        val target = c.target
        if (target in setOf("png", "jpg", "jpeg", "webp", "bmp")) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(out.absolutePath, o)
            return if (o.outWidth > 0 && o.outHeight > 0) null else "image does not decode"
        }
        if (target == "pdf") return if (out.readBytes().copyOf(5).decodeToString() == "%PDF-") null else "not a PDF"
        if (target in setOf("txt", "epub")) return null
        val info = FFprobeKit.getMediaInformation(out.absolutePath).mediaInformation ?: return "output can't be probed"
        val src = probe(c.input)
        val v = info.streams.firstOrNull { it.type == "video" && !attached(it) }
        val audio = info.streams.filter { it.type == "audio" }
        val srcHasAudio = src?.streams?.any { it.type == "audio" } == true
        if (target in videoTargets) {
            v ?: return "no video stream"
            val w = v.width?.toInt() ?: 0
            val h = v.height?.toInt() ?: 0
            if (target != "gif" && (w % 2 != 0 || h % 2 != 0)) return "odd size ${w}x$h"
            val (dw, dh) = displaySize(v)
            val (sw, sh) = src?.streams?.firstOrNull { it.type == "video" && !attached(it) }?.let(::displaySize) ?: (0.0 to 0.0)
            if (sw > 0 && (sw > sh) != (dw > dh) && abs(sw - sh) > 1) return "orientation changed: ${sw.toInt()}x${sh.toInt()} -> ${dw.toInt()}x${dh.toInt()}"
            if (sw > 0 && c.preset != 3 && target != "gif" && abs((dw / dh) / (sw / sh) - 1) > 0.03) return "shape changed: ${"%.3f".format(sw / sh)} -> ${"%.3f".format(dw / dh)}"
            if (c.preset == 2 && c.res == 0 && maxOf(w, h) > 854 && target != "gif") return "Low is ${w}x$h (> 854)"
            if (sw > 0 && c.preset != 3 && maxOf(dw, dh) > maxOf(sw, sh) + 2) return "upscaled ${sw.toInt()}x${sh.toInt()} -> ${dw.toInt()}x${dh.toInt()}"
            if (c.res > 0 && minOf(w, h) > c.res) return "resolution ${c.res} ignored: ${w}x$h"
            if (srcHasAudio && target != "gif" && audio.isEmpty()) return "audio lost"
        } else if (ConversionCapabilityManager.isAudioOnlyTarget(target)) {
            if (v != null) return "video kept in an audio file"
            if (audio.size != 1) return "${audio.size} audio streams"
        }
        val srcDur = src?.duration?.toDoubleOrNull() ?: 0.0
        val outDur = info.duration?.toDoubleOrNull() ?: 0.0
        if (srcDur > 0 && outDur > 0 && abs(srcDur - outDur) > maxOf(0.5, srcDur * 0.1)) return "duration ${"%.2f".format(srcDur)} s -> ${"%.2f".format(outDur)} s"
        if (target in videoTargets && target != "gif" && v != null) frameProblem(out, v, info, if (c.fps == 0) frameCount(c.input) else -1)?.let { return it }
        return decodeProblem(out, target in videoTargets)
    }

    /**
     * Every frame must decode: vp9_mediacodec wrote files whose last 30-60 frames did not, which
     * an exit code or a 30-frame decode never noticed. Tolerance: 2% or 3 frames (CFR rounding).
     */
    private val frameCache = mutableMapOf<String, Int>()

    private fun frameCount(f: File, cache: Boolean = true): Int {
        if (cache) frameCache[f.absolutePath]?.let { return it }
        val s = FFprobeKit.execute("-v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames -of csv=p=0 \"${f.absolutePath}\"")
        val n = s.output?.trim()?.lines()?.lastOrNull()?.trim()?.toIntOrNull() ?: -1
        if (cache) frameCache[f.absolutePath] = n
        return n
    }

    /** [sourceFrames] >= 0: a variable-rate source keeps its own frames, so never expect more than it has. */
    private fun frameProblem(out: File, v: StreamInformation, info: MediaInformation, sourceFrames: Int): String? {
        val rate = v.averageFrameRate?.split('/')?.let { p ->
            val n = p.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val d = p.getOrNull(1)?.toDoubleOrNull() ?: 1.0
            if (d > 0) n / d else 0.0
        } ?: 0.0
        val seconds = v.allProperties?.optString("duration")?.toDoubleOrNull() ?: info.duration?.toDoubleOrNull() ?: 0.0
        val expected = if (sourceFrames > 0) minOf(rate * seconds, sourceFrames.toDouble()) else rate * seconds
        if (expected < 1) return null
        val decoded = frameCount(out, cache = false) // outputs reuse names: never cache them
        return if (decoded < expected - maxOf(3.0, expected * 0.02)) "decoded $decoded of ~${expected.toInt()} frames" else null
    }

    /** Null when the first 30 frames (video) or 2 s (audio) really decode. */
    private fun decodeProblem(out: File, video: Boolean): String? {
        val s = FFmpegKit.execute("-v error -i \"${out.absolutePath}\" ${if (video) "-frames:v 30" else "-t 2"} -f null -")
        return if (ReturnCode.isSuccess(s.returnCode)) null else "does not decode: " + (s.allLogsAsString ?: "").takeLast(160).replace('\n', ' ')
    }

    private fun mediaNote(c: Case, out: File?): String? {
        if (out == null || c.target !in videoTargets && !ConversionCapabilityManager.isAudioOnlyTarget(c.target)) return null
        val info = runCatching { FFprobeKit.getMediaInformation(out.absolutePath).mediaInformation }.getOrNull() ?: return null
        val v = info.streams.firstOrNull { it.type == "video" && !attached(it) }
        val a = info.streams.firstOrNull { it.type == "audio" }
        val srcA = probe(c.input)?.streams?.filter { it.type == "audio" }.orEmpty()
        val copyExpected = MorphEngine.shouldCopyAudio(c.target, c.preset, srcA.map { it.codec ?: "" }, srcA.singleOrNull()?.bitrate?.toLongOrNull())
        return listOfNotNull(
            v?.let { "${it.codec} ${it.width}x${it.height}" },
            a?.let { "${it.codec} ${(it.bitrate?.toLongOrNull() ?: 0) / 1000}k" + if (copyExpected) " (kept)" else "" }
        ).joinToString(", ")
    }

    // ---------------------------------------------------------------- helpers

    private fun probe(f: File): MediaInformation? = probeCache.getOrPut(f.absolutePath) {
        runCatching { FFprobeKit.getMediaInformation(f.absolutePath).mediaInformation }.getOrNull()
    }

    private fun attached(s: StreamInformation) = s.allProperties?.optJSONObject("disposition")?.optInt("attached_pic", 0) == 1

    /** Width/height as shown: sample aspect ratio applied, then the display rotation. */
    private fun displaySize(s: StreamInformation): Pair<Double, Double> {
        val sar = s.sampleAspectRatio?.split(':')?.let { p ->
            val n = p.getOrNull(0)?.toDoubleOrNull()
            val d = p.getOrNull(1)?.toDoubleOrNull()
            if (n != null && d != null && n > 0 && d > 0) n / d else null
        } ?: 1.0
        val w = (s.width ?: 0) * sar
        val h = (s.height ?: 0).toDouble()
        val sides = s.allProperties?.optJSONArray("side_data_list")
        val rotation = (0 until (sides?.length() ?: 0)).firstNotNullOfOrNull { i -> sides?.optJSONObject(i)?.takeIf { it.has("rotation") }?.optInt("rotation") } ?: 0
        return if (abs(rotation) % 180 == 90) h to w else w to h
    }

    private fun uriOf(f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)

    private fun outputs(): List<MorphOutputFile> = OutputManager.listOutputFiles(ctx)

    private fun outputKeys(): Set<String> = outputs().map { it.uri.toString() }.toSet()

    /** SAF outputs have no File: copy them next to the inputs so they can be probed. */
    private fun localCopy(item: MorphOutputFile): File? = item.file ?: runCatching {
        File(root, "out_${System.nanoTime()}.${item.extension}").also { dst ->
            ctx.contentResolver.openInputStream(item.uri)!!.use { input -> dst.outputStream().use { input.copyTo(it) } }
        }
    }.getOrNull()

    private fun openFds() = File("/proc/self/fd").list()?.size ?: -1

    /**
     * The 1080p HEVC source, encoded once: libkvazaar aborts the process (pthread_mutex_destroy on a
     * destroyed mutex) when a second kvazaar encoder closes. Morph itself never encodes with kvazaar.
     */
    private fun hevcSource(): File? = File(root, "v_hevc_1080.mp4").takeIf { it.length() > 0 }
        ?: gen("v_hevc_1080.mp4", "${video("1920x1080", 30, 6)} ${tone(6)} -map 0:v -map 1:a -c:v libkvazaar -kvazaar-params preset=ultrafast -b:v 8M -tag:v hvc1 -c:a aac -ac 2 -b:a 320k")

    /**
     * Encoders other phones end up on but this one never picks by itself (its hardware VP9 and
     * x264-native win): Morph's exact FFmpeg commands for VP8, libopenh264 and h264_mediacodec.
     */
    private fun encoderPaths() {
        log("--- encoder paths")
        val sources = listOfNotNull(File(root, "v_h264_aac.mp4"), File(root, "v_portrait.mp4"), hevcSource()).filter { it.length() > 0 }
        val plan = listOf("webm" to "libvpx", "mp4" to "libopenh264", "mkv" to "libopenh264", "mov" to "libopenh264", "mp4" to "h264_mediacodec")
        for (src in sources) for ((target, encoder) in plan) for (preset in 0..2) {
            if (encoder == "h264_mediacodec" && preset > 0) continue
            val rotation = probe(src)?.streams?.firstOrNull { it.type == "video" }?.let { st ->
                val sides = st.allProperties?.optJSONArray("side_data_list")
                (0 until (sides?.length() ?: 0)).firstNotNullOfOrNull { i -> sides?.optJSONObject(i)?.takeIf { it.has("rotation") }?.optInt("rotation") }
            } ?: 0
            val out = File(root, "path_${System.nanoTime()}.$target")
            val audio = probe(src)?.streams?.filter { it.type == "audio" }.orEmpty()
            val copy = MorphEngine.shouldCopyAudio(target, preset, audio.map { it.codec ?: "" }, audio.singleOrNull()?.bitrate?.toLongOrNull())
            val cmd = MorphEngine.buildFFmpegCommand(src.absolutePath, out, src.extension, target, preset, 4_000_000L, abs(rotation), 0, 0, encoder, copy)
            val t0 = SystemClock.elapsedRealtime()
            val session = FFmpegKit.execute(cmd)
            val ms = SystemClock.elapsedRealtime() - t0
            val c = Case(src, target, preset)
            val problem = if (!ReturnCode.isSuccess(session.returnCode)) "FAILED: " + (session.allLogsAsString ?: "").takeLast(200).replace('\n', ' ')
                else runCatching { checkOutput(c, out) }.getOrElse { "check crashed: $it" }
            record("[$encoder] ${src.name} -> $target p$preset", problem, ms, out.length(), mediaNote(c, out) ?: "")
            out.delete()
        }
    }

    private fun writePdf(file: File, pages: Int, scanPage: Boolean) {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 18f }
        // A "scanned" page: a 150 DPI camera-like picture (pattern + sensor noise), what compressPdf shrinks
        val scan = if (scanPage && gen("scan.png", "-f lavfi -i testsrc2=size=1240x1754:rate=1:duration=1 -vf noise=alls=12:allf=t -frames:v 1") != null) {
            BitmapFactory.decodeFile(File(root, "scan.png").absolutePath)
        } else null
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            if (scan != null && i == 0) page.canvas.drawBitmap(scan, null, android.graphics.Rect(0, 0, 595, 842), null)
            repeat(30) { line -> page.canvas.drawText("Halaman ${i + 1}, baris ${line + 1}: Morph stress test", 40f, 60f + line * 24, paint) }
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        scan?.recycle()
    }

    private fun writeEpub(file: File, prolog: String, title: String) {
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="a.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("a.opf", "$prolog<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>$title</dc:title></metadata>" +
                "<manifest><item id=\"c\" href=\"c.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"c\"/></spine></package>")
            put("c.xhtml", "<html><body><p>Isi bab satu.</p></body></html>")
        }
    }
}
