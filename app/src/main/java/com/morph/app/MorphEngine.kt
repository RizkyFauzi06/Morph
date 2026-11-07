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

object MorphEngine {

    fun convert(
        context: Context,
        inputUri: Uri,
        outputExtension: String,
        onProgress: (String) -> Unit = {},
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val timeStamp = System.currentTimeMillis()
        val cacheOutputFile = File(context.cacheDir, "Morph_$timeStamp.$outputExtension")

        // PISAHKAN JALUR: Jika Gambar, gunakan Android Native (Cepat & Support WEBP)
        if (outputExtension in listOf("jpg", "png", "webp")) {
            convertImageNatively(context, inputUri, cacheOutputFile, outputExtension, onProgress, onSuccess, onError)
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

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                val finalPath = saveToDownloads(context, cacheOutputFile)
                if (finalPath != null) onSuccess(finalPath) else onError("Gagal memindahkan file.")
            } else {
                onError("Gagal: ${session.failStackTrace}")
                Log.e("Morph", "FFmpeg Error: ${session.allLogsAsString}")
            }
        }
    }

    private fun convertImageNatively(
        context: Context,
        inputUri: Uri,
        cacheFile: File,
        extension: String,
        onProgress: (String) -> Unit = {},
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) throw Exception("Bitmap gagal dibaca")

                val compressFormat = when (extension) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.valueOf("WEBP")
                    else -> Bitmap.CompressFormat.JPEG
                }

                val outStream = FileOutputStream(cacheFile)
                bitmap.compress(compressFormat, 90, outStream)
                outStream.flush()
                outStream.close()

                val finalPath = saveToDownloads(context, cacheFile)
                launch(Dispatchers.Main) {
                    if (finalPath != null) onSuccess(finalPath) else onError("Gagal menyimpan gambar")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError("Gagal render gambar: ${e.message}") }
            }
        }
    }

    private fun saveToDownloads(context: Context, cachedFile: File): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val morphDir = File(downloadsDir, "Morph")
            if (!morphDir.exists()) morphDir.mkdirs()

            val destFile = File(morphDir, cachedFile.name)
            cachedFile.copyTo(destFile, overwrite = true)
            cachedFile.delete()
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}