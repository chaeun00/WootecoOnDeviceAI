package com.example.wootecoondeviceai.ml

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertThrows

@RunWith(AndroidJUnit4::class)
class ClassifierTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun classify_catImage_returnsCatLabel() {
        val testImage = testContext.assets.open("TestCat01.webp").use {
            BitmapFactory.decodeStream(it)
        }

        assertNotNull(
            "테스트 이미지(TestCat01.webp) 로드 실패. /androidTest/assets 폴더를 확인하세요.",
            testImage
        )

        val classifier = Classifier(appContext, TEST_MODEL_NAME)

        val results = classifier.classify(testImage!!)

        println("분류 결과: $results")
        val hasCatLabel = results.any { it.label.contains("cat", ignoreCase = true) }
        assertTrue("분류 결과($results)에 'cat'이 포함되어야 합니다.", hasCatLabel)
    }

    @Test
    fun classify_nullBitmap_throwsIllegalArgumentException() {
        var classifier = Classifier(appContext, TEST_MODEL_NAME)

        assertThrows(IllegalArgumentException::class.java) {
            classifier.classify(null)
        }
    }

    companion object {
        private const val TEST_MODEL_NAME = "mobilenet_v1_1.0_224_quant.tflite"
    }
}