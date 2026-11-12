package com.morph.app.core

enum class CapabilityStatus {
    SUPPORTED,
    CONDITIONAL,
    UNSUPPORTED,
    NOT_TESTED
}

data class ConversionCapability(
    val sourceExt: String,
    val targetExt: String,
    val status: CapabilityStatus,
    val isLosslessStreamCopyCapable: Boolean = false,
    val note: String = ""
)

object ConversionCapabilityManager {

    // Conversion test matrix mapping based on Morph Pre-Sprint 4 specifications
    private val capabilities = mapOf(
        // Video pairs
        ("mp4" to "mkv") to ConversionCapability("mp4", "mkv", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "Stream copy or transcode"),
        ("mp4" to "mov") to ConversionCapability("mp4", "mov", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "Stream copy or transcode"),
        ("mp4" to "webm") to ConversionCapability("mp4", "webm", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "VP9/Opus transcoding required"),
        ("mp4" to "gif") to ConversionCapability("mp4", "gif", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Video to animated GIF"),
        ("mp4" to "mp4") to ConversionCapability("mp4", "mp4", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Compression / bitrate scaling"),
        ("mkv" to "mp4") to ConversionCapability("mkv", "mp4", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "Stream copy when codecs compatible, transcode fallback"),
        ("mkv" to "mov") to ConversionCapability("mkv", "mov", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "Stream copy when codecs compatible"),
        ("mkv" to "webm") to ConversionCapability("mkv", "webm", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "VP9/Opus transcoding required"),
        ("mov" to "mp4") to ConversionCapability("mov", "mp4", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "Stream copy when H.264/AAC"),
        ("webm" to "mp4") to ConversionCapability("webm", "mp4", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "H.264/AAC transcoding required"),
        ("avi" to "mp4") to ConversionCapability("avi", "mp4", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Transcoding required"),

        // Video to Audio extract
        ("mp4" to "mp3") to ConversionCapability("mp4", "mp3", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Audio extraction"),
        ("mp4" to "m4a") to ConversionCapability("mp4", "m4a", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = true, note = "AAC stream copy to M4A"),
        ("mp4" to "wav") to ConversionCapability("mp4", "wav", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "PCM audio extraction"),
        ("mkv" to "mp3") to ConversionCapability("mkv", "mp3", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Audio extraction"),
        ("mkv" to "wav") to ConversionCapability("mkv", "wav", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "PCM audio extraction"),
        ("mp4" to "ogg") to ConversionCapability("mp4", "ogg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Audio extraction to OGG"),
        ("mp4" to "opus") to ConversionCapability("mp4", "opus", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Audio extraction to OPUS"),
        
        // Audio pairs
        ("flac" to "wav") to ConversionCapability("flac", "wav", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless decode to PCM WAV (file size increases)"),
        ("flac" to "mp3") to ConversionCapability("flac", "mp3", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossy transcode to MP3"),
        ("flac" to "aac") to ConversionCapability("flac", "aac", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossy transcode to AAC"),
        ("wav" to "flac") to ConversionCapability("wav", "flac", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless PCM compression to FLAC"),
        ("wav" to "mp3") to ConversionCapability("wav", "mp3", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "PCM to MP3 encode"),
        ("mp3" to "wav") to ConversionCapability("mp3", "wav", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "MP3 decode to PCM WAV"),
        ("m4a" to "mp3") to ConversionCapability("m4a", "mp3", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AAC/ALAC to MP3 transcode"),
        ("aac" to "wav") to ConversionCapability("aac", "wav", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AAC decode to PCM WAV"),
        ("mp3" to "m4a") to ConversionCapability("mp3", "m4a", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "MP3 to AAC encode"),
        ("wav" to "aac") to ConversionCapability("wav", "aac", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "WAV to AAC encode"),

        // Raster Image pairs
        ("png" to "jpg") to ConversionCapability("png", "jpg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Composites transparency on white"),
        ("png" to "jpeg") to ConversionCapability("png", "jpeg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Composites transparency on white"),
        ("png" to "webp") to ConversionCapability("png", "webp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless/lossy WEBP with alpha"),
        ("png" to "bmp") to ConversionCapability("png", "bmp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Native 24-bit/32-bit BMP"),
        ("png" to "pdf") to ConversionCapability("png", "pdf", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Image to single-page PDF"),
        ("jpg" to "png") to ConversionCapability("jpg", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless PNG output"),
        ("jpeg" to "png") to ConversionCapability("jpeg", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless PNG output"),
        ("jpg" to "webp") to ConversionCapability("jpg", "webp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "WEBP encode"),
        ("jpeg" to "webp") to ConversionCapability("jpeg", "webp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "WEBP encode"),
        ("jpg" to "bmp") to ConversionCapability("jpg", "bmp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Native BMP output"),
        ("jpeg" to "bmp") to ConversionCapability("jpeg", "bmp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Native BMP output"),
        ("jpg" to "pdf") to ConversionCapability("jpg", "pdf", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Image to single-page PDF"),
        ("jpeg" to "pdf") to ConversionCapability("jpeg", "pdf", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Image to single-page PDF"),
        ("webp" to "png") to ConversionCapability("webp", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Lossless PNG output"),
        ("webp" to "jpg") to ConversionCapability("webp", "jpg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Composites transparency on white"),
        ("webp" to "jpeg") to ConversionCapability("webp", "jpeg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Composites transparency on white"),
        ("webp" to "bmp") to ConversionCapability("webp", "bmp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Native BMP output"),
        ("bmp" to "png") to ConversionCapability("bmp", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "BMP to PNG"),
        ("bmp" to "jpg") to ConversionCapability("bmp", "jpg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "BMP to JPG"),

        // Vector SVG pairs
        ("svg" to "png") to ConversionCapability("svg", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AndroidSVG to PNG (retains alpha)"),
        ("svg" to "jpg") to ConversionCapability("svg", "jpg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AndroidSVG to JPG (white background)"),
        ("svg" to "jpeg") to ConversionCapability("svg", "jpeg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AndroidSVG to JPG (white background)"),
        ("svg" to "webp") to ConversionCapability("svg", "webp", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "AndroidSVG to WEBP"),

        // PDF pairs
        ("pdf" to "jpg") to ConversionCapability("pdf", "jpg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Multi-page render to JPG"),
        ("pdf" to "jpeg") to ConversionCapability("pdf", "jpeg", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Multi-page render to JPG"),
        ("pdf" to "png") to ConversionCapability("pdf", "png", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Multi-page render to PNG"),
        ("pdf" to "pdf") to ConversionCapability("pdf", "pdf", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "Smart PDF page compression"),

        // E-Book pairs
        ("epub" to "txt") to ConversionCapability("epub", "txt", CapabilityStatus.SUPPORTED, isLosslessStreamCopyCapable = false, note = "OPF spine ordered extraction"),
        ("txt" to "epub") to ConversionCapability("txt", "epub", CapabilityStatus.UNSUPPORTED, isLosslessStreamCopyCapable = false, note = "Not implemented in Pre-Sprint 4")
    )

    fun getCapability(sourceExt: String, targetExt: String): ConversionCapability {
        val src = sourceExt.trim().removePrefix(".").lowercase()
        val tgt = targetExt.trim().removePrefix(".").lowercase()
        return capabilities[src to tgt] ?: ConversionCapability(
            sourceExt = src,
            targetExt = tgt,
            status = CapabilityStatus.UNSUPPORTED,
            note = "Pair not supported"
        )
    }

    fun isSupported(sourceExt: String, targetExt: String): Boolean {
        val cap = getCapability(sourceExt, targetExt)
        return cap.status == CapabilityStatus.SUPPORTED || cap.status == CapabilityStatus.CONDITIONAL
    }

    fun isLosslessCompatible(sourceExt: String, targetExt: String, mimeType: String? = null): Boolean {
        val src = sourceExt.trim().removePrefix(".").lowercase()
        val tgt = targetExt.trim().removePrefix(".").lowercase()

        val cap = getCapability(src, tgt)
        if (cap.isLosslessStreamCopyCapable) return true

        val videoContainers = setOf("mp4", "mkv", "mov", "m4v", "ts")
        val isVideo = FileCategoryManager.isVideo(src) || (mimeType?.startsWith("video/") == true)
        if (isVideo && src in videoContainers && tgt in videoContainers && src != tgt) {
            return true
        }

        // M4A to M4A re-container
        if (src == "m4a" && tgt == "m4a") return true

        return false
    }

    fun getTargetFormats(fileName: String, mimeType: String? = null): List<String> {
        val originalExt = fileName.substringAfterLast('.', "").trim().lowercase()
        val category = FileCategoryManager.getCategory(originalExt, mimeType)

        val targets = mutableListOf<String>()

        when {
            originalExt == "svg" -> {
                targets.addAll(listOf("png", "jpg", "webp"))
            }
            originalExt == "epub" -> {
                targets.addAll(listOf("txt"))
            }
            originalExt == "pdf" || mimeType == "application/pdf" -> {
                targets.addAll(listOf("pdf", "jpg", "png"))
            }
            category == FileCategory.VIDEO -> {
                targets.addAll(listOf("mp4", "mkv", "mov", "webm", "gif", "avi", "mp3", "m4a", "wav", "ogg", "opus"))
            }
            category == FileCategory.AUDIO -> {
                targets.addAll(listOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus"))
            }
            category == FileCategory.IMAGE -> {
                targets.addAll(listOf("png", "jpg", "webp", "bmp", "pdf"))
            }
            category == FileCategory.DOCUMENT -> {
                if (originalExt == "txt") {
                    targets.addAll(listOf("pdf"))
                } else {
                    targets.addAll(listOf("pdf", "txt"))
                }
            }
            else -> {
                targets.addAll(listOf("mp4", "png", "mp3", "pdf"))
            }
        }

        // Allow compression in same format where supported (video, raster image, pdf)
        val isCompressible = category == FileCategory.VIDEO ||
                (category == FileCategory.IMAGE && originalExt in setOf("jpg", "jpeg", "png", "webp")) ||
                originalExt == "pdf"

        if (isCompressible) {
            if (originalExt.isNotEmpty() && !targets.contains(originalExt)) {
                targets.add(0, originalExt)
            }
        } else {
            targets.remove(originalExt)
        }

        return targets.distinct()
    }
}
