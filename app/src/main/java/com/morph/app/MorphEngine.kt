package com.morph.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.morph.app.core.*
import com.morph.app.ui.components.formatFileSize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

object MorphEngine {

    private const val TAG = "MorphEngine"

    // Encoder names. Hardware ones are FFmpeg's MediaCodec wrappers; X264_NATIVE is x264 (NEON)
    // through MorphNative and never appears on an FFmpeg command line.
    private const val HW_H264 = "h264_mediacodec"
    private const val HW_VP9 = "vp9_mediacodec"
    internal const val X264_NATIVE = "x264-native"
    private const val SW_H264 = "libopenh264"
    private const val SW_VP9 = "libvpx-vp9"
    /** Stand-in for targets with a single fixed encoder (audio, GIF, AVI) and for Instant copy. */
    internal const val DEFAULT_ENCODER = "default"
    private val H264_TARGETS = setOf("mp4", "mkv", "mov")

    internal fun isHardwareEncoder(encoder: String) = encoder.endsWith("_mediacodec")

    /**
     * Encoders to try, fastest first. ffmpeg-kit-full is LGPL (no libx264), so software H.264 is
     * native x264 when libmorph-native is present, else libopenh264. A hardware encoder whose
     * firmware already proved it caps the bitrate below what this conversion needs is skipped.
     */
    internal fun videoEncoderOrder(
        targetExt: String,
        targetVideoBps: Long,
        hwCapBps: (String) -> Long?,
        nativeX264: Boolean
    ): List<String> {
        val chain = when (targetExt) {
            in H264_TARGETS -> listOfNotNull(HW_H264, X264_NATIVE.takeIf { nativeX264 }, SW_H264)
            "webm" -> listOf(HW_VP9, SW_VP9)
            else -> return listOf(DEFAULT_ENCODER)
        }
        return chain.filterNot { isHardwareEncoder(it) && hardwareTooSlowFor(hwCapBps(it), targetVideoBps) }
    }

    /** A recorded cap below the underflow line means this hardware run would be thrown away anyway. */
    internal fun hardwareTooSlowFor(capBps: Long?, targetVideoBps: Long): Boolean =
        capBps != null && capBps < targetVideoBps * UNDERFLOW_RATIO

    private const val BT709_TAGS = "-colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv"

    /**
     * Encoder-only flags. MediaCodec: CBR so drivers that honour it hit -b:v, -g for a ~2 s GOP
     * (FFmpeg's default is 1 frame). libvpx-vp9: realtime speed 6-8 (speed 4 ran at 3.7 fps on a
     * Redmi 15; >= 5 switches libvpx to its fast non-RD mode search), row-mt + 4 tile columns for
     * all cores, no lookahead. BT.709 tags so nothing is labelled "gbr" (RGB).
     */
    internal fun videoCodecArgs(encoder: String, preset: Int): String = when (encoder) {
        HW_H264, HW_VP9 -> "-c:v $encoder -bitrate_mode cbr -g 60 $BT709_TAGS"
        SW_VP9 -> "-c:v $SW_VP9 -deadline realtime -cpu-used ${vp9Speed(preset)} -row-mt 1 -tile-columns 2 -lag-in-frames 0 $BT709_TAGS"
        else -> "-c:v $encoder $BT709_TAGS"
    }

    internal fun vp9Speed(preset: Int): Int = when (preset) {
        1 -> 7
        2 -> 8
        else -> 6
    }

    /** Hardware encoders take NV12 (YUV420SemiPlanar); many Qualcomm chips reject planar yuv420p. */
    internal fun encoderPixelFormat(encoder: String): String = if (isHardwareEncoder(encoder)) "nv12" else "yuv420p"

    /** Video bitrate per quality preset (High = the source-derived base). */
    internal fun videoBitrateForPreset(preset: Int, baseBps: Long): Long = when (preset) {
        1 -> (baseBps * 0.6).toLong()
        2 -> (baseBps * 0.35).toLong()
        else -> baseBps
    }

    internal fun audioKbpsFor(targetExt: String, preset: Int): Int = when {
        targetExt == "webm" -> when (preset) { 1 -> 128; 2 -> 96; else -> 192 }
        else -> when (preset) { 1 -> 160; 2 -> 96; else -> 256 }
    }

    /** Media time after which a running hardware encode is checked for an ignored bitrate. */
    private const val EARLY_BITRATE_CHECK_MS = 5_000L

    /** Output under this share of the target size = the driver ignored -b:v (Redmi 15: ~13%). */
    private const val UNDERFLOW_RATIO = 0.35

    /** Software output at least twice as dense as hardware => the driver, not the content, was the cause. */
    internal fun confirmsHwIgnoredBitrate(hwBytesPerMs: Double, swBytesPerMs: Double): Boolean =
        hwBytesPerMs > 0 && swBytesPerMs >= hwBytesPerMs * 2

    internal fun isBitrateUnderflow(outputBytes: Long, targetBitsPerSec: Long, durationMs: Long): Boolean {
        if (durationMs <= 0 || targetBitsPerSec <= 0) return false
        val expectedBytes = targetBitsPerSec / 8.0 * (durationMs / 1000.0)
        return outputBytes < expectedBytes * UNDERFLOW_RATIO
    }

    /** HFX-4A: longest rendered PDF page edge (~A4 at 300 DPI) to stay clear of OutOfMemoryError. */
    private const val MAX_PDF_RENDER_EDGE_PX = 2480f

    sealed class ConvertState {
        object Idle : ConvertState()
        data class Converting(val format: String, val progress: Int) : ConvertState()
        object Success : ConvertState()
        data class Error(val message: String, val originalUri: Uri? = null, val targetFormat: String = "") : ConvertState()
    }

    val currentStatus = MutableStateFlow<ConvertState>(ConvertState.Idle)

    private var activeJob: Job? = null

