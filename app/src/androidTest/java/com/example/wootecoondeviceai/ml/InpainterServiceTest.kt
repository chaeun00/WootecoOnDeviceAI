package com.example.wootecoondeviceai.ml

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InpainterServiceTest {
    private val inpainterService = InpainterService()

    @Test
    fun fillHoles_replacesTransparentPixel_withAverageColor() {
        val testBitmap = createTestBitmapWithHole(3, 3, Color.RED)
        val expectedColor = Color.RED
        val resultBitmap = inpainterService.fillHoles(testBitmap)

        assertNotNull("결과 비트맵은 null이면 안 됩니다.", resultBitmap)
        assertNotSame("새 비트맵을 반환해야 합니다.", testBitmap, resultBitmap)

        val resultPixel = resultBitmap!!.getPixel(1, 1)
        assertEquals("중앙 픽셀이 주변 평균색(RED)이어야 합니다.", expectedColor, resultPixel)
        assertEquals(255, Color.alpha(resultPixel))
    }

    private fun createTestBitmapWithHole(width: Int, height: Int, bgColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, bgColor)
            }
        }

        bitmap.setPixel(width / 2, height / 2, Color.TRANSPARENT)
        return bitmap
    }
}