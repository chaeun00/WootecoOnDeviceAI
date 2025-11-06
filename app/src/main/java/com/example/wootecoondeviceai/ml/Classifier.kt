package com.example.wootecoondeviceai.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

class Classifier(
    private val context: Context,
    private val modelName: String = DEFAULT_MODEL_NAME
) {
    private val labels: List<String> by lazy { loadLabels() }
    private val imageClassifier: ImageClassifier? by lazy { setupClassifier() }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABELS_FILE_NAME)
                .bufferedReader(Charsets.UTF_8)
                .readLines()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun setupClassifier(): ImageClassifier? {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(MAX_RESULTS)
            .build()

        return try {
            ImageClassifier.createFromFileAndOptions(
                context,
                modelName,
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun classify(bitmap: Bitmap?): List<ClassificationResult> {
        if (bitmap == null) {
            throw IllegalArgumentException(ERROR_NULL_BITMAP)
        }

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val classificationResults = imageClassifier?.classify(tensorImage)

        return parseResults(classificationResults)
    }

    private fun parseResults(
        classificationResults: List<Classifications>?
    ): List<ClassificationResult> {
        return classificationResults?.flatMap { classification ->
            classification.categories.map { category ->
                val index = category.label.toIntOrNull()
                val realLabel = index?.let { labels.getOrNull(it) } ?: UNKNOWN_LABEL

                ClassificationResult(
                    label = realLabel,
                    confidence = category.score
                )
            }
        } ?: emptyList()
    }

    companion object {
        private const val DEFAULT_MODEL_NAME = "mobilenet_v1_1.0_224_quant.tflite"
        private const val LABELS_FILE_NAME = "labels.txt"
        private const val MAX_RESULTS = 3
        private const val ERROR_NULL_BITMAP = "Input Bitmap cannot be null"
        private const val UNKNOWN_LABEL = "Unknown"
    }
}