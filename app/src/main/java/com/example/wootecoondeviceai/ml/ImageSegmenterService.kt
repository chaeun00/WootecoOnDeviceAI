package com.example.wootecoondeviceai.ml

import android.content.Context
import android.graphics.Bitmap
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
            foundIndices.add(maskBuffer.get().toInt())
        }

        return foundIndices
    }

    private fun mapIndicesToLabels(indices: Set<Int>): List<String> {
        return indices
            .mapNotNull { PASCAL_VOC_LABELS.getOrNull(it) }
            .distinct()
    }

    companion object {
        private const val DEFAULT_MODEL_NAME = "deeplabv3_with_metadata.tflite"

        private val PASCAL_VOC_LABELS = listOf(
            "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
            "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike",
            "person", "pottedplant", "sheep", "sofa", "train", "tvmonitor"
        )
    }
}