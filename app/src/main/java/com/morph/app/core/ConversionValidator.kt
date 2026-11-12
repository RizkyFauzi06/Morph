package com.morph.app.core

import android.graphics.BitmapFactory
import java.io.File

object ConversionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validateFile(file: File, expectedExtension: String): ValidationResult {
        if (!file.exists()) {
            return ValidationResult.Invalid("Berkas output tidak ditemukan di sistem penyimpanan.")
        }

        if (!file.isFile) {
            return ValidationResult.Invalid("Path output bukan merupakan berkas reguler.")
        }

        val size = file.length()
        if (size <= 0) {
            return ValidationResult.Invalid("Berkas output kosong (ukuran 0 byte).")
        }

        val ext = expectedExtension.trim().removePrefix(".").lowercase()

        // Raster image validation
        if (ext in listOf("png", "jpg", "jpeg", "webp", "bmp")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return ValidationResult.Invalid("Format gambar output rusak atau tidak dapat diuraikan.")
            }
        }

        // Text / Document validation
        if (ext == "txt") {
            try {
                if (file.readText(Charsets.UTF_8).isBlank()) {
                    return ValidationResult.Invalid("Berkas teks hasil konversi kosong.")
                }
            } catch (e: Exception) {
                return ValidationResult.Invalid("Berkas teks tidak dapat dibaca: ${e.message}")
            }
        }

        return ValidationResult.Valid
    }
}
