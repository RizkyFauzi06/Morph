package com.morph.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.flow.MutableStateFlow

object MorphEngine {
    
    sealed class ConvertState {
        object Idle : ConvertState()
        data class Converting(val format: String, val progress: Int) : ConvertState()
        object Success : ConvertState()
        data class Error(val message: String, val originalUri: Uri? = null, val targetFormat: String = "") : ConvertState()
    }
    
    // Pipa yang akan dipantau oleh UI
    val currentStatus = MutableStateFlow<ConvertState>(ConvertState.Idle)

    fun convert(
        context: android.content.Context,
        inputUri: android.net.Uri,
        outputExtension: String,
        originalFileName: String,
        qualityPreset: Int, // PARAMETER BARU
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Jika format gambar, gunakan Native Android
        if (outputExtension in listOf("png", "jpg", "webp")) {
            // LEMPAR KE NATIVE
            convertImageNatively(context, inputUri, outputExtension, originalFileName, qualityPreset, onProgress, onSuccess, onError)
            return
        }
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                currentStatus.value = ConvertState.Converting(outputExtension, 0)
                onProgress("Menyiapkan file...", 0)
               // 1. PERSIAPKAN FILE SEMENTARA (DENGAN EKSTENSI YANG BENAR)
                val inputExt = originalFileName.substringAfterLast('.', "")
                val inputSuffix = if (inputExt.isNotEmpty()) ".$inputExt" else ""
                
                val cacheInputFile = java.io.File(context.cacheDir, "temp_input_morph$inputSuffix")
                val inputStream = context.contentResolver.openInputStream(inputUri)
                val outputStream = java.io.FileOutputStream(cacheInputFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val cacheOutputFile = java.io.File(context.cacheDir, "temp_output_morph.$outputExtension")

               // 2. DAPATKAN DURASI FILE UNTUK PROGRESS BAR & KALKULASI BITRATE
                val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(cacheInputFile.absolutePath).mediaInformation
                val totalDurationStr = mediaInfo?.duration
                val totalDurationMs = (totalDurationStr?.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L

                // MATEMATIKA CERDAS: Hitung Bitrate Asli (File Size in bits / Duration in seconds)
                val originalBitrateBps = if (totalDurationMs > 0) {
                    ((cacheInputFile.length() * 8.0) / (totalDurationMs / 1000.0)).toLong()
                } else {
                    5000000L // Fallback ke 5 Mbps jika durasi aneh/gagal dibaca
                }

                // Asumsikan video memakan 90% dari total ukuran file, sisa 10% untuk audio
                val videoBitrateBps = (originalBitrateBps * 0.9).toLong()

                // 3. SPRINT 3 (PBI 11): RACIKAN PARAMETER KUALITAS DINAMIS (SMART COMPRESSOR)
                val qualityArgs = if (outputExtension in listOf("mp4", "mkv", "webm", "avi", "mov")) {
                    when (qualityPreset) {
                        0 -> {
                            // TINGGI: Gunakan bitrate asli (Ukuran dan kualitas 100% terjaga)
                            "-b:v $videoBitrateBps -b:a 256k"
                        }
                        1 -> {
                            // SEDANG: Pangkas bitrate jadi 50% dari aslinya
                            val targetBv = (videoBitrateBps * 0.5).toLong()
                            "-b:v $targetBv -b:a 128k"
                        }
                        2 -> {
                            // RENDAH: Pangkas bitrate jadi 25% dari aslinya + paksa resolusi 720p
                            val targetBv = (videoBitrateBps * 0.25).toLong()
                            "-vf \"scale=trunc(iw/2)*2:720\" -b:v $targetBv -b:a 96k"
                        }
                        else -> ""
                    }
                } else if (outputExtension in listOf("mp3", "m4a", "ogg", "opus", "wav")) {
                    when (qualityPreset) {
                        0 -> "-b:a 320k" 
                        1 -> "-b:a 128k" 
                        2 -> "-b:a 64k"  
                        else -> ""
                    }
                } else {
                    "" 
                }

                // 4. SUSUN PERINTAH FFMPEG
                val command = "-y -i \"${cacheInputFile.absolutePath}\" $qualityArgs \"${cacheOutputFile.absolutePath}\""
                com.arthenica.ffmpegkit.FFmpegKit.executeAsync(
                    command,
                    { session ->
                        val returnCode = session.returnCode
                        if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                            val finalPath = saveToDownloads(context, cacheOutputFile, originalFileName, outputExtension)
                            if (finalPath != null) {
                                currentStatus.value = ConvertState.Success
                                onSuccess(finalPath)
                            } else {
                                currentStatus.value = ConvertState.Error("Gagal memindahkan file.", inputUri, outputExtension)
                                onError("Gagal memindahkan file.")
                            }
                        } else if (com.arthenica.ffmpegkit.ReturnCode.isCancel(returnCode)) {
                            currentStatus.value = ConvertState.Idle
                            cacheOutputFile.delete()
                            cacheInputFile.delete() // Bersihkan juga file inputnya
                            onError("Konversi dibatalkan pengguna.")
                        } else {
                            currentStatus.value = ConvertState.Error("Gagal Konversi (Kode: ${returnCode.value})", inputUri, outputExtension)
                            onError("Gagal Konversi (Kode: ${returnCode.value})")
                        }
                    },
                    { log -> /* Kosongkan */ },
                    { statistics ->
                        if (totalDurationMs > 0) {
                            val percentage = ((statistics.time.toFloat() / totalDurationMs) * 100).toInt().coerceIn(0, 100)
                            currentStatus.value = ConvertState.Converting(outputExtension, percentage)
                            onProgress("Telah diproses: $percentage%", percentage)
                        } else {
                            val secondsProcessed = statistics.time / 1000
                            currentStatus.value = ConvertState.Converting(outputExtension, 0)
                            onProgress("Telah diproses: $secondsProcessed detik", 0)
                        }
                    }
                )
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    currentStatus.value = ConvertState.Error("Gagal setup konversi", inputUri, outputExtension)
                    onError("Gagal setup konversi: ${e.message}")
                }
            }
        }
    }

    private fun convertImageNatively(
        context: android.content.Context,
        inputUri: android.net.Uri,
        extension: String,
        originalFileName: String,
        qualityPreset: Int, // PARAMETER BARU
        onProgress: (String, Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Update State Progress
                currentStatus.value = ConvertState.Converting(extension, 50)
                onProgress("Memproses gambar...", 50)

                // 1. Baca gambar dari URI
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, inputUri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, inputUri)
                }

                // 2. Tentukan format output
                // 2. Tentukan format output
                val format = when (extension.lowercase()) {
                    "png" -> android.graphics.Bitmap.CompressFormat.PNG
                    "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) 
                                android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS 
                              else 
                                @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
                    else -> android.graphics.Bitmap.CompressFormat.JPEG
                }

                // SPRINT 3 (PBI 11): Baca Preset Kualitas
                val prefs = MorphPrefs(context)
                val qualitySetting = when (qualityPreset) {
                    0 -> 100 // Tinggi 
                    1 -> 80  // Sedang 
                    2 -> 50  // Rendah 
                    else -> 100
                }

                // 3. Bikin Cache File & Simpan (Terapkan Kualitas)
                val cacheFile = java.io.File(context.cacheDir, "temp_image.$extension")
                val fos = java.io.FileOutputStream(cacheFile)
                bitmap.compress(format, qualitySetting, fos) // MENGGUNAKAN QUALITY SETTING
                fos.flush()
                fos.close()

                // 4. Pindah ke Downloads dengan nama asli
                val finalPath = saveToDownloads(context, cacheFile, originalFileName, extension)
                
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (finalPath != null) {
                        currentStatus.value = ConvertState.Success
                        onSuccess(finalPath)
                    } else {
                        currentStatus.value = ConvertState.Error("Gagal menyimpan gambar", inputUri, extension)
                        onError("Gagal menyimpan gambar")
                    }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) { 
                    currentStatus.value = ConvertState.Error("Gagal render gambar", inputUri, extension)
                    onError("Gagal render gambar: ${e.message}") 
                }
            }
        }
    }

    fun saveToDownloads(context: Context, cachedFile: File, originalName: String, targetFormat: String): String? {
        val prefs = MorphPrefs(context)
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val morphDir = File(downloadsDir, "Morph")
        if (!morphDir.exists()) morphDir.mkdirs()
        // Buang ekstensi lama dari nama aslinya
        val baseName = originalName.substringBeforeLast(".", "file")
        // Logika 8.2.5: Pertahankan nama atau tambah suffix unik
        var finalName = if (prefs.keepOriginalName) {
            "$baseName.$targetFormat"
        } else {
            "${baseName}_morph_converted.$targetFormat"
        }
        var destFile = File(morphDir, finalName)
        // Cegah file tertimpa jika nama sama
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
    
    // PBI 8.9: Fungsi Pembatalan Global
    fun cancelCurrentConversion() {
        com.arthenica.ffmpegkit.FFmpegKit.cancel() // Menghentikan semua proses FFmpeg C++ seketika
        currentStatus.value = ConvertState.Idle
    }
}