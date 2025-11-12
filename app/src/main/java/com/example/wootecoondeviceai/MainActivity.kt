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
import com.example.wootecoondeviceai.ml.ImageSegmenterService
import com.example.wootecoondeviceai.ml.MLRepository
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val mlRepository by lazy { MLRepository(this) }
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

        binding.btnCamera.setOnClickListener { checkCameraPermissionAndLaunch() }
        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnInpaint.setOnClickListener { performInpainting() }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        binding.ivPreview.setImageBitmap(bitmap)
        binding.tvResult.text = getString(R.string.text_analyzing)
        binding.chipGroupSegments.removeAllViews()

        lifecycleScope.launch {
            val result = mlRepository.analyzeImage(bitmap)

            updateUiWithResults(result.classificationText, result.segmentationResult?.foundClasses)
        }
    }

    private fun performPixelRemoval(keyword: String) {
        binding.tvResult.text = getString(R.string.text_analyzing)

        lifecycleScope.launch {
            val removedBitmap = mlRepository.removePixels(keyword)

            binding.ivPreview.setImageBitmap(removedBitmap)
            val completeMessage = getString(R.string.text_removal_complete, keyword)
            binding.tvResult.text = completeMessage
        }
    }

    private fun performInpainting() {
        binding.tvResult.text = getString(R.string.text_analyzing)

        lifecycleScope.launch {
            val inpaintedBitmap = mlRepository.fillHoles()

            binding.ivPreview.setImageBitmap(inpaintedBitmap)
            binding.tvResult.text = getString(R.string.text_inpaint_complete)
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
            val chip = createChip(keyword)
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

    companion object {
        private const val TAG = "MainActivity"
    }
}