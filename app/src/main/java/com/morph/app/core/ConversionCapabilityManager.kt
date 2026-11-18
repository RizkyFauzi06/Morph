package com.morph.app.core

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

object ConversionCapabilityManager {

    fun isLosslessCompatible(sourceExt: String, targetExt: String, mimeType: String? = null): Boolean {
        val src = sourceExt.trim().removePrefix(".").lowercase()
        val tgt = targetExt.trim().removePrefix(".").lowercase()

        val videoContainers = setOf("mp4", "mkv", "mov", "m4v", "ts")
        val isVideo = FileCategoryManager.isVideo(src) || (mimeType?.startsWith("video/") == true)
        if (isVideo && src in videoContainers && tgt in videoContainers && src != tgt) {
            return true
        }

        // M4A to M4A re-container
        if (src == "m4a" && tgt == "m4a") return true

        // Audio-only stream copy candidates. Shown as "Instant" in the UI; the engine still probes
        // the real codec (isStreamCopyCodecCompatible) and re-encodes when it doesn't match.
        val audioCopySources = streamCopyAudioSources[tgt]
        if (audioCopySources != null && src != tgt && src in audioCopySources) return true

        return false
    }

    /** Containers whose audio stream is commonly already in the target codec. */
    private val streamCopyAudioSources: Map<String, Set<String>> = mapOf(
        "m4a" to setOf("mp4", "mov", "mkv", "m4v", "ts", "aac"),
        "aac" to setOf("mp4", "mov", "mkv", "m4v", "ts", "m4a"),
        "mp3" to setOf("mkv", "avi", "mp4", "mov"),
        "opus" to setOf("webm", "mkv", "ogg"),
        "ogg" to setOf("webm", "mkv", "opus"),
        "flac" to setOf("mkv")
    )

    private val audioOnlyTargets = setOf("m4a", "aac", "mp3", "opus", "ogg", "flac", "wav")

    /**
     * Matroska/WebM have no rotation field Android players read, so copying a rotated phone video
     * (e.g. portrait, rotate=90) into them plays sideways. MP4/MOV keep the rotation matrix.
     */
    fun rotationBlocksStreamCopy(rotationDegrees: Int, targetExt: String): Boolean =
        rotationDegrees % 360 != 0 && targetExt.trim().removePrefix(".").lowercase() in setOf("mkv", "webm")

    fun isAudioOnlyTarget(targetExt: String): Boolean =
        targetExt.trim().removePrefix(".").lowercase() in audioOnlyTargets

    /**
     * Whether a re-encode into [targetExt] may keep the source audio as-is (-c:a copy).
     * Audio targets: the codecs Instant accepts. Video files: only codecs the container holds AND
     * every Android phone decodes, so a copy never swaps a playable AAC track for a silent DTS/AC3 one.
     */
    fun canKeepAudioCodec(targetExt: String, codec: String): Boolean {
        val tgt = targetExt.trim().removePrefix(".").lowercase()
        val c = codec.lowercase()
        // ALAC in M4A and FLAC in Ogg don't play on every Android version: only FLAC -> FLAC keeps them
        if (tgt in audioOnlyTargets) return c in audioCopyCodecs(tgt) && (c !in setOf("alac", "flac") || tgt == "flac")
        return c in when (tgt) {
            "mp4", "mov" -> setOf("aac", "mp3")
            "mkv" -> setOf("aac", "mp3", "opus", "vorbis")
            "webm" -> setOf("opus", "vorbis")
            else -> emptySet()
        }
    }

    /** Image sources whose groups can be merged into one PDF across groups. */
    private val pdfMergeImageSources = setOf("png", "jpg", "jpeg", "webp")

    /**
     * Groups that may be combined into a single PDF when the user picked images of several
     * extensions (e.g. WhatsApp's .jpg + .jpeg). Returns them only when 2+ such groups target PDF;
     * otherwise per-group merging is enough. Keys are the lowercase source extensions.
     */
    fun pdfCrossMergeGroups(targetPerGroup: Map<String, String>): List<String> {
        val candidates = targetPerGroup
            .filter { (src, target) -> src.lowercase() in pdfMergeImageSources && target.equals("pdf", ignoreCase = true) }
            .keys.toList()
        return if (candidates.size >= 2) candidates else emptyList()
    }

