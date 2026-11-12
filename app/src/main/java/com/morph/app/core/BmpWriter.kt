package com.morph.app.core

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object BmpWriter {

    fun writeBitmapAsBmp(bitmap: Bitmap, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            writeBitmapAsBmp(bitmap, fos)
        }
    }

    fun writeBitmapAsBmp(bitmap: Bitmap, outputStream: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height

        val rowWidthBytes = width * 3
        val padding = (4 - (rowWidthBytes % 4)) % 4
        val paddedRowBytes = rowWidthBytes + padding
        val imageSize = paddedRowBytes * height
        val fileSize = 54 + imageSize

        val header = ByteArray(54)
        // 1. Signature 'BM'
        header[0] = 0x42.toByte() // 'B'
        header[1] = 0x4D.toByte() // 'M'

        // 2. File size in bytes
        writeInt32(header, 2, fileSize)

        // 3. Reserved
        writeInt32(header, 6, 0)

        // 4. Data offset (54 bytes)
        writeInt32(header, 10, 54)

        // 5. DIB Header size (40 bytes - BITMAPINFOHEADER)
        writeInt32(header, 14, 40)

        // 6. Width and Height
        writeInt32(header, 18, width)
        writeInt32(header, 22, height) // positive: bottom-up

        // 7. Planes (1)
        writeInt16(header, 26, 1)

        // 8. Bits per pixel (24)
        writeInt16(header, 28, 24)

        // 9. Compression (0 = BI_RGB, uncompressed)
        writeInt32(header, 30, 0)

        // 10. Image data size
        writeInt32(header, 34, imageSize)

        // 11. X & Y pixels per meter (2835 ppm ~ 72 DPI)
        writeInt32(header, 38, 2835)
        writeInt32(header, 42, 2835)

        // 12. Colors in palette
        writeInt32(header, 46, 0)

        // 13. Important colors
        writeInt32(header, 50, 0)

        outputStream.write(header)

        // Pixels: BMP is stored bottom-to-top, BGR order
        val pixels = IntArray(width)
        val rowBuffer = ByteArray(paddedRowBytes)

        for (y in height - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            var offset = 0
            for (x in 0 until width) {
                val color = pixels[x]
                val b = (color and 0xFF).toByte()
                val g = ((color shr 8) and 0xFF).toByte()
                val r = ((color shr 16) and 0xFF).toByte()
                rowBuffer[offset++] = b
                rowBuffer[offset++] = g
                rowBuffer[offset++] = r
            }
            // Fill padding bytes with 0
            while (offset < paddedRowBytes) {
                rowBuffer[offset++] = 0
            }
            outputStream.write(rowBuffer)
        }
        outputStream.flush()
    }

    private fun writeInt32(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
