package com.example.licenseplatedetection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextRecognizer(private val textRecognizerListener: TextRecognizerListener) {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognizeText(bitmap: Bitmap, boundingBox: BoundingBox) {
        // Crop the image to the license plate area
        val plateWidth = ((boundingBox.x2 - boundingBox.x1) * bitmap.width).toInt()
        val plateHeight = ((boundingBox.y2 - boundingBox.y1) * bitmap.height).toInt()
        val plateX = (boundingBox.x1 * bitmap.width).toInt()
        val plateY = (boundingBox.y1 * bitmap.height).toInt()

        if (plateWidth <= 0 || plateHeight <= 0) return

        try {
            val plateBitmap = Bitmap.createBitmap(
                bitmap,
                plateX,
                plateY,
                plateWidth,
                plateHeight
            )

            val image = InputImage.fromBitmap(plateBitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Process the text and clean it up
                    val detectedText = visionText.text.replace("\n", " ").trim()
                    if (detectedText.isNotEmpty()) {
                        textRecognizerListener.onTextRecognized(boundingBox, detectedText)
                    }
                }
                .addOnFailureListener { e ->
                    textRecognizerListener.onTextRecognitionFailed(e.message ?: "Unknown error")
                }
        } catch (e: Exception) {
            textRecognizerListener.onTextRecognitionFailed(e.message ?: "Failed to crop image")
        }
    }

    interface TextRecognizerListener {
        fun onTextRecognized(boundingBox: BoundingBox, text: String)
        fun onTextRecognitionFailed(error: String)
    }
}