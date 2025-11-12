package com.example.wootecoondeviceai.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.wootecoondeviceai.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLRepository(
    private val context: Context
) {
    private val classifier by lazy { Classifier(context) }
    private val segmenterService by lazy { ImageSegmenterService(context) }
    private val inpainterService by lazy { InpainterService() }

    private var originalBitmap: Bitmap? = null
    private var segmentationResult: SegmentationResult? = null
    private var maskedBitmap: Bitmap? = null

    suspend fun analyzeImage(bitmap: Bitmap): AnalysisResult {
        originalBitmap = bitmap
        maskedBitmap = null

        val classificationText = runClassification(bitmap)
        val segmentationResult = runSegmentation(bitmap)
        this.segmentationResult = segmentationResult

        return AnalysisResult(classificationText, segmentationResult)
    }

    suspend fun removePixels(keyword: String): Bitmap? {
        val bitmapToProcess = originalBitmap
        val result = segmentationResult
        if (bitmapToProcess == null || result == null) {
            Log.w(TAG, "픽셀 제거 실패: 원본 비트맵 또는 분석 결과가 없습니다.")
            return null
        }

        val removedBitmap = withContext(Dispatchers.Default) {
            segmenterService.removePixels(bitmapToProcess, result, keyword)
        }
        maskedBitmap = removedBitmap
        return removedBitmap
    }

    suspend fun fillHoles(): Bitmap? {
        val bitmapToInpaint = maskedBitmap ?: originalBitmap
        if (bitmapToInpaint == null) {
            Log.w(TAG, "Inpainting 실패: 원본 이미지가 없습니다.")
            return null
        }

        val inpaintedBitmap = withContext(Dispatchers.Default) {
            inpainterService.fillHoles(bitmapToInpaint)
        }
        maskedBitmap = null
        return inpaintedBitmap
    }

    private suspend fun runClassification(bitmap: Bitmap): String {
        return withContext(Dispatchers.Default) {
            try {
                val result = classifier.classify(bitmap)
                result.joinToString("\n") {
                    "${it.label} (${(it.confidence * 100).toInt()}%)"
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "이미지 분석 실패: ${e.message}", e)
                context.getString(R.string.text_analysis_failed)
            }
        }
    }

    private suspend fun runSegmentation(bitmap: Bitmap): SegmentationResult? {
        return withContext(Dispatchers.Default) {
            try {
                segmenterService.analyzeBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "세그멘테이션 실패: ${e.message}", e)
                null
            }
        }
    }

    companion object {
        private  const val TAG = "MLRepository"
    }
}