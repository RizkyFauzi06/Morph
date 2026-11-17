package com.morph.app.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.morph.app.MorphPrefs
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/** One converted file in history: a plain File (Downloads/Morph) or a SAF document (file == null). */
data class MorphOutputFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val file: File? = null
) {
    val extension: String get() = name.substringAfterLast('.', "")
}

object OutputManager {

    private const val TAG = "OutputManager"
    private const val MORPH_FOLDER = "Morph"
    private const val MAX_NAME_ATTEMPTS = 500
    private const val MAX_SAF_HISTORY = 500

    /** Relative path used by MediaStore (API 29+). Trailing slash matches what MediaStore stores. */
    private val MEDIASTORE_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$MORPH_FOLDER/"

    /** Path of Downloads/Morph without touching the filesystem. */
    private fun morphDirectoryPath(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), MORPH_FOLDER)

    fun createTempFile(context: Context, prefix: String, extension: String): File {
        // The extension comes from a file name another app chose; it ends up inside the FFmpeg
        // command string, so anything but [a-z0-9] (quotes, spaces) could inject arguments.
        val ext = extension.lowercase().replace(Regex("[^a-z0-9]"), "").take(10)
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
     * Copies a validated cache file into the output folder (custom SAF folder when set and still
     * accessible, otherwise Downloads/Morph) and returns the saved file's path or document URI,
     * or null when saving failed. The temp file is deleted only on success.
     *
     * @param sourceName input file name used for the auto-subfolder (PDF pages pass the PDF name)
     */
    fun finalizeOutput(
        context: Context,
        tempFile: File,
        originalName: String,
        targetFormat: String,
        sourceName: String = originalName
    ): String? {
        if (!tempFile.exists() || !tempFile.isFile || tempFile.length() <= 0L) {
            Log.e(TAG, "Temp output missing or empty: ${tempFile.absolutePath}")
            return null
        }

        val targetExt = targetFormat.trim().removePrefix(".").lowercase()
        val baseName = sanitizedBaseName(originalName)
        val prefs = MorphPrefs(context)
        val keepOriginalName = prefs.keepOriginalName
        val subfolder = if (prefs.autoSubfolderPerFile) sanitizedBaseName(sourceName) else null

        // PBI 5.1: custom SAF folder first; any failure there falls back to Downloads/Morph
        val treeUri = validCustomTreeUri(context, prefs)
        val safPath = if (treeUri != null) {
            try {
                saveViaSaf(context, prefs, treeUri, tempFile, subfolder, baseName, targetExt, keepOriginalName)
            } catch (e: Exception) {
                Log.e(TAG, "SAF save failed for $originalName, falling back to Downloads/Morph", e)
                null
            }
        } else null

        val savedPath = safPath ?: try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, tempFile, subfolder, baseName, targetExt, keepOriginalName)
            } else {
                saveViaLegacyFile(context, tempFile, subfolder, baseName, targetExt, keepOriginalName)
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
        subfolder: String?,
        baseName: String,
        targetExt: String,
        keepOriginalName: Boolean
    ): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val mimeType = mediaStoreMimeType(targetExt)
        val morphDir = if (subfolder != null) File(morphDirectoryPath(), subfolder) else morphDirectoryPath()
        val relativePath = if (subfolder != null) "$MEDIASTORE_RELATIVE_PATH$subfolder/" else MEDIASTORE_RELATIVE_PATH

        // 1) Pick a name that is free both in MediaStore (our own rows) and on disk (visible files)
        var counter = 0
        var displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        while (counter < MAX_NAME_ATTEMPTS &&
            (mediaStoreNameExists(resolver, collection, displayName, relativePath) || File(morphDir, displayName).exists())
        ) {
            counter++
            displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        }

        // 2) Insert; a leftover file from a previous install is invisible to us and can make
        //    insert() return null (or throw), so retry once with a unique timestamp suffix.
        var uri = insertPending(resolver, collection, displayName, mimeType, relativePath)
        if (uri == null) {
            displayName = appendSuffix(displayName, "_${System.currentTimeMillis()}")
            Log.w(TAG, "MediaStore insert returned null, retrying as $displayName")
            uri = insertPending(resolver, collection, displayName, mimeType, relativePath)
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
    private fun insertPending(
        resolver: ContentResolver,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
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
    private fun mediaStoreNameExists(
        resolver: ContentResolver,
        collection: Uri,
        displayName: String,
        relativePath: String
    ): Boolean {
        return try {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(displayName, relativePath),
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
        subfolder: String?,
        baseName: String,
        targetExt: String,
        keepOriginalName: Boolean
    ): String? {
        val morphDir = if (subfolder != null) File(morphDirectoryPath(), subfolder) else morphDirectoryPath()
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

    // --- PBI 5.1 : custom output folder via Storage Access Framework ---

    /**
     * Returns the custom tree URI only while Morph still holds read+write access to it and the
     * folder still exists. A revoked or deleted folder is reset so the UI shows the default again.
     */
    fun validCustomTreeUri(context: Context, prefs: MorphPrefs = MorphPrefs(context)): Uri? {
        val raw = prefs.customOutputTreeUri ?: return null
        val tree = try { Uri.parse(raw) } catch (_: Exception) { null }
        val granted = tree != null && context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        val alive = granted && queryDocument(context.contentResolver, rootDocumentUri(tree)) != null
        if (!alive) {
            Log.w(TAG, "Custom output folder no longer accessible, resetting to Downloads/Morph: $raw")
            prefs.customOutputTreeUri = null
            return null
        }
        return tree
    }

    /** Human-readable label for a tree URI, e.g. "Documents/Morph" or "1A2B-3C4D: Music". */
    fun describeTreeUri(treeUri: Uri): String {
        return try {
            val id = DocumentsContract.getTreeDocumentId(treeUri)
            val volume = id.substringBefore(':', "")
            val path = id.substringAfter(':', id)
            when {
                volume.equals("primary", ignoreCase = true) -> path.ifBlank { "/" }
                volume.isNotEmpty() -> "$volume: ${path.ifBlank { "/" }}"
                else -> id
            }
        } catch (_: Exception) {
            treeUri.toString()
        }
    }

    private fun rootDocumentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private data class SafChild(val documentId: String, val name: String, val isDirectory: Boolean)

    private fun listSafChildren(resolver: ContentResolver, tree: Uri, parentDocId: String): List<SafChild> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            val out = ArrayList<SafChild>(c.count)
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out += SafChild(id, c.getString(1) ?: "", c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
            out
        } ?: emptyList()
    }

    private fun saveViaSaf(
        context: Context,
        prefs: MorphPrefs,
        tree: Uri,
        tempFile: File,
        subfolder: String?,
        baseName: String,
        targetExt: String,
        keepOriginalName: Boolean
    ): String? {
        val resolver = context.contentResolver
        var parentId = DocumentsContract.getTreeDocumentId(tree)

        if (subfolder != null) {
            val existing = listSafChildren(resolver, tree, parentId)
                .firstOrNull { it.isDirectory && it.name == subfolder }
            parentId = existing?.documentId
                ?: DocumentsContract.createDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    subfolder
                )?.let { DocumentsContract.getDocumentId(it) }
                ?: return null
        }

        val taken = listSafChildren(resolver, tree, parentId).map { it.name }.toHashSet()
        var counter = 0
        var displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        while (displayName in taken && counter < MAX_NAME_ATTEMPTS) {
            counter++
            displayName = buildFileName(baseName, targetExt, counter, keepOriginalName)
        }
        if (displayName in taken) displayName = appendSuffix(displayName, "_${System.currentTimeMillis()}")

        val docUri = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
            mediaStoreMimeType(targetExt),
            displayName
        ) ?: return null

        var copied = false
        try {
            val out = resolver.openOutputStream(docUri, "w") ?: throw IOException("Cannot open $docUri")
            out.use { output -> FileInputStream(tempFile).use { it.copyTo(output) } }
            copied = true
        } finally {
            if (!copied) {
                try { DocumentsContract.deleteDocument(resolver, docUri) } catch (_: Exception) {}
            }
        }

        val recorded = prefs.safOutputUris + docUri.toString()
        // shortcut: trims by set order (roughly insertion), not by date; fine for a history list
        prefs.safOutputUris = if (recorded.size > MAX_SAF_HISTORY) recorded.toList().takeLast(MAX_SAF_HISTORY).toSet() else recorded
        return docUri.toString()
    }

    private fun queryDocument(resolver: ContentResolver, uri: Uri): MorphOutputFile? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return try {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0) ?: return@use null
                MorphOutputFile(
                    uri = uri,
                    name = name,
                    size = if (c.isNull(1)) 0L else c.getLong(1),
                    lastModified = if (c.isNull(2)) 0L else c.getLong(2)
                )
            }
        } catch (_: Exception) {
            null // revoked permission or deleted document
        }
    }

    // --- File history: Downloads/Morph (+ one level of subfolders) and recorded SAF outputs ---

    fun listOutputFiles(context: Context): List<MorphOutputFile> {
        val result = mutableListOf<MorphOutputFile>()

        morphDirectoryPath().listFiles()?.forEach { entry ->
            val files = if (entry.isDirectory) entry.listFiles().orEmpty().toList() else listOf(entry)
            files.filter { it.isFile }.forEach { f ->
                result += MorphOutputFile(Uri.fromFile(f), f.name, f.length(), f.lastModified(), f)
            }
        }

        val prefs = MorphPrefs(context)
        val recorded = prefs.safOutputUris
        if (recorded.isNotEmpty()) {
            // Same name+size+mtime (seconds) = same file seen twice when the custom folder IS Downloads/Morph
            val fileKeys = result.map { Triple(it.name, it.size, it.lastModified / 1000) }.toHashSet()
            val alive = mutableSetOf<String>()
            recorded.forEach { raw ->
                val doc = queryDocument(context.contentResolver, Uri.parse(raw)) ?: return@forEach
                alive += raw
                // The custom folder may itself be Downloads/Morph; don't list the same file twice
                if (Triple(doc.name, doc.size, doc.lastModified / 1000) !in fileKeys) result += doc
            }
            if (alive.size != recorded.size) prefs.safOutputUris = alive
        }

        return result.sortedByDescending { it.lastModified }
    }

    fun deleteOutputFile(context: Context, item: MorphOutputFile): Boolean {
        val file = item.file
        if (file == null) {
            val deleted = try {
                DocumentsContract.deleteDocument(context.contentResolver, item.uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete SAF document ${item.uri}", e)
                false
            }
            if (deleted) {
                val prefs = MorphPrefs(context)
                prefs.safOutputUris = prefs.safOutputUris - item.uri.toString()
            }
            return deleted
        }

        if (file.delete()) return true
        // Android 10 legacy mode has no WRITE permission, but Morph still owns its MediaStore row
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val parent = file.parentFile ?: return false
                val relative = "${Environment.DIRECTORY_DOWNLOADS}/${parent.relativeTo(downloads).invariantSeparatorsPath}/"
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(file.name, relative)
                ) > 0
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore delete failed for ${file.absolutePath}", e)
                false
            }
        }
        return false
    }

    // Matches createTempFile names only: "<prefix>_<8 hex>[.<ext>]"
    private val tempNameRegex = Regex("""^[A-Za-z0-9_]+_[0-9a-f]{8}(\.[a-z0-9]{1,10})?$""")

    /**
     * Deletes cache files left by conversions that never reached their finally block
     * (app killed mid-job). Call only while no conversion is queued or running.
     */
    fun cleanStaleTempFiles(context: Context) {
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile && tempNameRegex.matches(f.name)) f.delete()
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