    suspend fun executeConversion(
        context: Context,
        inputUri: Uri,
        outputExtension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        videoResolution: Int = 0, // PBI 5.2: target short side (1080/720/480), 0 = original
        videoFps: Int = 0, // PBI 5.2: 60/30/24, 0 = original
        onResultDetail: (String) -> Unit = {} // HFX-5: e.g. PDF size comparison
    ): String = withContext(Dispatchers.IO) {
        val inputExt = originalFileName.substringAfterLast('.', "").trim().lowercase()
        val targetExt = outputExtension.trim().removePrefix(".").lowercase()
        activeJob = coroutineContext[Job]

        val resultDeferred = CompletableDeferred<String>()

        try {
            when {
                // Route 1: PDF Document Input
                inputExt == "pdf" && targetExt in listOf("jpg", "jpeg", "png", "webp") -> {
                    convertPdfToImages(context, inputUri, targetExt, originalFileName, qualityPreset, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }
                inputExt == "pdf" && targetExt == "pdf" -> {
                    compressPdf(context, inputUri, originalFileName, qualityPreset, onProgress, onResultDetail,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 2: Image to PDF
                targetExt == "pdf" && inputExt in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") -> {
                    resultDeferred.complete(
                        mergeImagesToPdf(context, listOf(inputUri), originalFileName, onProgress = onProgress)
                    )
                }

                // Route 3: Vector SVG to Bitmap Image
                inputExt == "svg" && targetExt in listOf("png", "jpg", "jpeg", "webp") -> {
                    convertSvgToImage(context, inputUri, targetExt, originalFileName, qualityPreset, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 4: E-Book EPUB to TXT
                inputExt == "epub" && targetExt == "txt" -> {
                    convertEpubToTxt(context, inputUri, originalFileName, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 4b: Plain text TXT to EPUB (PBI 5.4)
                inputExt == "txt" && targetExt == "epub" -> {
                    convertTxtToEpub(context, inputUri, originalFileName, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 5: Native Raster Image Converter
                inputExt in listOf("png", "jpg", "jpeg", "webp", "bmp") &&
                    targetExt in listOf("png", "jpg", "jpeg", "webp", "bmp") -> {
                    convertImageNatively(context, inputUri, targetExt, originalFileName, qualityPreset, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 6: Multimedia (Video & Audio) via FFmpeg Engine
                else -> {
                    convertMediaWithFFmpeg(context, inputUri, inputExt, targetExt, originalFileName, qualityPreset, videoResolution, videoFps, onProgress, onResultDetail,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }
            }

            resultDeferred.await()
        } finally {
            if (activeJob == coroutineContext[Job]) {
                activeJob = null
            }
            withContext(NonCancellable) {
                currentStatus.value = ConvertState.Idle
            }
        }
    }

    // --- 1. MEDIA CONVERTER (FFMPEG) ---
    private suspend fun convertMediaWithFFmpeg(
        context: Context,
        inputUri: Uri,
        inputExt: String,
        targetExt: String,
        originalFileName: String,
        qualityPreset: Int,
        videoResolution: Int,
        videoFps: Int,
        onProgress: (String, Int) -> Unit,
        onResultDetail: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempInput = OutputManager.createTempFile(context, "in_media", inputExt)
        val tempOutput = OutputManager.createTempFile(context, "out_media", targetExt)

        try {
            updateProgress(targetExt, strings.enginePreparingMedia, 5, onProgress)

            // Read the source straight from its content:// URI ("saf:" protocol) so big videos
            // don't need a full copy in cache (2x storage). Each saf: parameter is single-use.
            var useSaf = true
            fun inputArg(): String =
                if (useSaf) FFmpegKitConfig.getSafParameterForRead(context, inputUri) else tempInput.absolutePath

            var inputSize = getFileSizeFromUri(context, inputUri)
            // Clean cache copy of the source: used when saf: can't be probed or a saf: run fails
            fun copyInputToCache() {
                useSaf = false
                tempInput.delete()
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(tempInput).use { output -> input.copyTo(output) }
                } ?: throw IOException(strings.engineErrReadInput)
                if (!tempInput.exists() || tempInput.length() <= 0) {
                    throw IOException(strings.engineErrInputEmpty)
                }
                inputSize = tempInput.length()
            }

            var mediaInfo = try {
                FFprobeKit.getMediaInformation(inputArg()).mediaInformation
            } catch (e: Exception) {
                Log.w(TAG, "SAF probe failed, falling back to cache copy", e)
                null
            }

            if (mediaInfo == null || mediaInfo.streams.isNullOrEmpty()) {
                // Non-seekable providers (streams, some cloud apps), or a saf: probe that saw no
                // streams: copy to cache so codecs are read reliably (Instant depends on them)
                copyInputToCache()
                mediaInfo = FFprobeKit.getMediaInformation(inputArg()).mediaInformation
            }

            // Audio output from a video with no audio stream would be an empty/broken file
            if (ConversionCapabilityManager.isAudioOnlyTarget(targetExt) &&
                mediaInfo?.streams?.none { it.type.equals("audio", ignoreCase = true) } == true
            ) {
                throw IOException(strings.engineErrNoAudioTrack)
            }

            val totalDurationStr = mediaInfo?.duration
            val totalDurationMs = (totalDurationStr?.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L

            val originalBitrateBps = if (totalDurationMs > 0 && inputSize > 0) {
                ((inputSize * 8.0) / (totalDurationMs / 1000.0)).toLong()
            } else {
                4000000L
            }
            // PBI 5.2: fewer pixels need proportionally fewer bits, or 480p stays as big as 1080p
            val sourceShortSide = mediaInfo?.streams
                ?.firstOrNull { it.type.equals("video", ignoreCase = true) && !isAttachedPicture(it) }
                ?.let { minOf(it.width ?: 0L, it.height ?: 0L) } ?: 0L
            val pixelRatio = if (videoResolution > 0 && sourceShortSide > videoResolution) {
                (videoResolution.toDouble() / sourceShortSide).let { it * it }
            } else 1.0
            val videoBitrateBps = (originalBitrateBps * 0.9 * pixelRatio).toLong().coerceIn(400000L, 20000000L)

            // Video rotation check
            val videoRotation = getVideoRotation(mediaInfo)

            // Lossless / Stream Copy validation (extension-level rule)
            val isLosslessSupported = ConversionCapabilityManager.isLosslessCompatible(inputExt, targetExt)
            // Crucial: If video has rotation metadata and target is MKV or WebM, stream copy causes unrotated orientation on Android
            // PBI 5.2: a resolution/FPS change needs re-encoding, so Instant (-c copy) is off
            val wantsVideoChange = videoResolution > 0 || videoFps > 0
            val rotationBlocksCopy = ConversionCapabilityManager.rotationBlocksStreamCopy(videoRotation, targetExt)
            val codecsFit = qualityPreset != 3 || isStreamCopyCodecSafe(mediaInfo, targetExt)
            val isLosslessCapable = !wantsVideoChange && isLosslessSupported && !rotationBlocksCopy &&
                // BUG-03: the probed source codecs must really fit the target container
                codecsFit

            // Told to the user on success, so "Instant" never silently becomes a full re-encode
            var instantNote: String? = null
            val effectivePreset = if (qualityPreset == 3 && !isLosslessCapable) {
                Log.i(TAG, "Instant not used for $inputExt -> $targetExt: rotation=$videoRotation " +
                    "codecsFit=$codecsFit videoChange=$wantsVideoChange extOk=$isLosslessSupported")
                instantNote = when {
                    rotationBlocksCopy -> strings.instantSkippedRotation(targetExt.uppercase())
                    !codecsFit -> strings.instantSkippedCodec(targetExt.uppercase())
                    else -> strings.instantSkippedGeneric
                }
                0 // Fallback to High if format/codec doesn't support stream copying
            } else {
                qualityPreset
            }

            fun failed(r: Pair<ReturnCode?, String>) = !ReturnCode.isSuccess(r.first) && !ReturnCode.isCancel(r.first)

            // Software decoding only: FFmpeg's MediaCodec decoders lost the colour range on a Redmi 15
            // (dark output) and gave no measurable speed-up (0.93x -> 0.94x), so they were removed.
            val prefs = MorphPrefs(context)
            val firmware = Build.FINGERPRINT ?: ""
            val sourceVideo = mediaInfo?.streams
                ?.firstOrNull { it.type.equals("video", ignoreCase = true) && !isAttachedPicture(it) }
            val hasAudio = mediaInfo?.streams?.any { it.type.equals("audio", ignoreCase = true) } == true

            // Tries each encoder (hardware first) on the current input. Hardware failures are instant.
            suspend fun encodeWithFallback(preset: Int): Pair<ReturnCode?, String> {
                val targetVideoBps = videoBitrateForPreset(preset, videoBitrateBps)
                val targetBps = targetVideoBps + audioKbpsFor(targetExt, preset) * 1000L
                val encoders = if (preset == 3) {
                    listOf(DEFAULT_ENCODER)
                } else {
                    videoEncoderOrder(targetExt, targetVideoBps, { prefs.hwEncoderCapBps(it, firmware) }, MorphNative.isAvailable)
                }
                var result: Pair<ReturnCode?, String> = null to ""
                // Output rate (bytes per ms) the hardware run had when it was judged too small
                var hwBytesPerMs: Double? = null
                var hwEncoder: String? = null
                for ((index, encoder) in encoders.withIndex()) {
                    if (index > 0) {
                        Log.w(TAG, "${encoders[index - 1]} not used (rc=${result.first?.value}), trying $encoder")
                        updateProgress(targetExt, strings.enginePreparingMedia, 5, onProgress)
                    }
                    // Vendor drivers may ignore -b:v even in CBR (Redmi 15: 79 MB -> 5.5 MB at Medium,
                    // ~1.5 Mbps whatever the target). Any preset is guarded when software can redo it.
                    val watchBitrate = isHardwareEncoder(encoder) && index < encoders.lastIndex
                    // Set from FFmpegKit's statistics thread, read here after the session ends
                    val underflow = java.util.concurrent.atomic.AtomicBoolean(false)
                    val rateAtAbort = java.util.concurrent.atomic.AtomicReference<Double>(null)
                    tempOutput.delete()
                    result = if (encoder == X264_NATIVE) {
                        runNativeX264(
                            context, ::inputArg, tempOutput, targetExt, preset, targetVideoBps, sourceVideo,
                            videoRotation, videoResolution, videoFps, hasAudio, totalDurationMs, onProgress
                        )
                    } else {
                        runFFmpeg(
                            buildFFmpegCommand(inputArg(), tempOutput, inputExt, targetExt, preset, videoBitrateBps, videoRotation, videoResolution, videoFps, encoder),
                            targetExt, totalDurationMs, onProgress,
                            // Decide after 5 s of video instead of wasting a whole hardware encode
                            abortWhen = if (watchBitrate) { bytes, timeMs ->
                                (timeMs >= EARLY_BITRATE_CHECK_MS && isBitrateUnderflow(bytes, targetBps, timeMs))
                                    .also { if (it) rateAtAbort.set(bytes.toDouble() / timeMs) }
                            } else null,
                            onAbort = { underflow.set(true) }
                        )
                    }
                    // Clips shorter than the early check are judged on the finished file
                    if (watchBitrate && !underflow.get() && !failed(result) && !ReturnCode.isCancel(result.first)) {
                        underflow.set(isBitrateUnderflow(tempOutput.length(), targetBps, totalDurationMs))
                        if (underflow.get()) rateAtAbort.set(tempOutput.length().toDouble() / totalDurationMs)
                    }
                    if (underflow.get()) {
                        Log.w(TAG, "$encoder output far below target, redoing in software")
                        hwBytesPerMs = rateAtAbort.get()
                        hwEncoder = encoder
                        continue
                    }
                    if (!failed(result)) {
                        // Remember the cap only when software proves the content needed the bits.
                        // A static screen recording is small in software too and must not flag the device.
                        val hwRate = hwBytesPerMs
                        val hwName = hwEncoder
                        if (hwRate != null && hwName != null && !ReturnCode.isCancel(result.first) && totalDurationMs > 0 &&
                            confirmsHwIgnoredBitrate(hwRate, tempOutput.length().toDouble() / totalDurationMs)
                        ) {
                            val capBps = (hwRate * 8000).toLong()
                            Log.w(TAG, "$hwName caps the bitrate near $capBps bps on this firmware; skipping it when a target needs more")
                            prefs.setHwEncoderCapBps(hwName, firmware, capBps)
                        }
                        if (!ReturnCode.isCancel(result.first)) Log.i(TAG, "Video done: encode=$encoder")
                        break
                    }
                }
                return result
            }

            var usedPreset = effectivePreset
            var ffmpegResult = encodeWithFallback(usedPreset)

            // Safety net: if stream copy still fails (e.g. odd timestamps), re-encode instead of failing
            if (usedPreset == 3 && failed(ffmpegResult)) {
                Log.w(TAG, "Stream copy failed (${ffmpegResult.first?.value}), retrying with re-encode")
                instantNote = strings.instantSkippedGeneric
                usedPreset = 0
                updateProgress(targetExt, strings.enginePreparingMedia, 5, onProgress)
                ffmpegResult = encodeWithFallback(usedPreset)
            }

            // Last resort: some providers' saf: fds misbehave for certain demuxers/muxers. Copy once
            // (useSaf turns false, so this can't repeat) and reuse that copy for every encoder.
            if (useSaf && failed(ffmpegResult)) {
                Log.w(TAG, "FFmpeg failed on saf: input (${ffmpegResult.first?.value}), retrying from cache copy")
                copyInputToCache()
                updateProgress(targetExt, strings.enginePreparingMedia, 5, onProgress)
                ffmpegResult = encodeWithFallback(usedPreset)
            }

            val returnCode = ffmpegResult.first

            if (ReturnCode.isSuccess(returnCode)) {
                updateProgress(targetExt, strings.engineFinalizingValidating, 98, onProgress)

                // Validate output file
                when (val valResult = ConversionValidator.validateFile(tempOutput, targetExt)) {
                    is ConversionValidator.ValidationResult.Invalid -> {
                        OutputManager.cleanTempFiles(tempInput, tempOutput)
                        withContext(Dispatchers.Main) {
                            val errMsg = strings.engineErrInvalidOutput(valResult.reason)
                            currentStatus.value = ConvertState.Error(errMsg, inputUri, targetExt)
                            onError(errMsg)
                        }
                    }
                    is ConversionValidator.ValidationResult.Valid -> {
                        val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, targetExt)
                        OutputManager.cleanTempFiles(tempInput)
                        withContext(Dispatchers.Main) {
                            if (finalPath != null) {
                                currentStatus.value = ConvertState.Success
                                onProgress(strings.engineFinished100, 100)
                                instantNote?.let(onResultDetail)
                                onSuccess(finalPath)
                            } else {
                                currentStatus.value = ConvertState.Error(strings.engineErrSaveOutput, inputUri, targetExt)
                                onError(strings.engineErrSaveOutput)
                            }
                        }
                    }
                }
            } else if (ReturnCode.isCancel(returnCode)) {
                OutputManager.cleanTempFiles(tempInput, tempOutput)
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Idle
                    onError(strings.engineCancelled)
                }
            } else {
                val failLogs = ffmpegResult.second
                OutputManager.cleanTempFiles(tempInput, tempOutput)
                val errorMessage = when {
                    usedPreset == 3 ->
                        strings.engineErrStreamCopyIncompatible
                    failLogs.contains("does not support", ignoreCase = true) ->
                        strings.engineErrFormatNotSupported
                    else ->
                        strings.engineErrFFmpeg(returnCode?.value ?: -1)
                }
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error(errorMessage, inputUri, targetExt)
                    onError(errorMessage)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempInput, tempOutput)
            throw e
        }
    }

    /**
     * Video filter chain for re-encoding: optional downscale (PBI 5.2 resolution, or the Low
     * preset's 854px cap), then force even dimensions so H.264/yuv420p never fails on odd sizes
     * (e.g. 1081x1921 phone recordings). FFmpeg autorotates first, so iw/ih are display-oriented.
     */
    internal fun buildVideoFilterArgs(preset: Int, videoResolution: Int, videoFps: Int, outPixFmt: String? = null): String {
        val filters = mutableListOf<String>()
        when {
            // Scale the short side to the target, never upscale, keep aspect ratio
            videoResolution > 0 -> filters += "scale='if(gt(iw,ih),-2,min($videoResolution,iw))':'if(gt(iw,ih),min($videoResolution,ih),-2)'"
            preset == 2 -> filters += "scale='if(gt(iw,ih),854,-2)':'if(gt(iw,ih),-2,854)'"
        }
        // With an explicit output format, convert once here (BT.709 matrix) so swscale doesn't detour via RGB
        filters += if (outPixFmt != null) {
            "scale='trunc(iw/2)*2':'trunc(ih/2)*2':out_color_matrix=bt709:out_range=tv,format=$outPixFmt"
        } else {
            "scale='trunc(iw/2)*2':'trunc(ih/2)*2'"
        }
        val fpsArg = if (videoFps > 0) "-r $videoFps " else ""
        return "-vf \"${filters.joinToString(",")}\" $fpsArg"
    }

    /** "2.4 MB → 850 KB (-65%)"; a bigger result shows "(+12%)". */
    internal fun formatSizeChange(beforeBytes: Long, afterBytes: Long): String {
        val pct = if (beforeBytes > 0) Math.round((afterBytes - beforeBytes) * 100.0 / beforeBytes) else 0L
        val sign = if (pct > 0) "+" else ""
        return "${formatFileSize(beforeBytes)} → ${formatFileSize(afterBytes)} ($sign$pct%)"
    }

    internal fun buildFFmpegCommand(
        inputArg: String,
        tempOutput: File,
        inputExt: String,
        targetExt: String,
        preset: Int,
        videoBitrateBps: Long,
        videoRotation: Int,
        videoResolution: Int,
        videoFps: Int,
        videoEncoder: String
    ): String {
        val qualityArgs = buildFFmpegQualityArgs(inputExt, targetExt, preset, videoBitrateBps, videoRotation, videoResolution, videoFps, videoEncoder)
        return "-y -i \"$inputArg\" $qualityArgs \"${tempOutput.absolutePath}\""
    }

    /**
     * Software H.264 through native x264 (NEON): FFmpeg decodes + scales the source into a named
     * pipe as raw I420 at a constant frame rate, x264 encodes that to an Annex-B file, then FFmpeg
     * muxes it with the source audio. Same (ReturnCode, logs) shape as runFFmpeg.
     */
    private suspend fun runNativeX264(
        context: Context,
        inputArg: () -> String,
        tempOutput: File,
        targetExt: String,
        preset: Int,
        targetVideoBps: Long,
        source: com.arthenica.ffmpegkit.StreamInformation?,
        rotation: Int,
        videoResolution: Int,
        videoFps: Int,
        hasAudio: Boolean,
        totalDurationMs: Long,
        onProgress: (String, Int) -> Unit
    ): Pair<ReturnCode?, String> {
        val width = source?.width?.toInt() ?: 0
        val height = source?.height?.toInt() ?: 0
        val streamIndex = source?.index
        if (width <= 0 || height <= 0 || streamIndex == null) return ReturnCode(1) to "x264: unknown source size"

        val (outW, outH) = MorphNative.outputSize(width, height, rotation, preset, videoResolution)
        val (fpsNum, fpsDen) = MorphNative.frameRate(source.averageFrameRate, videoFps)
        val kbps = (targetVideoBps / 1000).toInt().coerceAtLeast(100)
        val keyint = Math.round(fpsNum.toDouble() / fpsDen * 2).toInt().coerceAtLeast(1)

        // Never call FFmpegKitConfig.ignoreSignal(SIGPIPE) here: that flag makes FFmpegKit SKIP its own
        // signal(SIGPIPE, SIG_IGN) (verified in libffmpegkit.so term_init). nativeX264Open sets SIG_IGN.
        val pipe = FFmpegKitConfig.registerNewFFmpegPipe(context)
        val elementary = OutputManager.createTempFile(context, "x264_es", "h264")
        try {
            val handle = MorphNative.nativeX264Open(
                pipe, elementary.absolutePath, outW, outH, fpsNum, fpsDen, MorphNative.x264PresetFor(preset), kbps, keyint
            )
            if (handle <= 0) return ReturnCode(1) to "x264 open failed ($handle)"

            // shortcut: a user cancel in the first ~0.5 s, before FFmpeg opens the pipe, can leave that
            // FFmpeg thread blocked in open(); the conversion still cancels, only the thread leaks.
            val (decode, frames) = try {
                coroutineScope {
                    val encoded = async(Dispatchers.IO) { MorphNative.nativeX264Encode(handle) }
                    val decode = runFFmpeg(
                        "-y -i \"${inputArg()}\" -map 0:$streamIndex -an -sn -dn " +
                            "-vf \"scale=$outW:$outH:out_color_matrix=bt709:out_range=tv,format=yuv420p\" " +
                            "-fps_mode cfr -r $fpsNum/$fpsDen -f rawvideo -pix_fmt yuv420p \"$pipe\"",
                        targetExt, totalDurationMs, onProgress
                    )
                    // FFmpeg failed before or while writing: release the reader instead of waiting
                    if (!ReturnCode.isSuccess(decode.first)) MorphNative.cancel()
                    decode to encoded.await()
                }
            } finally {
                MorphNative.nativeX264Close(handle) // after the encode call returned (structured scope)
            }
            // Order matters: a failed decode also cancels the reader (ERR_CANCELLED), but that must
            // stay a failure so the next encoder (libopenh264) gets its turn, not a user cancel
            if (ReturnCode.isCancel(decode.first)) return ReturnCode(ReturnCode.CANCEL) to decode.second
            if (!ReturnCode.isSuccess(decode.first)) return decode
            if (frames == MorphNative.ERR_CANCELLED) return ReturnCode(ReturnCode.CANCEL) to decode.second
            if (frames <= 0) return ReturnCode(1) to "x264 encode failed ($frames)\n${decode.second}"

            // Constant-rate video from the pipe + original audio; x264 already wrote BT.709 VUI
            val audioIn = if (hasAudio) "-i \"${inputArg()}\" " else ""
            val audioOut = if (hasAudio) "-map 1:a:0 -c:a aac -b:a ${audioKbpsFor(targetExt, preset)}k " else ""
            val container = when (targetExt) {
                "mp4", "mov" -> "-movflags +faststart "
                else -> ""
            }
            return runFFmpeg(
                "-y -f h264 -framerate $fpsNum/$fpsDen -i \"${elementary.absolutePath}\" $audioIn" +
                    "-map 0:v:0 -c:v copy $audioOut$container\"${tempOutput.absolutePath}\"",
                // The mux is a quick copy: keep the bar where the decode left it instead of resetting it
                targetExt, totalDurationMs, onProgress = { _, _ -> }
            )
        } finally {
            FFmpegKitConfig.closeFFmpegPipe(pipe)
            elementary.delete()
        }
    }

    /** Runs one FFmpeg command and reports progress. Returns the return code and the full log. */
    private suspend fun runFFmpeg(
        command: String,
        targetExt: String,
        totalDurationMs: Long,
        onProgress: (String, Int) -> Unit,
        abortWhen: ((outputBytes: Long, mediaTimeMs: Long) -> Boolean)? = null,
        onAbort: () -> Unit = {}
    ): Pair<ReturnCode?, String> {
        val strings = AppStrings.get()
        val ffmpegSession = CompletableDeferred<ReturnCode?>()
        var aborted = false

        val session = FFmpegKit.executeAsync(
            command,
            { s -> ffmpegSession.complete(s.returnCode) },
            { log -> Log.d(TAG, "[FFmpeg] ${log.message}") },
            { stats ->
                if (!aborted && abortWhen != null && abortWhen(stats.size, stats.time.toLong())) {
                    aborted = true
                    onAbort()
                    FFmpegKit.cancel(stats.sessionId)
                }
                if (!ffmpegSession.isCompleted) {
                    if (totalDurationMs > 0) {
                        val percentage = ((stats.time.toFloat() / totalDurationMs) * 95).toInt().coerceIn(5, 95)
                        currentStatus.value = ConvertState.Converting(targetExt, percentage)
                        onProgress(strings.engineProcessedPercent(percentage), percentage)
                    } else {
                        val seconds = (stats.time / 1000).toLong()
                        currentStatus.value = ConvertState.Converting(targetExt, 50)
                        onProgress(strings.engineProcessedSeconds(seconds), 50)
                    }
                }
            }
        )

        val returnCode = ffmpegSession.await()
        return returnCode to (session.allLogsAsString ?: "")
    }

    /**
     * BUG-03: inspects the probed streams and asks ConversionCapabilityManager whether
     * `-c copy` into [targetExt] is safe. Missing probe data => not safe (re-encode).
     */
    private fun isStreamCopyCodecSafe(mediaInfo: com.arthenica.ffmpegkit.MediaInformation?, targetExt: String): Boolean {
        val streams = mediaInfo?.streams ?: return false
        if (streams.isEmpty()) return false

        val videoCodecs = streams
            .filter { it.type.equals("video", ignoreCase = true) && !isAttachedPicture(it) }
            .map { it.codec ?: "" }
        val audioCodecs = streams
            .filter { it.type.equals("audio", ignoreCase = true) }
            .map { it.codec ?: "" }

        val compatible = ConversionCapabilityManager.isStreamCopyCodecCompatible(targetExt, videoCodecs, audioCodecs)
        Log.i(TAG, "Stream copy guard -> $targetExt video=$videoCodecs audio=$audioCodecs compatible=$compatible")
        return compatible
    }

    /** Cover art (MP3/M4A/MKV attachments) shows up as a video stream with disposition.attached_pic=1. */
    private fun isAttachedPicture(stream: com.arthenica.ffmpegkit.StreamInformation): Boolean {
        return try {
            stream.allProperties?.optJSONObject("disposition")?.optInt("attached_pic", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun getVideoRotation(mediaInfo: com.arthenica.ffmpegkit.MediaInformation?): Int {
        if (mediaInfo == null) return 0
        val videoStream = mediaInfo.streams?.firstOrNull { it.type.equals("video", ignoreCase = true) } ?: return 0

        // 1. Check stream tags ("rotate")
        val tags = videoStream.tags
        if (tags != null) {
            val tagRotate = tags.optString("rotate", "").toIntOrNull()
                ?: if (tags.has("rotate")) tags.optInt("rotate", 0) else null
            if (tagRotate != null && tagRotate != 0) {
                return ((tagRotate % 360) + 360) % 360
            }
        }

        // 2. Check side_data_list in stream properties
        try {
            val allProps = videoStream.allProperties
            val sideDataList = allProps?.optJSONArray("side_data_list")
            if (sideDataList != null) {
                for (i in 0 until sideDataList.length()) {
                    val sideData = sideDataList.optJSONObject(i)
                    if (sideData != null && sideData.has("rotation")) {
                        val rot = sideData.optInt("rotation", 0)
                        if (rot != 0) {
                            return ((rot % 360) + 360) % 360
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca rotasi video dari side_data_list", e)
        }

        return 0
    }

    internal fun buildFFmpegQualityArgs(
        inputExt: String,
        targetExt: String,
        preset: Int,
        videoBitrateBps: Long,
        videoRotation: Int = 0,
        videoResolution: Int = 0,
        videoFps: Int = 0,
        videoEncoder: String = DEFAULT_ENCODER
    ): String {
        // Stream Copy (Lossless Remuxing)
        if (preset == 3) {
            return when (targetExt) {
                "mp4", "mov" -> "-c copy -sn -dn -movflags +faststart"
                "mkv", "webm" -> "-c copy -sn -dn"
                "m4a" -> "-vn -sn -dn -c:a copy -movflags +faststart"
                "aac", "mp3", "opus", "ogg", "flac", "wav" -> "-vn -sn -dn -c:a copy"
                else -> "-c copy"
            }
        }

        // Target: Animated GIF
        if (targetExt == "gif") {
            val fps = when (preset) {
                0 -> 15
                1 -> 12
                else -> 10
            }
            val maxDim = when (preset) {
                0 -> 480
                1 -> 360
                else -> 240
            }
            val rotateArg = if (videoRotation != 0) "-metadata:s:v rotate=0 " else ""
            val filterGraph = "fps=$fps,scale='if(gt(iw,ih),$maxDim,-2)':'if(gt(iw,ih),-2,$maxDim)':flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
            return "-an -sn -dn $rotateArg-vf \"$filterGraph\""
        }

        val rotateArg = if (videoRotation != 0) "-metadata:s:v rotate=0 " else ""
        val videoArgs = buildVideoFilterArgs(preset, videoResolution, videoFps)

        // Target: WebM (vp9_mediacodec, else libvpx-vp9)
        if (targetExt == "webm") {
            val encoder = if (videoEncoder == HW_VP9) HW_VP9 else SW_VP9
            val filters = buildVideoFilterArgs(preset, videoResolution, videoFps, encoderPixelFormat(encoder))
            val bv = videoBitrateForPreset(preset, videoBitrateBps)
            // yuv420p (format= in the filter) keeps VP9 at profile 0: 10-bit sources would become
            // profile 2, which many Android players render as a black screen
            return "$filters${videoCodecArgs(encoder, preset)} -b:v $bv -c:a libopus -b:a ${audioKbpsFor(targetExt, preset)}k $rotateArg"
        }

        // Target: Standard Video Containers (MP4, MKV, MOV, AVI)
        if (targetExt in listOf("mp4", "mkv", "mov", "avi")) {
            val fastStartArg = when (targetExt) {
                "mp4", "mov" -> "-movflags +faststart "
                // Matroska holds a whole cluster (up to 5 s / 5 MB) in memory before writing it, so the
                // live size check could see ~0 bytes and wrongly abort a healthy hardware encode.
                "mkv" -> "-cluster_time_limit 1000 "
                else -> ""
            }
            val bv = videoBitrateForPreset(preset, videoBitrateBps)
            val ba = "${audioKbpsFor(targetExt, preset)}k"
            // AVI keeps FFmpeg's default MPEG-4 Part 2 + MP3 encoders, which are already fast
            if (targetExt == "avi") {
                return "$fastStartArg$videoArgs-b:v $bv -b:a $ba $rotateArg"
            }
            // ffmpeg-kit-full is LGPL: no libx264 (asking for it failed every encode in v1.0.2).
            // Here: h264_mediacodec or libopenh264; native x264 runs outside this command.
            val encoder = if (videoEncoder == HW_H264) HW_H264 else SW_H264
            val filters = buildVideoFilterArgs(preset, videoResolution, videoFps, encoderPixelFormat(encoder))
            return "${videoCodecArgs(encoder, preset)} -c:a aac $fastStartArg$filters-b:v $bv -b:a $ba $rotateArg"
        }

        // Target: Audio Formats
        val audioCommon = "-vn -sn -dn"
        return when (targetExt) {
            "wav" -> "$audioCommon -c:a pcm_s16le"
            "flac" -> "$audioCommon -c:a flac"
            "mp3" -> {
                val b = when (preset) {
                    0 -> "320k"
                    1 -> "192k"
                    2 -> "128k"
                    else -> "256k"
                }
                "$audioCommon -c:a libmp3lame -b:a $b"
            }
            "aac", "m4a" -> {
                val b = when (preset) {
                    0 -> "256k"
                    1 -> "192k"
                    2 -> "128k"
                    else -> "192k"
                }
                val faststart = if (targetExt == "m4a") " -movflags +faststart" else ""
                "$audioCommon -c:a aac -b:a $b$faststart"
            }
            "opus" -> {
                val b = when (preset) {
                    0 -> "160k"
                    1 -> "128k"
                    2 -> "96k"
                    else -> "128k"
                }
                "$audioCommon -c:a libopus -b:a $b"
            }
            "ogg" -> {
                val b = when (preset) {
                    0 -> "256k"
                    1 -> "160k"
                    2 -> "112k"
                    else -> "160k"
                }
                "$audioCommon -c:a libvorbis -b:a $b"
            }
            else -> audioCommon
        }
    }

    // --- 2. PDF TO IMAGES ---
    private suspend fun convertPdfToImages(
        context: Context,
        inputUri: Uri,
        targetExtension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempPdf = OutputManager.createTempFile(context, "in_pdf", "pdf")
        val createdTempFiles = mutableListOf<File>()
        var openPfd: ParcelFileDescriptor? = null
        var openRenderer: PdfRenderer? = null

        try {
            updateProgress(targetExtension, strings.engineReadingPdf, 10, onProgress)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
            } ?: throw IOException(strings.engineErrPdfRead)

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            openPfd = pfd
            val renderer = PdfRenderer(pfd)
            openRenderer = renderer
            val pageCount = renderer.pageCount

            if (pageCount == 0) {
                throw IllegalStateException(strings.engineErrPdfNoPages)
            }

            val scale = when (qualityPreset) {
                0, 3 -> 2.0f // High / Lossless (144+ DPI)
                1    -> 1.5f // Medium
                2    -> 1.0f // Low (72 DPI)
                else -> 1.5f
            }

            val format = if (targetExtension == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val qualityVal = when (qualityPreset) {
                0, 3 -> 100
                1    -> 85
                2    -> 65
                else -> 85
            }

            val baseName = originalFileName.substringBeforeLast('.', "doc")
            var lastSavedPath: String? = null

            for (i in 0 until pageCount) {
                currentCoroutineContext().ensureActive()

                val percent = (((i + 1).toFloat() / pageCount) * 85).toInt() + 10
                updateProgress(targetExtension, strings.engineRenderingPage(i + 1, pageCount), percent, onProgress)

                val pageSuffix = if (pageCount > 1) "_hal_${i + 1}" else ""
                val pageFileName = "$baseName$pageSuffix.$targetExtension"
                val tempPageFile = OutputManager.createTempFile(context, "pdf_page_$i", targetExtension)
                createdTempFiles.add(tempPageFile)

                // One page in memory at a time; page and bitmap are always released
                var bitmap: Bitmap? = null
                try {
                    renderer.openPage(i).use { page ->
                        // HFX-4A: clamp the longest edge so huge pages (A0 posters) don't OOM low-RAM phones
                        var pageScale = scale
                        val longestEdge = maxOf(page.width, page.height) * pageScale
                        if (longestEdge > MAX_PDF_RENDER_EDGE_PX) {
                            pageScale *= MAX_PDF_RENDER_EDGE_PX / longestEdge
                        }
                        val width = (page.width * pageScale).toInt().coerceAtLeast(1)
                        val height = (page.height * pageScale).toInt().coerceAtLeast(1)

                        val pageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap = pageBitmap
                        Canvas(pageBitmap).drawColor(Color.WHITE) // PDF pages need opaque white background
                        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }

                    FileOutputStream(tempPageFile).use { fos ->
                        bitmap!!.compress(format, qualityVal, fos)
                    }
                } finally {
                    bitmap?.recycle()
                }

                val savedPath = OutputManager.finalizeOutput(context, tempPageFile, pageFileName, targetExtension, sourceName = originalFileName)
                if (savedPath != null) lastSavedPath = savedPath
            }

            closePdfRenderer(openRenderer, openPfd)
            openRenderer = null
            openPfd = null
            OutputManager.cleanTempFiles(tempPdf, *createdTempFiles.toTypedArray())

            withContext(Dispatchers.Main) {
                if (lastSavedPath != null) {
                    currentStatus.value = ConvertState.Success
                    val msg = if (pageCount > 1) strings.enginePagesExtractedSuccess(pageCount) else lastSavedPath
                    onSuccess(msg)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSavePdfImage, inputUri, targetExtension)
                    onError(strings.engineErrSavePdfImage)
                }
            }
        } catch (e: Exception) {
            // Release native renderer + fd even on cancellation / OOM-adjacent failures
            closePdfRenderer(openRenderer, openPfd)
            OutputManager.cleanTempFiles(tempPdf, *createdTempFiles.toTypedArray())
            throw e
        }
    }

    private fun closePdfRenderer(renderer: PdfRenderer?, pfd: ParcelFileDescriptor?) {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }

    // --- 3. PDF COMPRESSOR ---
    private suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onResultDetail: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempInput = OutputManager.createTempFile(context, "comp_in", "pdf")
        val tempOutput = OutputManager.createTempFile(context, "comp_out", "pdf")
        var openPfd: ParcelFileDescriptor? = null
        var openRenderer: PdfRenderer? = null

        try {
            updateProgress("pdf", strings.engineAnalyzingPdf, 10, onProgress)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            } ?: throw IOException(strings.engineErrPdfRead)

            val pfd = ParcelFileDescriptor.open(tempInput, ParcelFileDescriptor.MODE_READ_ONLY)
            openPfd = pfd
            val renderer = PdfRenderer(pfd)
            openRenderer = renderer
            val pageCount = renderer.pageCount

            if (pageCount == 0) {
                throw IllegalStateException(strings.engineErrPdfEmpty)
            }

            // Longest rendered edge + JPEG quality. ~2200px on A4 is ~190 DPI: text stays sharp.
            val (maxEdgePx, jpegQuality) = when (qualityPreset) {
                1 -> 1800 to 65 // Medium
                2 -> 1400 to 50 // Low
                else -> 2200 to 80 // High (Instant does not apply to PDF)
            }

            FileOutputStream(tempOutput).use { fos ->
                JpegPdfWriter(fos.buffered()).use { writer ->
                    for (i in 0 until pageCount) {
                        currentCoroutineContext().ensureActive()

                        renderer.openPage(i).use { page ->
                            val scale = maxEdgePx.toFloat() / maxOf(page.width, page.height)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val jpeg = try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }.toByteArray()
                            } finally {
                                bitmap.recycle()
                            }
                            // Page keeps its original size in points; only the image inside is resampled
                            writer.addPage(jpeg, width, height, page.width.toFloat(), page.height.toFloat())
                        }

                        val percent = (((i + 1).toFloat() / pageCount) * 80).toInt() + 10
                        updateProgress("pdf", strings.engineCompressingPage(i + 1, pageCount), percent, onProgress)
                    }
                }
            }

            closePdfRenderer(openRenderer, openPfd)
            openRenderer = null
            openPfd = null

            // HFX-5: measure before finalizeOutput moves/deletes the temp files
            val beforeBytes = tempInput.length()
            val afterBytes = tempOutput.length()

            // Text-only or already-optimized PDFs grow when rasterized: keep the original, save nothing
            if (afterBytes >= beforeBytes) {
                OutputManager.cleanTempFiles(tempInput, tempOutput)
                val msg = strings.pdfAlreadyOptimal(formatFileSize(beforeBytes))
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error(msg, inputUri, "pdf")
                    onError(msg)
                }
                return
            }

            val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, "pdf")
            OutputManager.cleanTempFiles(tempInput, tempOutput)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onResultDetail(strings.pdfCompressionResult(formatSizeChange(beforeBytes, afterBytes)))
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSavePdfCompress, inputUri, "pdf")
                    onError(strings.engineErrSavePdfCompress)
                }
            }
        } catch (e: Exception) {
            // Release native renderer and fd on cancel/error
            closePdfRenderer(openRenderer, openPfd)
            OutputManager.cleanTempFiles(tempInput, tempOutput)
            throw e
        }
    }

    // --- 4. IMAGE(S) TO PDF ---
    suspend fun mergeImagesToPdf(
        context: Context,
        inputUris: List<Uri>,
        originalFileName: String,
        qualityPreset: Int = 0,
        onProgress: (String, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val strings = AppStrings.get()
        val tempOutput = OutputManager.createTempFile(context, "img_merge_pdf", "pdf")
        val pdfDocument = PdfDocument()
        activeJob = coroutineContext[Job] // so cancelCurrentConversion() can stop a merge too

        try {
            val total = inputUris.size
            for ((index, uri) in inputUris.withIndex()) {
                currentCoroutineContext().ensureActive()
                val percent = ((index.toFloat() / total) * 85).toInt() + 5
                updateProgress("pdf", strings.engineBuildingPdfPage(index + 1, total), percent, onProgress)

                val bitmap = decodeBitmap(context, uri)

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            updateProgress("pdf", strings.engineSavingPdfDoc, 95, onProgress)

            FileOutputStream(tempOutput).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, "pdf")
            OutputManager.cleanTempFiles(tempOutput)

            if (finalPath != null) {
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Success
                }
                finalPath
            } else {
                throw IOException(strings.engineErrSaveMergedPdf)
            }
        } catch (e: Exception) {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
            OutputManager.cleanTempFiles(tempOutput)
            withContext(Dispatchers.Main) {
                val errMsg = strings.engineErrMergePdf(e.message ?: "")
                currentStatus.value = ConvertState.Error(errMsg, inputUris.firstOrNull(), "pdf")
            }
            throw e
        }
    }

    // --- 5. NATIVE IMAGE CONVERTER ---
    private suspend fun convertImageNatively(
        context: Context,
        inputUri: Uri,
        extension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val targetExt = extension.trim().removePrefix(".").lowercase()
        val tempOutput = OutputManager.createTempFile(context, "img_out", targetExt)

        try {
            updateProgress(targetExt, strings.engineProcessingImage, 40, onProgress)

            val bitmap = decodeBitmap(context, inputUri)

            updateProgress(targetExt, strings.engineCompressingImage, 70, onProgress)

            // JPG and 24-bit BMP have no alpha: composite on white, or transparent pixels turn black
            val needsOpaque = targetExt in listOf("jpg", "jpeg", "bmp")
            val finalBitmap = if (needsOpaque && bitmap.hasAlpha()) {
                val opaqueBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(opaqueBitmap)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()
                opaqueBitmap
            } else {
                bitmap
            }

            if (targetExt == "bmp") {
                BmpWriter.writeBitmapAsBmp(finalBitmap, tempOutput)
            } else {
                val qualitySetting = when (qualityPreset) {
                    0, 3 -> 100 // High / Lossless
                    1    -> 85  // Medium
                    2    -> 65  // Low
                    else -> 90
                }
                FileOutputStream(tempOutput).use { fos ->
                    finalBitmap.compress(compressFormatFor(targetExt, qualitySetting), qualitySetting, fos)
                }
            }
            finalBitmap.recycle()

            val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, targetExt)
            OutputManager.cleanTempFiles(tempOutput)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSaveImage, inputUri, targetExt)
                    onError(strings.engineErrSaveImage)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempOutput)
            throw e
        }
    }

    // --- 6. SVG CONVERTER ---
    private suspend fun convertSvgToImage(
        context: Context,
        inputUri: Uri,
        targetExtension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        var tempSvgOut: File? = null
        try {
            tempSvgOut = SvgConverter.convertSvg(
                context = context,
                inputUri = inputUri,
                targetExtension = targetExtension,
                qualityPreset = qualityPreset,
                onProgress = { step, percent ->
                    updateProgress(targetExtension, step, percent, onProgress)
                }
            )

            val finalPath = OutputManager.finalizeOutput(context, tempSvgOut, originalFileName, targetExtension)
            OutputManager.cleanTempFiles(tempSvgOut)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                } else {
                    val errMsg = AppStrings.get().engineErrSaveSvg
                    currentStatus.value = ConvertState.Error(errMsg, inputUri, targetExtension)
                    onError(errMsg)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempSvgOut)
            throw e
        }
    }

    // --- 7. EPUB TO TXT ---
    // --- TXT TO EPUB (PBI 5.4) ---
    private suspend fun convertTxtToEpub(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempEpub = OutputManager.createTempFile(context, "out_epub", "epub")
        val job = currentCoroutineContext()[Job]

        try {
            updateProgress("epub", strings.engineReadingTxt, 10, onProgress)

            // shortcut: whole TXT is held in memory; fine for books (a few MB), stream it if 100MB+ logs matter
            val bytes = context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
                ?: throw IOException(strings.engineErrReadInput)
            val text = EpubBuilder.decodeText(bytes)
            if (text.isBlank()) throw IOException(strings.engineErrInputEmpty)

            job?.ensureActive()
            updateProgress("epub", strings.engineBuildingEpub, 30, onProgress)
            val chapters = EpubBuilder.splitChapters(text) { strings.epubChunkTitle(it) }
            if (chapters.isEmpty()) throw IOException(strings.engineErrInputEmpty)

            val title = originalFileName.substringBeforeLast('.', originalFileName)
            EpubBuilder.build(
                chapters = chapters,
                title = title,
                language = AppStrings.currentLanguage, // best guess for dc:language; text language is unknown
                output = tempEpub,
                onProgress = { done, total ->
                    val percent = 30 + (done * 65 / total)
                    updateProgress("epub", strings.engineWritingChapter(done, total), percent, onProgress)
                },
                checkCancelled = { job?.ensureActive() }
            )

            val finalPath = OutputManager.finalizeOutput(context, tempEpub, originalFileName, "epub")
            OutputManager.cleanTempFiles(tempEpub)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onProgress(strings.engineFinished100, 100)
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSaveOutput, inputUri, "epub")
                    onError(strings.engineErrSaveOutput)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempEpub)
            throw e
        }
    }

