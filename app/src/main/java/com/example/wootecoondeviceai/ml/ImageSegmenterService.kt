package com.example.wootecoondeviceai.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter
import org.tensorflow.lite.task.vision.segmenter.OutputType
import org.tensorflow.lite.task.vision.segmenter.Segmentation
import java.nio.ByteBuffer

class ImageSegmenterService(
    private val context: Context,
    private val modelName: String = DEFAULT_MODEL_NAME
) {
    private val imageSegmenter: ImageSegmenter? by lazy {
        setupSegmenter()
    }

    private fun setupSegmenter(): ImageSegmenter? {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setOutputType(OutputType.CATEGORY_MASK)
            .build()

        return try {
            ImageSegmenter.createFromFileAndOptions(
                context,
                modelName,
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun analyzeBitmap(bitmap: Bitmap?): SegmentationResult? {
        if (bitmap == null) {
            return null
        }

        if (imageSegmenter == null) {
            return null
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results: List<Segmentation>? = imageSegmenter?.segment(tensorImage)
        return parseResults(results)
    }

    private fun parseResults(results: List<Segmentation>?): SegmentationResult? {
        if (results.isNullOrEmpty() || results[0].masks.isEmpty()) {
            return null
        }

        val segmentation = results[0]
        val mask = segmentation.masks[0]

        val foundIndices = extractIndicesFromMask(mask.buffer)
        val foundClassNames = mapIndicesToLabels(foundIndices)

        return SegmentationResult(
            mask.buffer, mask.width, mask.height, foundClassNames
        )
    }

    private fun extractIndicesFromMask(maskBuffer: ByteBuffer): Set<Int> {
        val foundIndices = mutableSetOf<Int>()

        maskBuffer.rewind()
        while (maskBuffer.hasRemaining()) {
            foundIndices.add(maskBuffer.get().toUByte().toInt())
        }

        return foundIndices
    }

    private fun mapIndicesToLabels(indices: Set<Int>): List<String> {
        return indices
            .mapNotNull { PASCAL_VOC_LABELS.getOrNull(it) }
            .distinct()
    }

    fun removePixels(
        bitmap: Bitmap,
        result: SegmentationResult,
        keyword: String
    ): Bitmap? {
        try {
            val targetIndex = findTargetIndex(keyword) ?: return null

            return createMaskedBitmap(
                bitmap,
                result.maskBuffer.rewind() as ByteBuffer,
                result.maskWidth,
                result.maskHeight,
                targetIndex
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun findTargetIndex(keyword: String): Int? {
        val index = PASCAL_VOC_LABELS.indexOf(keyword.lowercase())
        return if (index != -1) index else null
    }

    private fun createMaskedBitmap(
        originalBitmap: Bitmap,
        maskBuffer: ByteBuffer,
        maskWidth: Int,
        maskHeight: Int,
        targetIndex: Int
    ): Bitmap {
        val newBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = originalBitmap.width
        val height = originalBitmap.height
        val pixels = IntArray(width * height)
        newBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        applyMaskToPixelArray(
            pixels, width, height,
            maskBuffer, maskWidth, maskHeight,
            targetIndex
        )

        newBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return newBitmap
    }

    private fun applyMaskToPixelArray(
        pixels: IntArray,
        width: Int, height: Int,
        maskBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int,
        targetIndex: Int
    ) {
        val xScale = maskWidth.toFloat() / width
        val yScale = maskHeight.toFloat() / height

        for (y in 0 until height) {
            for (x in 0 until width) {
                processSinglePixel(
                    pixels, x, y, width,
                    maskBuffer, maskWidth,
                    xScale, yScale, targetIndex
                )
            }
        }
    }

    private fun processSinglePixel(
        pixels: IntArray,
        x: Int, y: Int, width: Int,
        maskBuffer: ByteBuffer, maskWidth: Int,
        xScale: Float, yScale: Float, targetIndex: Int
    ) {
        val classIndex = getMaskIndexAt(
            x, y, xScale, yScale,
            maskBuffer, maskWidth
        )

        if (classIndex == targetIndex) {
            pixels[y * width + x] = Color.TRANSPARENT
        }
    }

    private fun getMaskIndexAt(
        x: Int, y: Int,
        xScale: Float, yScale: Float,
        maskBuffer: ByteBuffer, maskWidth: Int
    ): Int {
        val maskX = ((x + ROUNDING_OFFSET) * xScale).toInt()
        val maskY = ((y + ROUNDING_OFFSET) * yScale).toInt()

        val safeMaskX = maskX.coerceIn(0, maskWidth - 1)
        val safeMaskY = maskY.coerceIn(0, (maskBuffer.capacity() / maskWidth) - 1)

        val index = safeMaskY * maskWidth + safeMaskX

        return maskBuffer.get(index).toUByte().toInt()
    }

    companion object {
        private const val DEFAULT_MODEL_NAME = "deeplabv3_with_metadata.tflite"

        private val PASCAL_VOC_LABELS = listOf(
            "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
            "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike",
            "person", "pottedplant", "sheep", "sofa", "train", "tvmonitor"
        )

        private const val ROUNDING_OFFSET = 0.5f
    }
}