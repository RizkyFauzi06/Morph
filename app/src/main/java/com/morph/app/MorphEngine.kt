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
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.morph.app.core.*
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

    sealed class ConvertState {
        object Idle : ConvertState()
        data class Converting(val format: String, val progress: Int) : ConvertState()
        object Success : ConvertState()
        data class Error(val message: String, val originalUri: Uri? = null, val targetFormat: String = "") : ConvertState()
    }

    val currentStatus = MutableStateFlow<ConvertState>(ConvertState.Idle)

    // Managed Coroutine Scope tied to the Engine lifecycle
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    suspend fun executeConversion(
        context: Context,
        inputUri: Uri,
        outputExtension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit
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
                    compressPdf(context, inputUri, originalFileName, qualityPreset, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
                    )
                }

                // Route 2: Image to PDF
                targetExt == "pdf" && inputExt in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") -> {
                    convertImageToPdf(context, inputUri, originalFileName, onProgress,
                        { path -> resultDeferred.complete(path) },
                        { err -> resultDeferred.completeExceptionally(IOException(err)) }
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
                    convertMediaWithFFmpeg(context, inputUri, inputExt, targetExt, originalFileName, qualityPreset, onProgress,
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

    fun convert(
        context: Context,
        inputUri: Uri,
        outputExtension: String,
        originalFileName: String,
        qualityPreset: Int, // 0: Tinggi, 1: Sedang, 2: Rendah, 3: Instan (Lossless)
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val targetExt = outputExtension.trim().removePrefix(".").lowercase()

        // Cancel any pending conversion job before starting a new one
        activeJob?.cancel()

        activeJob = engineScope.launch {
            try {
                val finalPath = executeConversion(
                    context = context,
                    inputUri = inputUri,
                    outputExtension = targetExt,
                    originalFileName = originalFileName,
                    qualityPreset = qualityPreset,
                    onProgress = onProgress
                )
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Idle
                    onError(AppStrings.get().engineCancelled)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Conversion setup error", e)
                withContext(Dispatchers.Main) {
                    val errMsg = AppStrings.get().engineErrSetup(e.message ?: "")
                    currentStatus.value = ConvertState.Error(errMsg, inputUri, targetExt)
                    onError(errMsg)
                }
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
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempInput = OutputManager.createTempFile(context, "in_media", inputExt)
        val tempOutput = OutputManager.createTempFile(context, "out_media", targetExt)

        try {
            updateProgress(targetExt, strings.enginePreparingMedia, 5, onProgress)

            // Copy input stream to temp cache file
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException(strings.engineErrReadInput)

            if (!tempInput.exists() || tempInput.length() <= 0) {
                throw IOException(strings.engineErrInputEmpty)
            }

            // Probe media information via FFprobe
            val mediaInfo = FFprobeKit.getMediaInformation(tempInput.absolutePath).mediaInformation
            val totalDurationStr = mediaInfo?.duration
            val totalDurationMs = (totalDurationStr?.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L

            val originalBitrateBps = if (totalDurationMs > 0) {
                ((tempInput.length() * 8.0) / (totalDurationMs / 1000.0)).toLong()
            } else {
                4000000L
            }
            val videoBitrateBps = (originalBitrateBps * 0.9).toLong().coerceIn(400000L, 20000000L)

            // Video rotation check
            val videoRotation = getVideoRotation(mediaInfo)

            // Lossless / Stream Copy validation
            val isLosslessSupported = ConversionCapabilityManager.isLosslessCompatible(inputExt, targetExt)
            // Crucial: If video has rotation metadata and target is MKV or WebM, stream copy causes unrotated orientation on Android
            val isLosslessCapable = isLosslessSupported && !(videoRotation != 0 && targetExt in listOf("mkv", "webm"))

            val effectivePreset = if (qualityPreset == 3 && !isLosslessCapable) {
                0 // Fallback to High if format doesn't support stream copying
            } else {
                qualityPreset
            }

            val qualityArgs = buildFFmpegQualityArgs(inputExt, targetExt, effectivePreset, videoBitrateBps, videoRotation)
            val command = "-y -i \"${tempInput.absolutePath}\" $qualityArgs \"${tempOutput.absolutePath}\""

            val ffmpegSession = CompletableDeferred<ReturnCode>()

            val session = FFmpegKit.executeAsync(
                command,
                { s -> ffmpegSession.complete(s.returnCode) },
                { log -> Log.d(TAG, "[FFmpeg] ${log.message}") },
                { stats ->
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
                val failLogs = session.allLogsAsString ?: ""
                OutputManager.cleanTempFiles(tempInput, tempOutput)
                val errorMessage = when {
                    qualityPreset == 3 ->
                        strings.engineErrStreamCopyIncompatible
                    failLogs.contains("does not support", ignoreCase = true) ->
                        strings.engineErrFormatNotSupported
                    else ->
                        strings.engineErrFFmpeg(returnCode.value)
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

    private fun buildFFmpegQualityArgs(
        inputExt: String,
        targetExt: String,
        preset: Int,
        videoBitrateBps: Long,
        videoRotation: Int = 0
    ): String {
        // Stream Copy (Lossless Remuxing)
        if (preset == 3) {
            return when (targetExt) {
                "mp4", "mov" -> "-c copy -sn -dn -movflags +faststart"
                "mkv" -> "-c copy -sn -dn"
                "m4a", "aac" -> "-vn -sn -dn -c:a copy"
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

        // Target: WebM
        if (targetExt == "webm") {
            val rotateArg = if (videoRotation != 0) "-metadata:s:v rotate=0 " else ""
            return when (preset) {
                0 -> "-c:v libvpx-vp9 -b:v $videoBitrateBps -deadline realtime -cpu-used 4 -c:a libopus -b:a 192k $rotateArg"
                1 -> {
                    val halfBv = (videoBitrateBps * 0.6).toLong()
                    "-c:v libvpx-vp9 -b:v $halfBv -deadline realtime -cpu-used 4 -c:a libopus -b:a 128k $rotateArg"
                }
                2 -> {
                    val lowBv = (videoBitrateBps * 0.35).toLong()
                    val scaleFilter = "scale='if(gt(iw,ih),854,-2)':'if(gt(iw,ih),-2,854)'"
                    "-vf \"$scaleFilter\" -c:v libvpx-vp9 -b:v $lowBv -deadline realtime -cpu-used 4 -c:a libopus -b:a 96k $rotateArg"
                }
                else -> "-c:v libvpx-vp9 -b:v $videoBitrateBps -deadline realtime -cpu-used 4 -c:a libopus -b:a 128k $rotateArg"
            }
        }

        // Target: Standard Video Containers (MP4, MKV, MOV, AVI)
        if (targetExt in listOf("mp4", "mkv", "mov", "avi")) {
            val rotateArg = if (videoRotation != 0) "-metadata:s:v rotate=0 " else ""
            val fastStartArg = if (targetExt in listOf("mp4", "mov")) "-movflags +faststart " else ""
            return when (preset) {
                0 -> "$fastStartArg-b:v $videoBitrateBps -b:a 256k $rotateArg"
                1 -> {
                    val targetBv = (videoBitrateBps * 0.6).toLong()
                    "$fastStartArg-b:v $targetBv -b:a 160k $rotateArg"
                }
                2 -> {
                    val targetBv = (videoBitrateBps * 0.35).toLong()
                    val scaleFilter = "scale='if(gt(iw,ih),854,-2)':'if(gt(iw,ih),-2,854)'"
                    "$fastStartArg-vf \"$scaleFilter\" -b:v $targetBv -b:a 96k $rotateArg"
                }
                else -> "$fastStartArg-b:v $videoBitrateBps -b:a 256k $rotateArg"
            }
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

        try {
            updateProgress(targetExtension, strings.engineReadingPdf, 10, onProgress)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
            } ?: throw IOException(strings.engineErrPdfRead)

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            if (pageCount == 0) {
                renderer.close()
                pfd.close()
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

                val page = renderer.openPage(i)
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE) // PDF pages need opaque white background

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageSuffix = if (pageCount > 1) "_hal_${i + 1}" else ""
                val pageFileName = "$baseName$pageSuffix.$targetExtension"
                val tempPageFile = OutputManager.createTempFile(context, "pdf_page_$i", targetExtension)
                createdTempFiles.add(tempPageFile)

                FileOutputStream(tempPageFile).use { fos ->
                    bitmap.compress(format, qualityVal, fos)
                }
                bitmap.recycle()

                val savedPath = OutputManager.finalizeOutput(context, tempPageFile, pageFileName, targetExtension)
                if (savedPath != null) lastSavedPath = savedPath
            }

            renderer.close()
            pfd.close()
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
            OutputManager.cleanTempFiles(tempPdf, *createdTempFiles.toTypedArray())
            throw e
        }
    }

    // --- 3. PDF COMPRESSOR ---
    private suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempInput = OutputManager.createTempFile(context, "comp_in", "pdf")
        val tempOutput = OutputManager.createTempFile(context, "comp_out", "pdf")

        try {
            updateProgress("pdf", strings.engineAnalyzingPdf, 10, onProgress)

            context.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            } ?: throw IOException(strings.engineErrPdfRead)

            val pfd = ParcelFileDescriptor.open(tempInput, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            if (pageCount == 0) {
                renderer.close()
                pfd.close()
                throw IllegalStateException(strings.engineErrPdfEmpty)
            }

            val (scale, jpegQuality) = when (qualityPreset) {
                0 -> Pair(1.0f, 85)
                1 -> Pair(0.8f, 65)
                2 -> Pair(0.6f, 45)
                3 -> Pair(1.0f, 95)
                else -> Pair(0.8f, 65)
            }

            val pdfDocument = PdfDocument()

            for (i in 0 until pageCount) {
                currentCoroutineContext().ensureActive()

                val page = renderer.openPage(i)
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val compressedBytes = baos.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                bitmap.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val docPage = pdfDocument.startPage(pageInfo)
                docPage.canvas.drawBitmap(compressedBitmap, 0f, 0f, null)
                pdfDocument.finishPage(docPage)
                compressedBitmap.recycle()

                val percent = (((i + 1).toFloat() / pageCount) * 80).toInt() + 10
                updateProgress("pdf", strings.engineCompressingPage(i + 1, pageCount), percent, onProgress)
            }

            renderer.close()
            pfd.close()

            FileOutputStream(tempOutput).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, "pdf")
            OutputManager.cleanTempFiles(tempInput, tempOutput)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrSavePdfCompress, inputUri, "pdf")
                    onError(strings.engineErrSavePdfCompress)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempInput, tempOutput)
            throw e
        }
    }

    // --- 4. IMAGE TO PDF ---
    private suspend fun convertImageToPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val strings = AppStrings.get()
        val tempOutput = OutputManager.createTempFile(context, "img_pdf", "pdf")
        try {
            updateProgress("pdf", strings.engineLoadingImage, 30, onProgress)

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, inputUri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, inputUri)
            }

            updateProgress("pdf", strings.engineBuildingPdfPages, 60, onProgress)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
            bitmap.recycle()

            FileOutputStream(tempOutput).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            val finalPath = OutputManager.finalizeOutput(context, tempOutput, originalFileName, "pdf")
            OutputManager.cleanTempFiles(tempOutput)

            withContext(Dispatchers.Main) {
                if (finalPath != null) {
                    currentStatus.value = ConvertState.Success
                    onSuccess(finalPath)
                } else {
                    currentStatus.value = ConvertState.Error(strings.engineErrImageToPdf, inputUri, "pdf")
                    onError(strings.engineErrImageToPdf)
                }
            }
        } catch (e: Exception) {
            OutputManager.cleanTempFiles(tempOutput)
            throw e
        }
    }

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

        try {
            val total = inputUris.size
            for ((index, uri) in inputUris.withIndex()) {
                currentCoroutineContext().ensureActive()
                val percent = ((index.toFloat() / total) * 85).toInt() + 5
                updateProgress("pdf", strings.engineBuildingPdfPage(index + 1, total), percent, onProgress)

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

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

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, inputUri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, inputUri)
            }

            updateProgress(targetExt, strings.engineCompressingImage, 70, onProgress)

            if (targetExt == "bmp") {
                BmpWriter.writeBitmapAsBmp(bitmap, tempOutput)
                bitmap.recycle()
            } else {
                val isJpg = targetExt == "jpg" || targetExt == "jpeg"
                val finalBitmap = if (isJpg && bitmap.hasAlpha()) {
                    // Composite alpha over solid white background to prevent black distortion
                    val opaqueBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(opaqueBitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    bitmap.recycle()
                    opaqueBitmap
                } else {
                    bitmap
                }

                val format = when (targetExt) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    else -> Bitmap.CompressFormat.JPEG
                }

                val qualitySetting = when (qualityPreset) {
                    0, 3 -> 100 // High / Lossless
                    1    -> 85  // Medium
                    2    -> 65  // Low
                    else -> 90
                }

                FileOutputStream(tempOutput).use { fos ->
                    finalBitmap.compress(format, qualitySetting, fos)
                }
                finalBitmap.recycle()
            }

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
        activeJob?.cancel()
        activeJob = null
        FFmpegKit.cancel()
        currentStatus.value = ConvertState.Idle
    }
}
