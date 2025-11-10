package com.example.wootecoondeviceai.ml

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageSegmenterServiceTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun analyzeBitmap_personImage_returnsValidResultWithPersonClass() {
        val testImage = testContext.assets.open(TEST_IMAGE_NAME).use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("테스트 이미지 로드 실패", testImage)

        val segmenterService = ImageSegmenterService(appContext, TEST_MODEL_NAME)
        val result = segmenterService.analyzeBitmap(testImage!!)
        assertNotNull(
            "분석 결과(result)가 null이면 안 됩니다.", result)
        assertTrue("마스크 너비(width)가 0보다 커야 합니다.", result!!.maskWidth > 0)
        assertTrue("마스크 높이(height)가 0보다 커야 합니다.", result.maskHeight > 0)

        val foundClasses = result.foundClasses
        println("발견된 클래스: $foundClasses")
        assertTrue(
            "발견된 클래스 목록($foundClasses)에 'person'이 포함되어야 합니다.",
            foundClasses.contains("person")
        )
    }

    companion object {
        private const val TEST_MODEL_NAME = "deeplabv3_with_metadata.tflite"
        private const val TEST_IMAGE_NAME = "person_test.jpg"
    }
}