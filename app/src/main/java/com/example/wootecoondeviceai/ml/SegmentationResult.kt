package com.example.wootecoondeviceai.ml

import java.nio.ByteBuffer

data class SegmentationResult(
    val maskBuffer: ByteBuffer,
    val maskWidth: Int,
    val maskHeight: Int,
    val foundClasses: List<String>
)
