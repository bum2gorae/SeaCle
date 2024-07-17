package com.example.seacle

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.seacle.ui.theme.SeaCleTheme
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.IOException

data class Prediction(
    @SerializedName("class_name") val className: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("x1") val x1: Float,
    @SerializedName("x2") val x2: Float,
    @SerializedName("y1") val y1: Float,
    @SerializedName("y2") val y2: Float
)

data class FlaskResponse(
    @SerializedName("data") val data: List<Prediction>
)

class MainViewModel : ViewModel() {
    val imageBitmap = MutableLiveData<Bitmap?>()
    val flaskResponse = MutableLiveData<FlaskResponse?>()
}

fun postDataToFlaskServer(json: String, viewModel: MainViewModel) {
    val client = OkHttpClient()

    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
    val requestBody = json.toRequestBody(mediaType)

    val request = Request.Builder()
        .url("http://192.168.0.101:3800/predict") // Flask 서버의 엔드포인트 URL
        .post(requestBody)
        .build()
    Log.d("flask", "requestFinish")

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Unexpected code $response")

        val responseBody = response.body?.string()
        responseBody?.let {
            Log.d("test","Response: $responseBody")
            val flaskResponse = Gson().fromJson(it, FlaskResponse::class.java)
            Log.d("flask", "Response: $flaskResponse")
            viewModel.flaskResponse.postValue(flaskResponse)
        }
    }
}

class VideoActivity : ComponentActivity() {
    init {
        System.loadLibrary("opencv_java4")
    }
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeaCleTheme {
                val context = LocalContext.current
                val previewView = remember { PreviewView(context) }
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = {
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    LaunchedEffect(Unit) {
                        startCamera(previewView)
                    }
                    val flaskResponse = viewModel.flaskResponse.observeAsState()

                    flaskResponse.value?.let { response ->
                        DrawOverlay(response)
                    }
                }
            }
        }
    }
    private fun startCamera(previewView: PreviewView) {
        Log.d("CameraX", "Starting camera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }


            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
                Log.d("CameraX", "Camera started successfully")
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        Log.d("ImageAnalysis", "Processing image")
        val gson = Gson()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bgrBytes = imageToIntArray(imageProxy)
                val jsonString = gson.toJson(bgrBytes)
                postDataToFlaskServer(jsonString, viewModel)
            } catch (e: Exception) {
                Log.e("ImageAnalysis", "Error processing image", e)
            } finally {
                imageProxy.close()
            }
        }
    }
}

@Composable
fun DisplayImage(bitmap: Bitmap, response: FlaskResponse?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        response?.let {
            DrawOverlay(it)
        }
    }
}

@Composable
fun DrawOverlay(flaskResponse: FlaskResponse) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        flaskResponse.data.forEach { prediction ->
            drawRect(
                color = androidx.compose.ui.graphics.Color.Red,
                topLeft = Offset(prediction.x1, prediction.y1),
                size = androidx.compose.ui.geometry.Size(prediction.x2 - prediction.x1, prediction.y2 - prediction.y1),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = Paint().apply {
                    color = Color.RED
                    textSize = 40f
                }
                drawText(
                    "${prediction.className} (${(prediction.confidence * 100).toInt()}%)",
                    prediction.x1,
                    prediction.y1 - 10,
                    paint
                )
            }
        }
    }
}

private fun imageToIntArray(imageProxy: ImageProxy): Array<Array<IntArray>> {
    val yBuffer = imageProxy.planes[0].buffer
    val uBuffer = imageProxy.planes[1].buffer
    val vBuffer = imageProxy.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val yBytes = ByteArray(ySize)
    val uBytes = ByteArray(uSize)
    val vBytes = ByteArray(vSize)

    yBuffer.get(yBytes)
    uBuffer.get(uBytes)
    vBuffer.get(vBytes)

    val nv21Bytes = ByteArray(ySize + uSize + vSize)
    System.arraycopy(yBytes, 0, nv21Bytes, 0, ySize)
    for (i in 0 until vSize) {
        nv21Bytes[ySize + i * 2] = vBytes[i]
        nv21Bytes[ySize + i * 2 + 1] = uBytes[i]
    }

    val yuvImage = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
    Log.d("imgproxy","width: ${imageProxy.width}, height: ${imageProxy.height}")
    yuvImage.put(0, 0, nv21Bytes)

    val bgrImage = Mat()
    Imgproc.cvtColor(yuvImage, bgrImage, Imgproc.COLOR_YUV2BGR_NV21)

    // 3차원 배열 생성
    val height = bgrImage.rows()
    val width = bgrImage.cols()
    val bgrArray = Array(height) { Array(width) { IntArray(3) } }

    val bgrBytes = ByteArray(bgrImage.total().toInt() * bgrImage.elemSize().toInt())
    bgrImage.get(0, 0, bgrBytes)

    // ByteArray를 3차원 IntArray로 변환
    var index = 0
    for (i in 0 until height) {
        for (j in 0 until width) {
            bgrArray[i][j][0] = bgrBytes[index].toInt() and 0xFF  // Blue
            bgrArray[i][j][1] = bgrBytes[index + 1].toInt() and 0xFF  // Green
            bgrArray[i][j][2] = bgrBytes[index + 2].toInt() and 0xFF  // Red
            index += 3
        }
    }

    return bgrArray
}