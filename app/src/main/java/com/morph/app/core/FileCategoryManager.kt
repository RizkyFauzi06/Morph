package com.morph.app.core

import androidx.compose.ui.graphics.Color
import com.morph.app.ui.theme.MorphIndigo
import com.morph.app.ui.theme.MorphPink
import com.morph.app.ui.theme.MorphViolet

enum class FileCategory(val displayName: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    IMAGE("Gambar"),
    DOCUMENT("Dokumen"),
    ARCHIVE("Arsip"),
    OTHER("Lainnya")
}

object FileCategoryManager {

    private val videoExtensions = setOf(
        "mp4", "mkv", "mov", "webm", "avi", "flv", "wmv", "3gp", "m4v", "ts", "ogv"
    )

    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "aiff", "alac", "amr"
    )

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "svg", "heic", "heif", "ico", "tiff", "tif"
    )

    private val documentExtensions = setOf(
        "pdf", "txt", "epub", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods", "odp"
    )

    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz"
    )

    fun getCategory(extension: String, mimeType: String? = null): FileCategory {
        val ext = extension.trim().removePrefix(".").lowercase()
        val mime = mimeType?.trim()?.lowercase() ?: ""

        // 1. Direct extension check (primary)
        when {
            ext in videoExtensions -> return FileCategory.VIDEO
            ext in audioExtensions -> return FileCategory.AUDIO
            ext in imageExtensions -> return FileCategory.IMAGE
            ext in documentExtensions -> return FileCategory.DOCUMENT
            ext in archiveExtensions -> return FileCategory.ARCHIVE
        }

        // 2. MIME type fallback
        when {
            mime.startsWith("video/") -> return FileCategory.VIDEO
            mime.startsWith("audio/") -> return FileCategory.AUDIO
            mime.startsWith("image/") -> return FileCategory.IMAGE
            mime == "application/pdf" || mime == "text/plain" || mime.contains("epub") ||
                    mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") ||
                    mime == "text/csv" -> return FileCategory.DOCUMENT
            mime.contains("zip") || mime.contains("tar") || mime.contains("compressed") ||
                    mime.contains("archive") -> return FileCategory.ARCHIVE
        }

        return FileCategory.OTHER
    }

    fun isAudio(extension: String): Boolean = getCategory(extension) == FileCategory.AUDIO
    fun isVideo(extension: String): Boolean = getCategory(extension) == FileCategory.VIDEO
    fun isImage(extension: String): Boolean = getCategory(extension) == FileCategory.IMAGE
    fun isDocument(extension: String): Boolean = getCategory(extension) == FileCategory.DOCUMENT

    fun getCategoryColor(category: FileCategory): Color {
        return when (category) {
            FileCategory.VIDEO -> MorphPink
            FileCategory.AUDIO -> MorphViolet
            FileCategory.IMAGE -> Color(0xFF10B981) // Emerald green
            FileCategory.DOCUMENT -> Color(0xFFEF4444) // Red/Rose
            FileCategory.ARCHIVE -> Color(0xFFF59E0B) // Amber
            FileCategory.OTHER -> MorphIndigo
        }
    }

    fun getExtensionBadgeColor(extension: String): Color {
        return getCategoryColor(getCategory(extension))
    }
}