    private suspend fun convertEpubToTxt(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempEpub = OutputManager.createTempFile(context, "in_epub", "epub")
        val tempTxt = OutputManager.createTempFile(context, "out_txt", "txt")

        try {
            updateProgress("txt", strings.engineReadingEpub, 10, onProgress)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempEpub).use { output -> input.copyTo(output) }
            } ?: throw IOException(strings.engineErrReadEpub)

            val extractedText = EpubParser.parseEpubToText(tempEpub) { step, percent ->
                updateProgress("txt", step, percent, onProgress)
            }

            updateProgress("txt", strings.engineSavingEpubTxt, 95, onProgress)
            tempTxt.writeText(extractedText, Charsets.UTF_8)

            val finalPath = OutputManager.finalizeOutput(context, tempTxt, originalFileName, "txt")
            OutputManager.cleanTempFiles(tempEpub, tempTxt)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSaveTxt, inputUri, "txt")
                    onError(strings.engineErrSaveTxt)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempEpub, tempTxt)
            throw e
        }
    }

    /** Longest decoded image edge; a 200 MP photo would otherwise need ~800 MB and crash. */
    private const val MAX_IMAGE_EDGE_PX = 8192

    /** Decodes an image URI as a software bitmap, downscaled so its longest edge fits MAX_IMAGE_EDGE_PX. */
    internal fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_EDGE_PX) {
                    val f = MAX_IMAGE_EDGE_PX.toDouble() / longest
                    decoder.setTargetSize(
                        (info.size.width * f).toInt().coerceAtLeast(1),
                        (info.size.height * f).toInt().coerceAtLeast(1)
                    )
                }
            }
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException(AppStrings.get().engineErrReadInput)
    }

    /** WEBP_LOSSLESS ignores quality, so Medium/Low must use lossy WebP to actually shrink. */
    internal fun compressFormatFor(targetExt: String, quality: Int): Bitmap.CompressFormat = when (targetExt) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            quality >= 100 -> Bitmap.CompressFormat.WEBP_LOSSLESS
            else -> Bitmap.CompressFormat.WEBP_LOSSY
        }
        else -> Bitmap.CompressFormat.JPEG
    }

    private fun updateProgress(
        format: String,
        message: String,
        percent: Int,
        onProgress: (String, Int) -> Unit
    ) {
        currentStatus.value = ConvertState.Converting(format, percent)
        onProgress(message, percent)
    }

    fun cancelCurrentConversion() {
        MorphNative.cancel()
        activeJob?.cancel()
        activeJob = null
        FFmpegKit.cancel()
        currentStatus.value = ConvertState.Idle
    }
}
