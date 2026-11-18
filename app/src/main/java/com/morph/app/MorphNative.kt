package com.morph.app

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/**
 * JNI bridge to libmorph-native (x264 with NEON, built from app/src/main/cpp).
 *
 * Only the H.264 *encoding* is native: FFmpeg decodes and scales the source into a named pipe,
 * x264 encodes the raw frames to an Annex-B file, and FFmpeg muxes it with the original audio
 * (MorphEngine.runNativeX264). Every format, filter and container stays in FFmpeg.
 */
object MorphNative {

    /**
     * False in JVM unit tests, on ABIs the library isn't built for (x86_64 emulators) and on
     * checkouts without the gitignored x264 source. Callers then use libopenh264.
     */
    val isAvailable: Boolean = runCatching { System.loadLibrary("morph-native") }.isSuccess

    const val ERR_CANCELLED = -7L

    /** morph_native.cpp's error codes are -1..-8; -64..0 is reserved for them. */
    private const val ERR_MIN = -64L

    /**
     * True for nativeX264Open's error results. Never test the handle's sign: on Android 11+ arm64
     * the heap tags pointers in the top byte (Scudo, usually 0xB4), so a valid Session* is a
     * NEGATIVE jlong. "handle <= 0" rejected every open on a Redmi 15 (rc=1 within 1 ms).
     */
    fun isOpenError(handle: Long): Boolean = handle in ERR_MIN..0L

    /**
     * Opens the pipe (non-blocking read end) and the encoder. Returns an opaque handle (may be
     * negative, see isOpenError) or an error code -1..-8.
     */
    @JvmStatic external fun nativeX264Open(
        pipePath: String,
        outPath: String,
        width: Int,
        height: Int,
        fpsNum: Int,
        fpsDen: Int,
        preset: String,
        bitrateKbps: Int,
        keyint: Int
    ): Long

    /** Blocks until the writer closes the pipe. Returns encoded frames (>= 0) or an error < 0. */
    @JvmStatic external fun nativeX264Encode(handle: Long): Long

    /** Frees the encoder and closes both files. Call exactly once per successful open. */
    @JvmStatic external fun nativeX264Close(handle: Long)

    @JvmStatic external fun nativeCancel()

    fun cancel() {
        if (isAvailable) nativeCancel()
    }

    /**
     * x264 speed preset per Morph quality preset. High and Medium share superfast: on a Redmi 15
     * veryfast managed 14.3 fps on 1080p against 19.2 fps, and at Morph's bitrates (High ~18 Mbps
     * for 1080p) the picture difference is hard to see. Quality comes from the bitrate. Low uses
     * ultrafast, which also turns off CABAC, so old and cheap decoders play it.
     */
    fun x264PresetFor(qualityPreset: Int): String = when (qualityPreset) {
        2 -> "ultrafast"
        else -> "superfast" // High and Medium (Instant never reaches an encoder)
    }

    /**
     * Exact output size the native path asks FFmpeg for (x264 needs it before the first frame).
     * Mirrors MorphEngine.buildVideoFilterArgs: rotation first (autorotate), then the PBI 5.2
     * short-side target or the Low preset's 854 px long side, then even dimensions.
     * [sar] is ffprobe's sample aspect ratio ("32:27"). The raw pipe can't carry it, so non-square
     * pixels (DVD, HDV) are resized to their display shape here instead of playing squashed.
     */
    fun outputSize(
        width: Int, height: Int, rotationDegrees: Int, qualityPreset: Int, videoResolution: Int, sar: String? = null
    ): Pair<Int, Int> {
        val displayWidth = Math.round(width * sampleAspect(sar)).toInt() // SAR applies before the turn
        val rotated = rotationDegrees % 180 != 0
        val w = (if (rotated) height else displayWidth).coerceAtLeast(2)
        val h = (if (rotated) displayWidth else height).coerceAtLeast(2)
        val (sw, sh) = when {
            videoResolution > 0 && minOf(w, h) > videoResolution -> {
                val scale = videoResolution.toDouble() / minOf(w, h)
                if (w > h) Math.round(w * scale).toInt() to videoResolution else videoResolution to Math.round(h * scale).toInt()
            }
            // Low: same rule as the FFmpeg filter, long side 854 (landscape: width, otherwise height)
            videoResolution <= 0 && qualityPreset == 2 -> {
                if (w > h) 854 to Math.round(h * 854.0 / w).toInt() else Math.round(w * 854.0 / h).toInt() to 854
            }
            else -> w to h
        }
        return (sw / 2 * 2).coerceAtLeast(2) to (sh / 2 * 2).coerceAtLeast(2)
    }

    /** "32:27" -> 1.185. Square, unknown ("0:1", "N/A") or absurd ratios count as 1. */
    private fun sampleAspect(sar: String?): Double {
        val parts = sar?.split(':') ?: return 1.0
        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return 1.0
        val den = parts.getOrNull(1)?.toDoubleOrNull() ?: return 1.0
        if (num <= 0 || den <= 0) return 1.0
        return (num / den).takeIf { it in 0.25..4.0 } ?: 1.0
    }

    /** Parses ffprobe's "30000/1001" style rate. Falls back to 30 fps for "0/0", VFR oddities or junk. */
    fun frameRate(rate: String?, overrideFps: Int): Pair<Int, Int> {
        if (overrideFps > 0) return overrideFps to 1
        val parts = rate?.split('/')
        val num = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val den = parts?.getOrNull(1)?.toIntOrNull() ?: 1
        val fps = if (den > 0) num.toDouble() / den else 0.0
        return if (fps in 1.0..240.0) num to den else 30 to 1
    }
}
