package com.example.wootecoondeviceai.ml

import android.graphics.Bitmap
import android.graphics.Color

class InpainterService
{
    fun fillHoles(bitmap: Bitmap): Bitmap? {
        return try {
            createInpaintedBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createInpaintedBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val currentPixels = IntArray(width * height)
        bitmap.getPixels(currentPixels, 0, width, 0, 0, width, height)

        val finalPixels = runInpaintingLoop(currentPixels, width, height)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(finalPixels, 0, width, 0, 0, width, height)
        }
    }


    private fun runInpaintingLoop(
        initialPixels: IntArray, width: Int, height: Int
    ): IntArray {
        var readPixels = initialPixels

        repeat(MAX_ITERATIONS) {
            val writePixels = readPixels.clone()
            processPixelArrayInpainting(readPixels, writePixels, width, height)

            if (readPixels.contentEquals(writePixels)) {
                return writePixels
            }

            readPixels = writePixels
        }
        return readPixels
    }

    private fun processPixelArrayInpainting(
        readPixels: IntArray,
        writePixels: IntArray,
        width: Int, height: Int
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                processSinglePixelInpaint(
                    readPixels, writePixels, x, y, width, height
                )
            }
        }
    }

    private fun processSinglePixelInpaint(
        readPixels: IntArray, writePixels: IntArray,
        x: Int, y: Int, width: Int, height: Int
    ) {
        val index = y * width + x
        val pixelColor = readPixels[index]
        if (Color.alpha(pixelColor) != 0) {
            return
        }

        val avgColor = calculateAverageColor(readPixels, x, y, width, height)
        if (avgColor != null) {
            writePixels[index] = avgColor
        }
    }

    private fun calculateAverageColor(
        pixels: IntArray,
        x: Int, y: Int, width: Int, height: Int
    ): Int? {
        val sum = accumulateNeighborColors(pixels, x, y, width, height)

        return createColorFromAverage(sum)
    }

    private fun accumulateNeighborColors(
        pixels: IntArray,
        x: Int, y: Int, width: Int, height: Int
    ): NeighborColorSum {
        var sum = NeighborColorSum(0, 0, 0, 0)

        for (i in -1..1) {
            for (j in -1..1) {
                sum = accumulateSingleNeighbor(
                    sum, pixels, x + j, y + i, width, height
                )
            }
        }

        return sum
    }

    private fun accumulateSingleNeighbor(
        currentSum: NeighborColorSum, pixels: IntArray,
        x: Int, y: Int, width: Int, height: Int
    ): NeighborColorSum {
        val neighborColor = getValidNeighborColor(pixels, x, y, width, height)

        if (neighborColor == null) {
            return currentSum
        }

        return NeighborColorSum(
            totalRed = currentSum.totalRed + Color.red(neighborColor),
            totalGreen = currentSum.totalGreen + Color.green(neighborColor),
            totalBlue = currentSum.totalBlue + Color.blue(neighborColor),
            neighborCount = currentSum.neighborCount + 1
        )
    }

    private fun createColorFromAverage(sum: NeighborColorSum): Int? {
        return if (sum.neighborCount > 0) {
            Color.rgb(
                sum.totalRed / sum.neighborCount,
                sum.totalBlue / sum.neighborCount,
                sum.totalGreen / sum.neighborCount
            )
        } else {
            null
        }
    }

    private fun getValidNeighborColor(
        pixels: IntArray,
        x: Int, y: Int, width: Int, height: Int
    ): Int? {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null
        }

        val pixelColor = pixels[y * width + x]
        if (Color.alpha(pixelColor) == 0) {
            return null
        }

        return pixelColor
    }

    companion object {
        private const val MAX_ITERATIONS = 50
    }
}