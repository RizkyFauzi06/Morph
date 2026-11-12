package com.morph.app.core

import android.content.Context
import android.os.Environment
import com.morph.app.MorphPrefs
import java.io.File
import java.io.IOException
import java.util.UUID

object OutputManager {

    fun getMorphDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val morphDir = File(downloadsDir, "Morph")
        if (!morphDir.exists()) {
            morphDir.mkdirs()
        }
        return morphDir
    }

    fun createTempFile(context: Context, prefix: String, extension: String): File {
        val ext = extension.trim().removePrefix(".")
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        val uniqueId = UUID.randomUUID().toString().take(8)
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return File(context.cacheDir, "${safePrefix}_${uniqueId}$suffix")
    }

    fun resolveUniqueOutputFile(context: Context, originalName: String, targetFormat: String): File {
        val morphDir = getMorphDirectory()
        val prefs = MorphPrefs(context)
        val targetExt = targetFormat.trim().removePrefix(".").lowercase()

        val baseName = originalName.substringBeforeLast('.', "file")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // Sanitize forbidden filename characters
            .ifBlank { "file" }

        var fileName = if (prefs.keepOriginalName) {
            "$baseName.$targetExt"
        } else {
            "${baseName}_morph_converted.$targetExt"
        }

        var destFile = File(morphDir, fileName)
        var counter = 1

        while (destFile.exists()) {
            fileName = if (prefs.keepOriginalName) {
                "${baseName}($counter).$targetExt"
            } else {
                "${baseName}_morph_converted_$counter.$targetExt"
            }
            destFile = File(morphDir, fileName)
            counter++
        }

        return destFile
    }

    fun finalizeOutput(context: Context, tempFile: File, originalName: String, targetFormat: String): String? {
        if (!tempFile.exists() || tempFile.length() <= 0) {
            return null
        }

        val destFile = resolveUniqueOutputFile(context, originalName, targetFormat)
        return try {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            destFile.absolutePath
        } catch (e: IOException) {
            null
        }
    }

    fun cleanTempFiles(vararg files: File?) {
        for (f in files) {
            try {
                if (f != null && f.exists()) {
                    f.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
