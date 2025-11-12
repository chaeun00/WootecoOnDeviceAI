package com.example.wootecoondeviceai.ml

data class AnalysisResult(
    val classificationText: String,
    val segmentationResult: SegmentationResult?
)
