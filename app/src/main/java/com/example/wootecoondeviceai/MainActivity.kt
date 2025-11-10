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
import com.example.wootecoondeviceai.databinding.ActivityMainBinding
import com.example.wootecoondeviceai.ml.Classifier

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val classifier by lazy { Classifier(this) }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview())  { bitmap ->
            analyzeImage(bitmap)
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
                analyzeImage(bitmap)
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

    private fun analyzeImage(bitmap: Bitmap?) {
        if (bitmap == null) {
            Log.w(TAG, "사진 비트맵이 null임")
            return
        }

        binding.ivPreview.setImageBitmap(bitmap)
        binding.tvResult.text = getString(R.string.text_analyzing)

        val resultText = runClassification(bitmap)
        binding.tvResult.text = resultText
    }

    private fun runClassification(bitmap: Bitmap): String {
        try {
            val result = classifier.classify(bitmap)
            return result.joinToString("\n") {
                "${it.label} (${(it.confidence * 100).toInt()}%)"
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "이미지 분석 실패: ${e.message}", e)
            return getString(R.string.text_analysis_failed)
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