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
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MorphEngine {
    
    sealed class ConvertState {
        object Idle : ConvertState()
        data class Converting(val format: String, val progress: Int) : ConvertState()
        object Success : ConvertState()
        data class Error(val message: String, val originalUri: Uri? = null, val targetFormat: String = "") : ConvertState()
    }
    
    // Pipa yang dipantau oleh UI
    val currentStatus = MutableStateFlow<ConvertState>(ConvertState.Idle)

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
        val inputExt = originalFileName.substringAfterLast('.', "").lowercase()
        val targetExt = outputExtension.lowercase()

        // --- RUTE 1: Input Dokumen PDF (PBI 2.5 & 13) ---
        if (inputExt == "pdf") {
            if (targetExt in listOf("jpg", "jpeg", "png", "webp")) {
                convertPdfToImages(context, inputUri, targetExt, originalFileName, qualityPreset, onProgress, onSuccess, onError)
                return
            } else if (targetExt == "pdf") {
                compressPdf(context, inputUri, originalFileName, qualityPreset, onProgress, onSuccess, onError)
                return
            }
        }

        // --- RUTE 2: Output PDF dari Gambar (Image ke PDF) ---
        if (targetExt == "pdf" && inputExt in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic")) {
            convertImageToPdf(context, inputUri, originalFileName, onProgress, onSuccess, onError)
            return
        }

        // --- RUTE 3: Native Image Converter (JPG, PNG, WEBP) ---
        if (targetExt in listOf("png", "jpg", "jpeg", "webp")) {
            convertImageNatively(context, inputUri, targetExt, originalFileName, qualityPreset, onProgress, onSuccess, onError)
            return
        }

        // --- RUTE 4: Audio / Video via FFmpeg Engine (Termasuk PBI 11: Lossless Stream Copy) ---
        GlobalScope.launch(Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting(targetExt, 0)
                onProgress("Menyiapkan file multimedia...", 0)

                val inputSuffix = if (inputExt.isNotEmpty()) ".$inputExt" else ""
                val cacheInputFile = File(context.cacheDir, "temp_input_morph$inputSuffix")
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(cacheInputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val cacheOutputFile = File(context.cacheDir, "temp_output_morph.$targetExt")

                // Dapatkan durasi dan metadata
                val mediaInfo = FFprobeKit.getMediaInformation(cacheInputFile.absolutePath).mediaInformation
                val totalDurationStr = mediaInfo?.duration
                val totalDurationMs = (totalDurationStr?.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L

                val originalBitrateBps = if (totalDurationMs > 0) {
                    ((cacheInputFile.length() * 8.0) / (totalDurationMs / 1000.0)).toLong()
                } else {
                    5000000L
                }
                val videoBitrateBps = (originalBitrateBps * 0.9).toLong()

                // PBI 11: Parameter Kualitas & Lossless Stream Copy
                val qualityArgs = if (qualityPreset == 3) {
                    // LOSSLESS STREAM COPY: Tidak melakukan encoding ulang, langsung salin stream bitstream!
                    if (targetExt in listOf("mp4", "mkv", "mov", "avi", "webm")) {
                        "-c copy"
                    } else if (targetExt in listOf("m4a", "aac", "mp3")) {
                        "-vn -c:a copy"
                    } else {
                        "-c copy"
                    }
                } else if (targetExt in listOf("mp4", "mkv", "webm", "avi", "mov")) {
                    when (qualityPreset) {
                        0 -> "-b:v $videoBitrateBps -b:a 256k"
                        1 -> {
                            val targetBv = (videoBitrateBps * 0.5).toLong()
                            "-b:v $targetBv -b:a 128k"
                        }
                        2 -> {
                            val targetBv = (videoBitrateBps * 0.25).toLong()
                            "-vf \"scale=trunc(iw/2)*2:720\" -b:v $targetBv -b:a 96k"
                        }
                        else -> "-b:v $videoBitrateBps -b:a 256k"
                    }
                } else if (targetExt in listOf("mp3", "m4a", "ogg", "opus", "wav")) {
                    when (qualityPreset) {
                        0 -> "-b:a 320k"
                        1 -> "-b:a 128k"
                        2 -> "-b:a 64k"
                        else -> "-b:a 192k"
                    }
                } else {
                    ""
                }

                val command = "-y -i \"${cacheInputFile.absolutePath}\" $qualityArgs \"${cacheOutputFile.absolutePath}\""
                FFmpegKit.executeAsync(
                    command,
                    { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode)) {
                            val finalPath = saveToDownloads(context, cacheOutputFile, originalFileName, targetExt)
                            cacheOutputFile.delete()
                            cacheInputFile.delete()
                            if (finalPath != null) {
                                currentStatus.value = ConvertState.Success
                                onSuccess(finalPath)
                            } else {
                                currentStatus.value = ConvertState.Error("Gagal memindahkan file ke Downloads.", inputUri, targetExt)
                                onError("Gagal memindahkan file ke Downloads.")
                            }
                        } else if (ReturnCode.isCancel(returnCode)) {
                            currentStatus.value = ConvertState.Idle
                            cacheOutputFile.delete()
                            cacheInputFile.delete()
                            onError("Konversi dibatalkan pengguna.")
                        } else {
                            val errMessage = if (qualityPreset == 3) {
                                "Stream Copy tidak kompatibel untuk file ini. Silakan gunakan kualitas Tinggi atau Sedang."
                            } else {
                                "Gagal Konversi (Kode: ${returnCode.value})"
                            }
                            currentStatus.value = ConvertState.Error(errMessage, inputUri, targetExt)
                            cacheOutputFile.delete()
                            cacheInputFile.delete()
                            onError(errMessage)
                        }
                    },
                    { _ -> },
                    { statistics ->
                        if (totalDurationMs > 0) {
                            val percentage = ((statistics.time.toFloat() / totalDurationMs) * 100).toInt().coerceIn(0, 100)
                            currentStatus.value = ConvertState.Converting(targetExt, percentage)
                            onProgress("Telah diproses: $percentage%", percentage)
                        } else {
                            val secondsProcessed = statistics.time / 1000
                            currentStatus.value = ConvertState.Converting(targetExt, 0)
                            onProgress("Telah diproses: $secondsProcessed detik", 0)
                        }
                    }
                )
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error("Gagal setup konversi: ${e.message}", inputUri, targetExt)
                    onError("Gagal setup konversi: ${e.message}")
                }
            }
        }
    }

    // --- PBI 2.5 & 13: KONVERSI PDF KE GAMBAR (JPG/PNG) NATIVE ANDROID ---
    private fun convertPdfToImages(
        context: Context,
        inputUri: Uri,
        targetExtension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting(targetExtension, 10)
                onProgress("Membaca dokumen PDF...", 10)

                val cacheInputFile = File(context.cacheDir, "temp_pdf_in.pdf")
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(cacheInputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val pfd = ParcelFileDescriptor.open(cacheInputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount

                if (pageCount == 0) {
                    renderer.close()
                    pfd.close()
                    cacheInputFile.delete()
                    throw IllegalStateException("Dokumen PDF tidak memiliki halaman.")
                }

                // Resolusi render berbasis preset
                val scale = when (qualityPreset) {
                    0 -> 2.0f // Tinggi (144+ DPI)
                    1 -> 1.5f // Sedang
                    2 -> 1.0f // Rendah (72 DPI)
                    3 -> 2.0f // Lossless
                    else -> 1.5f
                }

                val format = if (targetExtension == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val qualityVal = when (qualityPreset) {
                    0 -> 100
                    1 -> 80
                    2 -> 60
                    3 -> 100
                    else -> 85
                }

                var lastSavedPath: String? = null
                val baseName = originalFileName.substringBeforeLast('.', "doc")

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE) // PDF latar belakang putih
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageSuffix = if (pageCount > 1) "_hal_${i + 1}" else ""
                    val pageFileName = "$baseName$pageSuffix.$targetExtension"
                    val cachePageFile = File(context.cacheDir, "temp_page_${i}.$targetExtension")
                    FileOutputStream(cachePageFile).use { fos ->
                        bitmap.compress(format, qualityVal, fos)
                    }
                    bitmap.recycle()

                    val savedPath = saveToDownloads(context, cachePageFile, pageFileName, targetExtension)
                    cachePageFile.delete()
                    if (savedPath != null) lastSavedPath = savedPath

                    val percent = (((i + 1).toFloat() / pageCount) * 85).toInt() + 10
                    currentStatus.value = ConvertState.Converting(targetExtension, percent)
                    onProgress("Halaman ${i + 1} dari $pageCount...", percent)
                }

                renderer.close()
                pfd.close()
                cacheInputFile.delete()

                launch(Dispatchers.Main) {
                    if (lastSavedPath != null) {
                        currentStatus.value = ConvertState.Success
                        val msg = if (pageCount > 1) "$pageCount halaman berhasil dikonversi ke Downloads/Morph" else lastSavedPath
                        onSuccess(msg)
                    } else {
                        currentStatus.value = ConvertState.Error("Gagal menyimpan hasil gambar PDF", inputUri, targetExtension)
                        onError("Gagal menyimpan hasil gambar PDF")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error("Gagal ekstrak gambar PDF: ${e.message}", inputUri, targetExtension)
                    onError("Gagal ekstrak gambar PDF: ${e.message}")
                }
            }
        }
    }

    // --- PBI 2.5 & 13: KOMPRESI PDF (REDUCE FILE SIZE SECARA CERDAS) ---
    private fun compressPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting("pdf", 10)
                onProgress("Menganalisis ukuran PDF...", 10)

                val cacheInputFile = File(context.cacheDir, "temp_pdf_comp_in.pdf")
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(cacheInputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val pfd = ParcelFileDescriptor.open(cacheInputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount

                if (pageCount == 0) {
                    renderer.close()
                    pfd.close()
                    cacheInputFile.delete()
                    throw IllegalStateException("Dokumen PDF kosong.")
                }

                val (scale, jpegQuality) = when (qualityPreset) {
                    0 -> Pair(1.0f, 85) // Tinggi
                    1 -> Pair(0.8f, 65) // Sedang (Disarankan)
                    2 -> Pair(0.6f, 45) // Rendah (Kompresi Maksimal)
                    3 -> Pair(1.0f, 95) // Lossless
                    else -> Pair(0.8f, 65)
                }

                val pdfDocument = PdfDocument()

                for (i in 0 until pageCount) {
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
                    currentStatus.value = ConvertState.Converting("pdf", percent)
                    onProgress("Mengompres halaman ${i + 1} dari $pageCount...", percent)
                }

                renderer.close()
                pfd.close()

                val cacheOutputFile = File(context.cacheDir, "temp_pdf_compressed.pdf")
                FileOutputStream(cacheOutputFile).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                pdfDocument.close()
                cacheInputFile.delete()

                val finalPath = saveToDownloads(context, cacheOutputFile, originalFileName, "pdf")
                cacheOutputFile.delete()

                launch(Dispatchers.Main) {
                    if (finalPath != null) {
                        currentStatus.value = ConvertState.Success
                        onSuccess(finalPath)
                    } else {
                        currentStatus.value = ConvertState.Error("Gagal menyimpan hasil kompresi PDF", inputUri, "pdf")
                        onError("Gagal menyimpan hasil kompresi PDF")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error("Gagal kompres PDF: ${e.message}", inputUri, "pdf")
                    onError("Gagal kompres PDF: ${e.message}")
                }
            }
        }
    }

    // --- FITUR EKSPANSI: IMAGE KE PDF ---
    private fun convertImageToPdf(
        context: Context,
        inputUri: Uri,
        originalFileName: String,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting("pdf", 30)
                onProgress("Memuat gambar...", 30)

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, inputUri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, inputUri)
                }

                currentStatus.value = ConvertState.Converting("pdf", 60)
                onProgress("Menyusun lembar PDF...", 60)

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)

                val cacheOutputFile = File(context.cacheDir, "temp_img_to_pdf.pdf")
                FileOutputStream(cacheOutputFile).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                pdfDocument.close()

                val finalPath = saveToDownloads(context, cacheOutputFile, originalFileName, "pdf")
                cacheOutputFile.delete()

                launch(Dispatchers.Main) {
                    if (finalPath != null) {
                        currentStatus.value = ConvertState.Success
                        onSuccess(finalPath)
                    } else {
                        currentStatus.value = ConvertState.Error("Gagal membuat PDF dari gambar", inputUri, "pdf")
                        onError("Gagal membuat PDF dari gambar")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error("Gagal render PDF: ${e.message}", inputUri, "pdf")
                    onError("Gagal render PDF: ${e.message}")
                }
            }
        }
    }

    // --- NATIVE IMAGE CONVERTER ---
    private fun convertImageNatively(
        context: Context,
        inputUri: Uri,
        extension: String,
        originalFileName: String,
        qualityPreset: Int,
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting(extension, 50)
                onProgress("Memproses gambar...", 50)

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, inputUri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, inputUri)
                }

                val format = when (extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 
                                Bitmap.CompressFormat.WEBP_LOSSLESS 
                              else 
                                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }

                val qualitySetting = when (qualityPreset) {
                    0 -> 100 // Tinggi
                    1 -> 80  // Sedang
                    2 -> 50  // Rendah
                    3 -> 100 // Lossless
                    else -> 100
                }

                val cacheFile = File(context.cacheDir, "temp_image.$extension")
                FileOutputStream(cacheFile).use { fos ->
                    bitmap.compress(format, qualitySetting, fos)
                }

                val finalPath = saveToDownloads(context, cacheFile, originalFileName, extension)
                cacheFile.delete()
                
                launch(Dispatchers.Main) {
                    if (finalPath != null) {
                        currentStatus.value = ConvertState.Success
                        onSuccess(finalPath)
                    } else {
                        currentStatus.value = ConvertState.Error("Gagal menyimpan gambar", inputUri, extension)
                        onError("Gagal menyimpan gambar")
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { 
                    currentStatus.value = ConvertState.Error("Gagal render gambar: ${e.message}", inputUri, extension)
                    onError("Gagal render gambar: ${e.message}") 
                }
            }
        }
    }

    fun saveToDownloads(context: Context, cachedFile: File, originalName: String, targetFormat: String): String? {
        val prefs = MorphPrefs(context)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val morphDir = File(downloadsDir, "Morph")
        if (!morphDir.exists()) morphDir.mkdirs()

        val baseName = originalName.substringBeforeLast(".", "file")
        var finalName = if (prefs.keepOriginalName) {
            "$baseName.$targetFormat"
        } else {
            "${baseName}_morph_converted.$targetFormat"
        }
        var destFile = File(morphDir, finalName)

        var counter = 1
        while (destFile.exists()) {
            finalName = if (prefs.keepOriginalName) {
                "${baseName}($counter).$targetFormat"
            } else {
                "${baseName}_morph_converted_$counter.$targetFormat"
            }
            destFile = File(morphDir, finalName)
            counter++
        }

        return try {
            cachedFile.copyTo(destFile, overwrite = true)
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    // Fungsi Pembatalan Global
    fun cancelCurrentConversion() {
        FFmpegKit.cancel()
        currentStatus.value = ConvertState.Idle
    }
}
