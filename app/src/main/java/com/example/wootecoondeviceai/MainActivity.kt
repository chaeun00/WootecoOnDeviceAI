package com.example.wootecoondeviceai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wootecoondeviceai.databinding.ActivityMainBinding
import com.example.wootecoondeviceai.ml.Classifier
import com.example.wootecoondeviceai.ml.ClassificationResult
import com.example.wootecoondeviceai.ml.ImageSegmenterService
import com.example.wootecoondeviceai.ml.InpainterService
import com.example.wootecoondeviceai.ml.SegmentationResult
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val classifier by lazy { Classifier(this) }
    private val segmenterService by lazy { ImageSegmenterService(this) }
    private val inpainterService by lazy { InpainterService() }

    private var originalBitmap: Bitmap? = null
    private var segmentationResult: SegmentationResult? = null
    private var maskedBitmap: Bitmap? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview())  { bitmap ->
            bitmap?.let { analyzeImage(it) } ?: Log.w(TAG, "사진 비트맵이 null임")
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->

            if (isGranted) {
                Log.d(TAG, "카메라 권한 허용됨")
                takePictureLauncher.launch(null)
            } else {
                Log.d(TAG, "카메라 권한 거부됨")
                Toast.makeText(this, getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d(TAG, "갤러리에서 Uri 받음: $uri")
                val bitmap = uriToBitmap(uri)
                bitmap?.let { analyzeImage(it) } ?: Log.w(TAG, "Uri -> Bitmap 변환 실패")
            } else {
                Log.w(TAG, "갤러리에서 Uri가 null임 (선택 취소)")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.btnGallery.setOnClickListener {
            Log.d(TAG, "갤러리 버튼 클릭됨")
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnInpaint.setOnClickListener {
            performInpainting()
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "권한이 이미 있음. 카메라 실행.")
                takePictureLauncher.launch(null)
            }
            else -> {
                Log.d(TAG, "권한 없음. 권한 요청.")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        originalBitmap = bitmap
        maskedBitmap = null

        binding.ivPreview.setImageBitmap(bitmap)
        binding.tvResult.text = getString(R.string.text_analyzing)
        binding.chipGroupSegments.removeAllViews()

        lifecycleScope.launch {
            val classificationResultText = runClassification(bitmap)
            val segmentationResult = runSegmentation(bitmap)
            this@MainActivity.segmentationResult = segmentationResult

            updateUiWithResults(classificationResultText, segmentationResult?.foundClasses)
        }
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
                getString(R.string.text_analysis_failed)
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

    private fun updateUiWithResults(
        classificationText: String,
        foundClasses: List<String>?
    ) {
        binding.tvResult.text = classificationText
        updateSegmentationChips(foundClasses)
    }

    private fun updateSegmentationChips(foundClasses: List<String>?) {
        binding.chipGroupSegments.removeAllViews()

        val keywords = foundClasses?.filter { it != ImageSegmenterService.LABEL_BACKGROUND }
        if (keywords.isNullOrEmpty()) return

        for (keyword in keywords) {
            var chip = createChip(keyword)
            binding.chipGroupSegments.addView(chip)
        }
    }

    private fun createChip(keyword: String): Chip {
        return Chip(this).apply {
            text = keyword
            isClickable = true
            setOnClickListener {
                performPixelRemoval(keyword)
            }
        }
    }

    private fun performPixelRemoval(keyword: String) {
        val bitmapToProcess = originalBitmap
        val result = segmentationResult
        if (bitmapToProcess == null || result == null) {
            Log.w(TAG, "픽셀 제거 실패: 원본 비트맵 또는 분석 결과가 없습니다.")
            return
        }

        binding.tvResult.text = getString(R.string.text_analyzing)
        launchPixelRemovalJob(bitmapToProcess, result, keyword)
    }

    private fun launchPixelRemovalJob(
        bitmap: Bitmap,
        result: SegmentationResult,
        keyword: String
    ) {
        lifecycleScope.launch {
            val removedBitmap = withContext(Dispatchers.Default) {
                segmenterService.removePixels(bitmap, result, keyword)
            }

            maskedBitmap = removedBitmap

            binding.ivPreview.setImageBitmap(removedBitmap)
            val completeMessage = getString(R.string.text_removal_complete, keyword)
            binding.tvResult.text = completeMessage
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            decodeBitmapFromUri(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Uri를 Bitmap으로 변환 실패: ${e.message}", e)
            null
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, false)
        } else {
            @Suppress("DEPRECATION")
            val originalBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            return if (originalBitmap.config != Bitmap.Config.ARGB_8888) {
                originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                originalBitmap
            }
        }
    }

    private fun performInpainting() {
        val bitmapToInpaint = maskedBitmap ?: originalBitmap

        if (bitmapToInpaint == null) {
            Log.w(TAG, "Inpainting 실패: 원본 이미지가 없습니다.")
            return
        }

        binding.tvResult.text = getString(R.string.text_analyzing)
        launchInpaintingJob(bitmapToInpaint)
    }

    private fun launchInpaintingJob(bitmap: Bitmap) {
        lifecycleScope.launch {
            val inpaintedBitmap = withContext(Dispatchers.Default) {
                inpainterService.fillHoles(bitmap)
            }

            binding.ivPreview.setImageBitmap(inpaintedBitmap)
            binding.tvResult.text = getString(R.string.text_inpaint_complete)

            maskedBitmap = null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}