    /** Audio codecs (ffprobe codec_name) each audio-only target can hold without re-encoding. */
    private fun audioCopyCodecs(targetExt: String): Set<String> = when (targetExt) {
        "m4a" -> setOf("aac", "alac")
        "aac" -> setOf("aac")
        "mp3" -> setOf("mp3")
        "opus" -> setOf("opus")
        "ogg" -> setOf("vorbis", "opus", "flac")
        "flac" -> setOf("flac")
        "wav" -> setOf("pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_u8", "pcm_f32le", "pcm_f64le")
        else -> emptySet()
    }

    /**
     * BUG-03 codec guard for the Instant preset (`-c copy`).
     * Returns true only when every relevant source stream can be copied into [targetExt] as-is.
     * Unknown/missing probe data returns false so the caller falls back to re-encoding.
     *
     * @param videoCodecs ffprobe codec_name of each real video stream (cover art excluded)
     * @param audioCodecs ffprobe codec_name of each audio stream
     */
    fun isStreamCopyCodecCompatible(targetExt: String, videoCodecs: List<String>, audioCodecs: List<String>): Boolean {
        val tgt = targetExt.trim().removePrefix(".").lowercase()
        val video = videoCodecs.map { it.lowercase() }
        val audio = audioCodecs.map { it.lowercase() }

        if (tgt in audioOnlyTargets) {
            // Video is dropped with -vn; the audio stream must already match the target container
            val allowed = audioCopyCodecs(tgt)
            return audio.isNotEmpty() && audio.all { it in allowed }
        }

        val (allowedVideo, allowedAudio) = when (tgt) {
            // first = allowed video codecs, second = allowed audio codecs (null = any)
            "mp4", "mov", "m4v" -> setOf("h264", "hevc", "mpeg4") to setOf("aac", "mp3", "alac")
            "webm" -> setOf("vp8", "vp9", "av1") to setOf("opus", "vorbis")
            "mkv" -> Pair<Set<String>?, Set<String>?>(null, null) // Matroska holds virtually any codec
            else -> return false
        }

        if (video.isEmpty() || video.any { it.isBlank() || it == "none" }) return false
        if (allowedVideo != null && !video.all { it in allowedVideo }) return false
        if (allowedAudio != null && !audio.all { it in allowedAudio }) return false
        return true
    }

    fun getTargetFormats(fileName: String, mimeType: String? = null): List<String> {
        val originalExt = fileName.substringAfterLast('.', "").trim().lowercase()
        val category = FileCategoryManager.getCategory(originalExt, mimeType)

        val targets = mutableListOf<String>()

        when {
            originalExt == "svg" -> {
                targets.addAll(listOf("png", "jpg", "webp"))
            }
            originalExt == "epub" -> {
                targets.addAll(listOf("txt"))
            }
            originalExt == "pdf" || mimeType == "application/pdf" -> {
                targets.addAll(listOf("pdf", "jpg", "png"))
            }
            category == FileCategory.VIDEO -> {
                targets.addAll(listOf("mp4", "mkv", "mov", "webm", "gif", "avi", "mp3", "m4a", "wav", "flac", "ogg", "opus"))
            }
            category == FileCategory.AUDIO -> {
                targets.addAll(listOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus"))
            }
            // Android has no TIFF decoder, so every TIFF conversion failed: offer none
            category == FileCategory.IMAGE && originalExt !in setOf("tiff", "tif") -> {
                targets.addAll(listOf("png", "jpg", "webp", "bmp", "pdf"))
            }
            category == FileCategory.DOCUMENT -> {
                // Only TXT -> EPUB has an engine route; DOC/DOCX/ODT -> PDF/TXT were listed
                // before but always fell through to FFmpeg and failed, so they are not offered.
                if (originalExt == "txt") {
                    targets.addAll(listOf("epub"))
                }
            }
            // Unknown types (ZIP, XLSX, code...) have no engine route; offering MP4/PNG/MP3/PDF
            // here only produced conversions that always failed in FFmpeg.
            else -> {}
        }

        // Allow compression in same format where the engine can write it. FLV/WMV/3GP/M4V/TS/OGV
        // have no FFmpeg recipe ("-vn" fallback, always failed); ".m4v" is even a raw MPEG-4 muxer.
        val isCompressible = (category == FileCategory.VIDEO && originalExt in setOf("mp4", "mkv", "mov", "webm", "avi")) ||
                (category == FileCategory.IMAGE && originalExt in setOf("jpg", "jpeg", "png", "webp")) ||
                originalExt == "pdf"

        if (isCompressible) {
            if (originalExt.isNotEmpty() && !targets.contains(originalExt)) {
                targets.add(0, originalExt)
            }
        } else {
            targets.remove(originalExt)
        }

        return targets.distinct()
    }
}
