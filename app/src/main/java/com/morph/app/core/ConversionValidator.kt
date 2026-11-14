package com.morph.app.core

import android.graphics.BitmapFactory
import java.io.File

object ConversionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validateFile(file: File, expectedExtension: String): ValidationResult {
        val strings = AppStrings.get()
        if (!file.exists()) {
            return ValidationResult.Invalid(strings.validatorFileNotFound)
        }

        if (!file.isFile) {
            return ValidationResult.Invalid(strings.validatorNotRegularFile)
        }

        val size = file.length()
        if (size <= 0) {
            return ValidationResult.Invalid(strings.validatorOutputEmpty)
        }

        val ext = expectedExtension.trim().removePrefix(".").lowercase()

        // Raster image validation
        if (ext in listOf("png", "jpg", "jpeg", "webp", "bmp")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return ValidationResult.Invalid(strings.validatorCorruptImage)
            }
        }

        // Text / Document validation
        if (ext == "txt") {
            try {
                if (file.readText(Charsets.UTF_8).isBlank()) {
                    return ValidationResult.Invalid(strings.validatorEmptyTxt)
                }
            } catch (e: Exception) {
                return ValidationResult.Invalid(strings.validatorUnreadableTxt(e.message ?: ""))
            }
        }

        return ValidationResult.Valid
    }
}
