package com.morph.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.FileOutputStream

// Vector rasterization powered by AndroidSVG (Apache License 2.0)
// Copyright 2013-2019 Paul LeBeau, Cave Rock Software Ltd.

object SvgConverter {

    fun convertSvg(
        context: Context,
        inputUri: Uri,
        targetExtension: String,
        qualityPreset: Int,
        onProgress: (step: String, percent: Int) -> Unit
    ): File {
        val strings = AppStrings.get()
        val targetExt = targetExtension.trim().removePrefix(".").lowercase()
        onProgress(strings.engineLoadingSvg, 20)

        val svg = context.contentResolver.openInputStream(inputUri)?.use { input ->
            SVG.getFromInputStream(input)
        } ?: throw IllegalStateException(strings.engineErrReadSvg)

        onProgress(strings.engineCalculatingSvgDim, 40)

        // Robust dimension resolution: documentWidth/Height vs viewBox vs default
        var rawWidth = svg.documentWidth
        var rawHeight = svg.documentHeight

        if (rawWidth <= 0 || rawHeight <= 0) {
            val viewBox = svg.documentViewBox
            if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                rawWidth = viewBox.width()
                rawHeight = viewBox.height()
            } else {
                rawWidth = 1024f
                rawHeight = 1024f
            }
        }

        // Clamp minimum size
        if (rawWidth <= 0) rawWidth = 1024f
        if (rawHeight <= 0) rawHeight = 1024f

        val scaleFactor = when (qualityPreset) {
            0, 3 -> 2.0f // High / Lossless scale
            1    -> 1.5f // Medium
            2    -> 1.0f // Normal / Low
            else -> 1.5f
        }

        // One scale for both sides so a clamp never stretches the image (6000x1000 -> 8192x1365)
        val scale = minOf(scaleFactor, 8192f / maxOf(rawWidth, rawHeight))
        val finalWidth = (rawWidth * scale).toInt().coerceAtLeast(16)
        val finalHeight = (rawHeight * scale).toInt().coerceAtLeast(16)

        onProgress(strings.engineRenderingSvg, 65)

        val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // JPG does not support alpha channel: paint white background to prevent black artifacts
        val isJpg = targetExt == "jpg" || targetExt == "jpeg"
        if (isJpg) {
            canvas.drawColor(Color.WHITE)
        } else {
            // PNG & WEBP maintain transparency with clean buffer
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        }

        // Render SVG to canvas with explicit viewport RectF matching the bitmap dimensions
        val destRect = android.graphics.RectF(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat())
        svg.renderToCanvas(canvas, destRect)

        onProgress(strings.engineCompressingSvgOut, 85)

        val tempOut = OutputManager.createTempFile(context, "svg_out", targetExt)
        val quality = when (qualityPreset) {
            0, 3 -> 100
            1    -> 85
            2    -> 65
            else -> 90
        }

        FileOutputStream(tempOut).use { fos ->
            bitmap.compress(com.morph.app.MorphEngine.compressFormatFor(targetExt, quality), quality, fos)
            fos.flush()
        }
        bitmap.recycle()

        return tempOut
    }
}
