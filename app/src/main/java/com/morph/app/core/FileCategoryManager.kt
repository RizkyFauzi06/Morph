
package com.morph.app.core

import androidx.compose.ui.graphics.Color

enum class FileCategory(val displayName: String) {
    IMAGE("Gambar"),
    VIDEO("Video"),
    AUDIO("Audio"),
    PDF("PDF"),
    EBOOK("E-book"),
    DOCUMENT("Dokumen"),
    SPREADSHEET("Spreadsheet"),
    ARCHIVE("Arsip"),
    CODE("Kode"),
    OTHER("Lainnya")
}

data class FileCategoryColors(
    val background: Color,
    val icon: Color
)

object FileCategoryManager {

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "bmp", "gif", "svg", "heic", "heif", "ico", "tiff", "tif"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "mov", "webm", "avi", "flv", "wmv", "3gp", "m4v", "ts", "ogv"
    )

    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "aiff", "alac", "amr"
    )

    private val pdfExtensions = setOf("pdf")
    private val ebookExtensions = setOf("epub")

    private val documentExtensions = setOf(
        "txt", "doc", "docx", "ppt", "pptx", "rtf", "odt", "odp"
    )

    private val spreadsheetExtensions = setOf(
        "csv", "xls", "xlsx", "ods"
    )

    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz"
    )

    private val codeExtensions = setOf(
        "c", "h", "cc", "cpp", "cxx", "hpp", "hxx",
        "cs", "kt", "kts", "java",
        "py", "js", "jsx", "ts", "tsx",
        "html", "css", "scss",
        "json", "xml", "yaml", "yml",
        "sh", "bash", "sql",
        "go", "rs", "swift", "dart",
        "php", "rb", "ipynb"
    )

    fun getCategory(extension: String, mimeType: String? = null): FileCategory {
        val ext = extension.trim().removePrefix(".").lowercase()
        val mime = mimeType?.trim()?.lowercase() ?: ""

        // Direct extension check
        when {
            ext in imageExtensions -> return FileCategory.IMAGE
            ext in videoExtensions -> return FileCategory.VIDEO
            ext in audioExtensions -> return FileCategory.AUDIO
            ext in pdfExtensions -> return FileCategory.PDF
            ext in ebookExtensions -> return FileCategory.EBOOK
            ext in spreadsheetExtensions -> return FileCategory.SPREADSHEET
            ext in documentExtensions -> return FileCategory.DOCUMENT
            ext in archiveExtensions -> return FileCategory.ARCHIVE
            ext in codeExtensions -> return FileCategory.CODE
        }

        // MIME type fallback
        when {
            mime.startsWith("image/") -> return FileCategory.IMAGE
            mime.startsWith("video/") -> return FileCategory.VIDEO
            mime.startsWith("audio/") -> return FileCategory.AUDIO
            mime == "application/pdf" -> return FileCategory.PDF
            mime.contains("epub") -> return FileCategory.EBOOK
            mime.contains("spreadsheet") || mime.contains("sheet") ||
                mime == "text/csv" -> return FileCategory.SPREADSHEET
            mime.contains("javascript") || mime.contains("json") ||
                mime.contains("xml") || mime.contains("x-python") -> return FileCategory.CODE
            mime.contains("zip") || mime.contains("tar") ||
                mime.contains("compressed") || mime.contains("archive") -> return FileCategory.ARCHIVE
            mime.contains("document") || mime.contains("presentation") ||
                mime == "text/plain" || mime.contains("rtf") -> return FileCategory.DOCUMENT
        }

        return FileCategory.OTHER
    }

    fun getCategoryColors(category: FileCategory, isDarkTheme: Boolean): FileCategoryColors {
        return when (category) {
            FileCategory.IMAGE -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF59451B), Color(0xFFFFD580))
            } else {
                FileCategoryColors(Color(0xFFFFF0CE), Color(0xFFC17A00))
            }

            FileCategory.VIDEO -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF1B3658), Color(0xFF91BCFF))
            } else {
                FileCategoryColors(Color(0xFFDCEBFF), Color(0xFF2865B5))
            }

            FileCategory.AUDIO -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF542D1D), Color(0xFFFFB58A))
            } else {
                FileCategoryColors(Color(0xFFFFE6D5), Color(0xFFC45A20))
            }

            FileCategory.PDF -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF552323), Color(0xFFFFA0A0))
            } else {
                FileCategoryColors(Color(0xFFFFE1E1), Color(0xFFC73535))
            }

            FileCategory.EBOOK -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF382653), Color(0xFFCBAAFF))
            } else {
                FileCategoryColors(Color(0xFFEDE3FF), Color(0xFF7545B8))
            }

            FileCategory.DOCUMENT -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF194D32), Color(0xFF8BE0A8))
            } else {
                FileCategoryColors(Color(0xFFDDF5E5), Color(0xFF21834A))
            }

            FileCategory.SPREADSHEET -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF17463F), Color(0xFF83DCCE))
            } else {
                FileCategoryColors(Color(0xFFD8F3EE), Color(0xFF167C70))
            }

            FileCategory.ARCHIVE -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF303B4A), Color(0xFFB3C0D1))
            } else {
                FileCategoryColors(Color(0xFFE5E9F0), Color(0xFF53657C))
            }

            FileCategory.CODE -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF252F5B), Color(0xFFAAB8FF))
            } else {
                FileCategoryColors(Color(0xFFE2E8FF), Color(0xFF455BB5))
            }

            FileCategory.OTHER -> if (isDarkTheme) {
                FileCategoryColors(Color(0xFF303B4A), Color(0xFFB3C0D1))
            } else {
                FileCategoryColors(Color(0xFFE5E9F0), Color(0xFF53657C))
            }
        }
    }

    // Compatibility helpers
    fun getCategoryColor(category: FileCategory): Color =
        getCategoryColors(category, isDarkTheme = false).icon

    fun getExtensionBadgeColor(extension: String): Color =
        getCategoryColor(getCategory(extension))

    fun getExtensionColors(extension: String, isDarkTheme: Boolean): FileCategoryColors =
        getCategoryColors(getCategory(extension), isDarkTheme)

    fun isAudio(extension: String): Boolean =
        getCategory(extension) == FileCategory.AUDIO

    fun isVideo(extension: String): Boolean =
        getCategory(extension) == FileCategory.VIDEO

    fun isImage(extension: String): Boolean =
        getCategory(extension) == FileCategory.IMAGE

    fun isDocument(extension: String): Boolean =
        getCategory(extension) == FileCategory.DOCUMENT

    fun isPdf(extension: String): Boolean =
        getCategory(extension) == FileCategory.PDF

    fun isEbook(extension: String): Boolean =
        getCategory(extension) == FileCategory.EBOOK

    fun isSpreadsheet(extension: String): Boolean =
        getCategory(extension) == FileCategory.SPREADSHEET

    fun isArchive(extension: String): Boolean =
        getCategory(extension) == FileCategory.ARCHIVE

    fun isCode(extension: String): Boolean =
        getCategory(extension) == FileCategory.CODE
}
