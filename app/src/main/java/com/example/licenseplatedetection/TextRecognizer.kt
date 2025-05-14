package com.example.licenseplatedetection

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

class TextRecognizer(
    private val context: Context,
    private val textRecognizerListener: TextRecognizerListener
) {
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @RequiresApi(Build.VERSION_CODES.O)
    fun recognizeText(bitmap: Bitmap, boundingBox: BoundingBox) {
        Log.e("TESTING", "$this = ${LocalDateTime.now()}")
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

//            val fileInit = File(context.cacheDir, "test_init_${LocalDateTime.now()}.jpeg")
//            FileOutputStream(fileInit).use {
//                plateBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
//            }
//
//            val file = File(context.cacheDir, "test_${LocalDateTime.now()}.jpeg")
//            FileOutputStream(file).use {
//                plateBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
//            }
//            Log.e("TESTING", "$this, ${file.path}")

            val image = InputImage.fromBitmap(plateBitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.e("TESTING", "success = ${LocalDateTime.now()}")
                    // Process the text and clean it up
                    val detectedText = visionText.text.replace("\n", " ").trim()
                    if (detectedText.isNotEmpty()) {
                        textRecognizerListener.onTextRecognized(boundingBox, detectedText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TESTING", "error = ${LocalDateTime.now()}")
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