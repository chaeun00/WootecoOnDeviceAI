package com.example.wootecoondeviceai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
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

    companion object {
        private const val TAG = "MainActivity"
    }
}