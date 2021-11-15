package com.test.externalcamera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraFilter
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var faceAnalyzer: FaceAnalyzer
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "ExternalCamera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(
                applicationContext,
                "CameraX is only supported on SDK version >= 21. Current SDK version is " + Build.VERSION.SDK_INT,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select camera. Nougat에서 확인했을 때, Cam이 External이 아니고 Front로 올라오는 것을 확인.
            val cameraSelector = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                val cameraFilter = getExternalCameraFilter()
                CameraSelector.Builder().addCameraFilter(cameraFilter).build()
            }

            // High-accuracy landmark detection and face classification
            val highAccuracyOpts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()

            // Real-time contour detection
            val realTimeOpts = FaceDetectorOptions.Builder()
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()

            val defaultOpts = FaceDetectorOptions.Builder().setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build()
            val detector = FaceDetection.getClient(defaultOpts)

            val faceAnalysis = ImageAnalysis.Builder()
                .build()
                .also {
                    faceAnalyzer = FaceAnalyzer(detector, graphic_overlay, cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    it.setAnalyzer(cameraExecutor, faceAnalyzer)
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, faceAnalysis)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getExternalCameraFilter():
            CameraFilter = Camera2CameraFilter.createCameraFilter { cameraInfos: List<Camera2CameraInfo> ->
        val resultList = mutableListOf<Camera2CameraInfo>()
        for (cameraInfo in cameraInfos) {
            if (cameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)?.equals(CameraMetadata.LENS_FACING_EXTERNAL) == true) {
                resultList.add(cameraInfo)
            }
        }
        resultList
    }
}