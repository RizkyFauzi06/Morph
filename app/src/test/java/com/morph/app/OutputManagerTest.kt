package com.morph.app

import com.morph.app.core.ConversionValidator
import com.morph.app.core.OutputManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCleanTempFiles() {
        val f1 = tempFolder.newFile("temp1.tmp")
        val f2 = tempFolder.newFile("temp2.tmp")
        assertTrue(f1.exists())
        assertTrue(f2.exists())

        OutputManager.cleanTempFiles(f1, f2)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
    }

    @Test
    fun testValidationForEmptyAndNonExistentFiles() {
        val nonExistent = tempFolder.root.resolve("does_not_exist.mp4")
        val result1 = ConversionValidator.validateFile(nonExistent, "mp4")
        assertTrue(result1 is ConversionValidator.ValidationResult.Invalid)

        val emptyFile = tempFolder.newFile("empty.txt")
        val result2 = ConversionValidator.validateFile(emptyFile, "txt")
        assertTrue(result2 is ConversionValidator.ValidationResult.Invalid)

        val validTxt = tempFolder.newFile("valid.txt")
        validTxt.writeText("Morph conversion output text content.")
        val result3 = ConversionValidator.validateFile(validTxt, "txt")
        assertTrue(result3 is ConversionValidator.ValidationResult.Valid)
    }
}
