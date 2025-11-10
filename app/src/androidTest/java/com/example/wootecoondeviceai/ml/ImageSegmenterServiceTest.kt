package com.example.wootecoondeviceai.ml

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class ImageSegmenterServiceTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun analyzeBitmap_personImage_returnsValidResultWithPersonClass() {
        val (testImage, segmenterService, result) = setupTestPrerequisites()

        val foundClasses = result.foundClasses
        println("발견된 클래스: $foundClasses")
        assertTrue(
            "발견된 클래스 목록($foundClasses)에 'person'이 포함되어야 합니다.",
            foundClasses.contains("person")
        )
    }

    @Test
    fun removePixels_withPersonKeyword_makesPersonPixelsTransparent() {
        val (testImage, segmenterService, result) = setupTestPrerequisites()
        val keyword = "person"
        val newBitmap = segmenterService.removePixels(testImage, result, keyword)

        assertNotNull("결과 비트맵이 null이면 안 됩니다.", newBitmap)
        assertNotSame("결과 비트맵은 원본 비트맵과 다른 객체여야 합니다.", testImage, newBitmap)

        val targetIndex = PERSON_CLASS_INDEX
        checkPixelTransparency(result, newBitmap!!, targetIndex)
    }

    private fun setupTestPrerequisites(): Triple<Bitmap, ImageSegmenterService, SegmentationResult> {
        val testImage = testContext.assets.open(TEST_IMAGE_NAME).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("테스트 이미지 로드 실패", testImage)

        val segmenterService = ImageSegmenterService(appContext, TEST_MODEL_NAME)
        val result = segmenterService.analyzeBitmap(testImage!!)

        assertNotNull("분석 결과(result)가 null이면 안 됩니다.", result)
        assertTrue("마스크 너비(width)가 0보다 커야 합니다.", result!!.maskWidth > 0)
        assertTrue("마스크 높이(height)가 0보다 커야 합니다.", result.maskHeight > 0)

        return Triple(testImage, segmenterService, result)
    }

    private fun checkPixelTransparency(
        result: SegmentationResult,
        bitmap: Bitmap,
        targetIndex: Int
    ) {
        val maskBuffer = result.maskBuffer

        val xScale = result.maskWidth.toFloat() / bitmap.width
        val yScale = result.maskHeight.toFloat() / bitmap.height

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val isTestFinished = checkAndAssertPixel(
                    x, y, bitmap,
                    maskBuffer, result.maskWidth,
                    xScale, yScale, targetIndex
                )

                if (isTestFinished) return
            }
        }
        fail("마스크에서 'person' (인덱스 $targetIndex) 픽셀을 찾지 못했습니다,")
    }

    private fun checkAndAssertPixel(
        x: Int, y: Int, bitmap: Bitmap,
        maskBuffer: ByteBuffer, maskWidth: Int,
        xScale: Float, yScale: Float, targetIndex: Int
    ): Boolean {
        val maskX = ((x + ROUNDING_OFFSET) * xScale).toInt()
        val maskY = ((y + ROUNDING_OFFSET) * yScale).toInt()

        val safeMaskX = maskX.coerceIn(0, maskWidth - 1)
        val safeMaskY = maskY.coerceIn(0, (maskBuffer.capacity() / maskWidth) - 1)

        val index = safeMaskY * maskWidth + safeMaskX
        val classIndex = maskBuffer.get(index).toUByte().toInt()

        if (classIndex != targetIndex) {
            return false
        }

        val pixelColor = bitmap.getPixel(x, y)
        val alpha = Color.alpha(pixelColor)

        assertEquals(
            "(${x}, ${y} 픽셀(person)이 투명(Alpha=0)해야 합니다.",
            0,
            alpha
        )
        return true
    }

    companion object {
        private const val TEST_MODEL_NAME = "deeplabv3_with_metadata.tflite"
        private const val TEST_IMAGE_NAME = "person_test.jpg"
        private const val PERSON_CLASS_INDEX = 15
        private const val ROUNDING_OFFSET = 0.5f
    }
}