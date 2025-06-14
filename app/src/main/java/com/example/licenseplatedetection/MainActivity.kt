package com.example.licenseplatedetection

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.licenseplatedetection.Constants.LABELS_PATH
import com.example.licenseplatedetection.Constants.MODEL_PATH
import com.example.licenseplatedetection.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener,
    TextRecognizer.TextRecognizerListener {
    private lateinit var binding: ActivityMainBinding
    private var currentBitmap: Bitmap? = null

    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var detector: Detector
    private lateinit var textRecognizer: TextRecognizer


    private lateinit var cameraExecutor: ExecutorService

    private val results = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
            detector.setup()
            textRecognizer = TextRecognizer(this, this)

            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }

            cameraExecutor = Executors.newSingleThreadExecutor()

            binding.btnFile.setOnClickListener {
                one()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.e("TESTING", "$requestCode, $resultCode, $data")
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val projection =
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name =
                        it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    Log.e("TESTING", "$id, $name, $uri")
                }
            }

            data?.data?.let { path ->
//                val outputDir = File(Environment.getExternalStorageDirectory(), "TestVideoFrames")
//                if (!outputDir.exists()) {
//                    outputDir.mkdirs()
//                }

                extractFrames(path)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun extractFrames(path: Uri) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, path)

//        val bitmap = retriever.getFrameAtTime(1_000_000)
//        if (bitmap != null) {
//            val frameFile = File(this.cacheDir, "x_1_000_000.jpeg")
//            FileOutputStream(frameFile).use {
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
//            }
//            bitmap.recycle()
//        }

        val duration =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0
        val frameRate =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: 0
        Log.e("TESTING", "duration = $duration, rate = $frameRate")

        val timeInterval = duration / frameRate
        (0..frameRate).forEach {
            val x = it * timeInterval * 1000
            Log.e("TESTING", "$x")
            val bitmap: Bitmap? = retriever.getFrameAtTime(x, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                val name = "frame_${x}.jpeg"
//                val frameFile = File(this.cacheDir, name)
                Log.e("TESTING", name)
                detector.detect(bitmap)
//                FileOutputStream(frameFile).use {
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
//                }
//                bitmap.recycle()
            }
        }

        retriever.release()
    }

    private fun one() {
//        val checked = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//        if (checked != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
//                1
//            )
//        } else {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, 100)
//        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
//        Log.e("TESTING", "${cameraInfo.cameraState.value.}")

        preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),  // Желаемое разрешение (Full HD)
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER  // Правило выбора
                        )
                    )
                    .build()
//                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
//                    .build()
            )
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),  // Желаемое разрешение (Full HD)
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER  // Правило выбора
                        )
                    )
                    .build()
//                ResolutionSelector.Builder()
//                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
//                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            Log.e("TESTING", "${imageProxy.height}x${imageProxy.width}")
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

                if (isFrontCamera) {
                    postScale(
                        -1f,
                        1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            currentBitmap = rotatedBitmap
            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Log.e(TAG, "Camera permission denied")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
        currentBitmap?.recycle()
        currentBitmap = null
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.inferenceTime.text = "0ms"
            binding.overlay.clearResults() // Gọi method mới để xóa kết quả
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        val bitmap = currentBitmap
        if (bitmap != null) {
            boundingBoxes.forEach { box ->
                textRecognizer.recognizeText(bitmap, box)
            }
        }

        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.updateResults(boundingBoxes)
        }
    }

    override fun onTextRecognized(boundingBox: BoundingBox, text: String) {
        runOnUiThread {
            Log.e("TESTING", "text = $text")
            val r = text.uppercase(Locale("en", "US")).filterNot { it.isWhitespace() }
            if (r.length != 6 && r.length != 8 && r.length != 9) return@runOnUiThread
            if (r.length == 6) {
                if (
                    !r[0].isLetter() ||
                    !r[1].isDigit() ||
                    !r[2].isDigit() ||
                    !r[3].isDigit() ||
                    !r[4].isLetter() ||
                    !r[5].isLetter()
                ) return@runOnUiThread
            }
            if (r.length == 8) {
                if (
                    !r[0].isLetter() ||
                    !r[1].isDigit() ||
                    !r[2].isDigit() ||
                    !r[3].isDigit() ||
                    !r[4].isLetter() ||
                    !r[5].isLetter() ||
                    !r[6].isDigit() ||
                    !r[7].isDigit()
                ) return@runOnUiThread
            }
            if (r.length == 9) {
                if (
                    !r[0].isLetter() ||
                    !r[1].isDigit() ||
                    !r[2].isDigit() ||
                    !r[3].isDigit() ||
                    !r[4].isLetter() ||
                    !r[5].isLetter() ||
                    !r[6].isDigit() ||
                    !r[7].isDigit() ||
                    !r[8].isDigit()
                ) return@runOnUiThread
            }

            results.add(r)
            binding.numbers.text = results.joinToString("\n")
            binding.overlay.updatePlateText(boundingBox, text)
        }
    }

    override fun onTextRecognitionFailed(error: String) {
        Log.e(TAG, "Text recognition failed: $error")
    }

}