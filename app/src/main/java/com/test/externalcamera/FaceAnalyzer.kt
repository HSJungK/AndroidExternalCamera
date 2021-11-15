package com.test.externalcamera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector

class FaceAnalyzer(
    private val faceDetector: FaceDetector,
    private val graphicOverlay: GraphicOverlay,
    private val isImageFlipped: Boolean) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            // ...
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (rotationDegrees == 0 || rotationDegrees == 180) {
                graphicOverlay!!.setImageSourceInfo(imageProxy.width, imageProxy.height, isImageFlipped)
            } else {
                graphicOverlay!!.setImageSourceInfo(imageProxy.height, imageProxy.width, isImageFlipped)
            }

            val result = faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    // Task completed successfully
                    if (faces.isNullOrEmpty()) {
                        return@addOnSuccessListener
                    }

                    graphicOverlay.clear()

                    for (face in faces) {
                        graphicOverlay.add(FaceGraphic(graphicOverlay, face))
                    }
                    graphicOverlay.postInvalidate()
                }.addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}