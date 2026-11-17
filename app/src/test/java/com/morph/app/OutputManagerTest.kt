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
    fun staleTempCleanupOnlyDeletesMorphWorkFiles() {
        val tmp = tempFolder.newFolder("morph_tmp")
        val cache = tempFolder.newFolder("cache")
        val leftovers = listOf(
            java.io.File(tmp, "in_media_ab12cd34.mp4"),
            java.io.File(tmp, "x264_es_0f1e2d3c.h264"),
            java.io.File(cache, "pdf_page_3_deadbeef.png") // pre-1.1.0 location
        )
        val keep = listOf(
            java.io.File(cache, "notes.txt"),                 // not ours
            java.io.File(cache, "in_media_ABCDEFGH.mp4"),     // not an 8-hex id
            java.io.File(tmp, "My Video_ab12cd34.mp4")        // space: never produced by createTempFile
        )
        (leftovers + keep).forEach { it.writeText("x") }
        java.io.File(cache, "pipes").mkdirs() // FFmpegKit's pipe folder must survive

        assertEquals(3, OutputManager.deleteStaleTempFiles(listOf(tmp, cache)))
        leftovers.forEach { assertFalse(it.name, it.exists()) }
        keep.forEach { assertTrue(it.name, it.exists()) }
        assertTrue(java.io.File(cache, "pipes").isDirectory)
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
