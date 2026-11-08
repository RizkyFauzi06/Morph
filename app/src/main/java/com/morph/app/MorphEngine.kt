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
        context: Context,
        inputUri: Uri,
        outputExtension: String,
        originalFileName: String, // Pastikan ini ada
        onProgress: (String, Int) -> Unit, 
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val timeStamp = System.currentTimeMillis()
        val cacheOutputFile = File(context.cacheDir, "Morph_$timeStamp.$outputExtension")

        // PISAHKAN JALUR: Jika Gambar, gunakan Android Native (Cepat & Support WEBP)
        if (outputExtension in listOf("png", "jpg", "webp")) {
            // UBAH BARIS INI: Tambahkan originalFileName
            convertImageNatively(context, inputUri, outputExtension, originalFileName, onProgress, onSuccess, onError)
            return
        }

        // JIKA VIDEO / AUDIO: Gunakan FFmpeg
        val inputPath = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        
        // Smart Command Builder
        val command = when (outputExtension) {
            "mp3", "m4a", "wav" -> "-y -i \"$inputPath\" -vn \"${cacheOutputFile.absolutePath}\"" // -vn = Matikan Video, ambil Audio saja
            "gif" -> "-y -i \"$inputPath\" -vf \"fps=10,scale=480:-1:flags=lanczos\" \"${cacheOutputFile.absolutePath}\"" // Kompresi GIF agar RAM tidak jebol
            else -> "-y -i \"$inputPath\" \"${cacheOutputFile.absolutePath}\"" // Konversi standar (mkv <-> mp4)
        }

        // Baca durasi menggunakan Engine Native Android (Lebih aman dari FFprobe)
        var totalDurationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, inputUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            totalDurationMs = durationStr?.toLong() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.e("Morph", "Gagal membaca durasi: ${e.message}")
        }

        // Eksekusi FFmpeg
        // ... kode sebelumnya ...
        FFmpegKit.executeAsync(
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
                    // PBI 8.9: Jika sengaja dibatalkan, kembalikan ke Idle dan hapus cache
                    currentStatus.value = ConvertState.Idle
                    cacheOutputFile.delete()
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
                    // UBAH BARIS INI: Kirim angka persentase sebagai parameter kedua
                    onProgress("Telah diproses: $percentage%", percentage) 
                } else {
                    val secondsProcessed = statistics.time / 1000
                    currentStatus.value = ConvertState.Converting(outputExtension, 0)
                    // UBAH BARIS INI: Kirim angka 0 (karena durasi gagal dibaca)
                    onProgress("Telah diproses: $secondsProcessed detik", 0) 
                }
            }
        )
        
        
    }

    private fun convertImageNatively(
        context: android.content.Context,
        inputUri: android.net.Uri,
        extension: String,
        originalFileName: String,
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
                val format = when (extension.lowercase()) {
                    "png" -> android.graphics.Bitmap.CompressFormat.PNG
                    "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) 
                                android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS 
                              else 
                                @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
                    else -> android.graphics.Bitmap.CompressFormat.JPEG
                }

                // 3. Bikin Cache File & Simpan (Ini yang tadi hilang / error!)
                val cacheFile = java.io.File(context.cacheDir, "temp_image.$extension")
                val fos = java.io.FileOutputStream(cacheFile)
                bitmap.compress(format, 100, fos)
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