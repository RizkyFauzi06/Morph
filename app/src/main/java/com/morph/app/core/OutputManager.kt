package com.morph.app.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.morph.app.MorphPrefs
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

object OutputManager {

    private const val TAG = "OutputManager"
    private const val MORPH_FOLDER = "Morph"
    private const val MAX_NAME_ATTEMPTS = 500

    /** Relative path used by MediaStore (API 29+). Trailing slash matches what MediaStore stores. */
    private val MEDIASTORE_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$MORPH_FOLDER/"

    /** Path of Downloads/Morph without touching the filesystem. */
    private fun morphDirectoryPath(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), MORPH_FOLDER)

    fun getMorphDirectory(): File {
        val morphDir = morphDirectoryPath()
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

    /**
     * Explicit extension -> MIME table for every format Morph can produce or open.
     * Never returns a blank value.
     */
    fun getMimeTypeForExtension(extension: String, fallback: String = "application/octet-stream"): String {
        return when (extension.trim().removePrefix(".").lowercase()) {
            // Images
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            // Video
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            // Audio
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            // Documents
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "epub" -> "application/epub+zip"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                ?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    /**
     * MIME used for MediaStore inserts. The platform's own mapping is preferred because
     * MediaProvider validates DISPLAY_NAME's extension against it (e.g. it knows "audio/x-wav",
     * not "audio/wav"); a mismatch makes it rename the file or reject the insert.
     */
    private fun mediaStoreMimeType(extension: String): String {
        val ext = extension.trim().removePrefix(".").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.takeIf { it.isNotBlank() }
            ?: getMimeTypeForExtension(ext)
    }

    private fun buildFileName(baseName: String, targetExt: String, counter: Int, keepOriginalName: Boolean): String {
        return when {
            counter == 0 && keepOriginalName -> "$baseName.$targetExt"
            counter == 0 -> "${baseName}_morph_converted.$targetExt"
            keepOriginalName -> "${baseName}($counter).$targetExt"
            else -> "${baseName}_morph_converted_$counter.$targetExt"
        }
    }

    private fun sanitizedBaseName(originalName: String): String =
        originalName.substringBeforeLast('.', "file")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // Sanitize forbidden filename characters
            .trim()
            .ifBlank { "file" }

    private fun appendSuffix(fileName: String, suffix: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) "$base$suffix.$ext" else "$base$suffix"
    }

    /**
     * Copies a validated cache file into Downloads/Morph and returns the absolute path of the
     * saved file, or null when saving failed. The temp file is deleted only on success.
     */
    fun finalizeOutput(context: Context, tempFile: File, originalName: String, targetFormat: String): String? {
        if (!tempFile.exists() || !tempFile.isFile || tempFile.length() <= 0L) {
            Log.e(TAG, "Temp output missing or empty: ${tempFile.absolutePath}")
            return null
        }

        val targetExt = targetFormat.trim().removePrefix(".").lowercase()
        val baseName = sanitizedBaseName(originalName)
        val keepOriginalName = MorphPrefs(context).keepOriginalName

        val savedPath = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, tempFile, baseName, targetExt, keepOriginalName)
            } else {
                saveViaLegacyFile(context, tempFile, baseName, targetExt, keepOriginalName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save output for $originalName", e)
            null
        }

        if (savedPath != null) {
            tempFile.delete()
        }
        return savedPath
    }

    // --- API 29+ : MediaStore.Downloads ---

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(
        context: Context,
        tempFile: File,
        baseName: String,
        targetExt: String,
        keepOriginalName: Boolean
    ): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val mimeType = mediaStoreMimeType(targetExt)
        val morphDir = morphDirectoryPath()

        // 1) Pick a name that is free both in MediaStore (our own rows) and on disk (visible files)
        var counter = 0
        var displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        while (counter < MAX_NAME_ATTEMPTS &&
            (mediaStoreNameExists(resolver, collection, displayName) || File(morphDir, displayName).exists())
        ) {
            counter++
            displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        }

        // 2) Insert; a leftover file from a previous install is invisible to us and can make
        //    insert() return null (or throw), so retry once with a unique timestamp suffix.
        var uri = insertPending(resolver, collection, displayName, mimeType)
        if (uri == null) {
            displayName = appendSuffix(displayName, "_${System.currentTimeMillis()}")
            Log.w(TAG, "MediaStore insert returned null, retrying as $displayName")
            uri = insertPending(resolver, collection, displayName, mimeType)
        }
        if (uri == null) {
            Log.e(TAG, "MediaStore insert failed for $displayName ($mimeType)")
            return null
        }

        // 3) Copy bytes; publish on success, remove the pending row on any failure
        var copied = false
        try {
            val out = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Cannot open output stream for $uri")
            out.use { output ->
                FileInputStream(tempFile).use { input -> input.copyTo(output) }
            }
            copied = true
        } catch (e: Exception) {
            Log.e(TAG, "Copy to MediaStore failed for $displayName", e)
        } finally {
            try {
                if (copied) {
                    val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, publish, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize pending MediaStore row $uri", e)
                copied = false
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            }
        }
        if (!copied) return null

        // MediaProvider may adjust the name (e.g. "name (1).ext"); read back the real one
        val finalName = queryDisplayName(resolver, uri) ?: displayName
        return File(morphDir, finalName).absolutePath
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertPending(resolver: ContentResolver, collection: Uri, displayName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, MEDIASTORE_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            // Some OEM builds throw IllegalStateException on name collisions instead of returning null
            Log.w(TAG, "MediaStore insert threw for $displayName", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreNameExists(resolver: ContentResolver, collection: Uri, displayName: String): Boolean {
        return try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(displayName, MEDIASTORE_RELATIVE_PATH),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore duplicate query failed", e)
            false
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- API 26–28 : direct File I/O (needs WRITE_EXTERNAL_STORAGE) ---

    private fun saveViaLegacyFile(
        context: Context,
        tempFile: File,
        baseName: String,
        targetExt: String,
        keepOriginalName: Boolean
    ): String? {
        val morphDir = morphDirectoryPath()
        if (!morphDir.exists() && !morphDir.mkdirs()) {
            Log.e(TAG, "Cannot create ${morphDir.absolutePath} (storage permission granted?)")
            return null
        }

        var counter = 0
        var destFile = File(morphDir, buildFileName(baseName, targetExt, counter, keepOriginalName))
        while (destFile.exists() && counter < MAX_NAME_ATTEMPTS) {
            counter++
            destFile = File(morphDir, buildFileName(baseName, targetExt, counter, keepOriginalName))
        }
        if (destFile.exists()) {
            destFile = File(morphDir, appendSuffix(destFile.name, "_${System.currentTimeMillis()}"))
        }

        return try {
            tempFile.copyTo(destFile, overwrite = false)
            if (destFile.length() != tempFile.length()) {
                throw IOException("Size mismatch after copy")
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(getMimeTypeForExtension(targetExt)),
                null
            )
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Legacy copy failed for ${destFile.absolutePath}", e)
            try { destFile.delete() } catch (_: Exception) {}
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
