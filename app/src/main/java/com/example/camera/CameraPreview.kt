package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    zoomRatio: Float,
    isTorchOn: Boolean,
    isFrozen: Boolean,
    frozenBitmap: Bitmap?,
    colorFilterMode: ColorFilterMode,
    onMaxZoomRatioDetermined: (Float) -> Unit,
    onZoomRatioChanged: (Float) -> Unit,
    onBitmapCaptured: (Bitmap) -> Unit,
    onPreviewViewCreated: (PreviewView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    // Sync Zoom state with CameraX
    LaunchedEffect(zoomRatio, camera) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    // Sync Torch state with CameraX
    LaunchedEffect(isTorchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            // Torch might not be supported on all devices/emulators gracefully handled
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isFrozen && frozenBitmap != null) {
            // Display Frozen Frame Snapshot
            Image(
                bitmap = frozenBitmap.asImageBitmap(),
                contentDescription = "Fermo Immagine",
                modifier = Modifier.fillMaxSize(),
                colorFilter = colorFilterMode.matrix?.let { ColorFilter.colorMatrix(it) }
            )
        } else {
            // Live CameraX Feed
            AndroidView(
                factory = { ctx ->
                    val pView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    previewView = pView
                    onPreviewViewCreated(pView)

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(pView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                            camera = boundCamera

                            // Observe Zoom state
                            boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                                if (zoomState != null) {
                                    onMaxZoomRatioDetermined(zoomState.maxZoomRatio)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    // Setup Touch-to-Focus & Pinch-to-Zoom Gesture Listeners
                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: zoomRatio
                            val newZoom = currentZoom * detector.scaleFactor
                            onZoomRatioChanged(newZoom)
                            return true
                        }
                    })

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            tapFocusPoint = Offset(e.x, e.y)
                            val factory = pView.meteringPointFactory
                            val point = factory.createPoint(e.x, e.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                            return true
                        }
                    })

                    pView.setOnTouchListener { v, event ->
                        scaleDetector.onTouchEvent(event)
                        gestureDetector.onTouchEvent(event)
                        v.performClick()
                        true
                    }

                    pView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Visual Color Matrix overlay if filter active on live stream
            if (colorFilterMode.matrix != null) {
                val bitmap = previewView?.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Filtro applicato",
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.colorMatrix(colorFilterMode.matrix)
                    )
                }
            }

            // Target Focus Reticle overlay
            tapFocusPoint?.let { point ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.Yellow,
                        radius = 48f,
                        center = point,
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}

fun captureFrameBitmap(previewView: PreviewView?, frozenBitmap: Bitmap?): Bitmap? {
    if (frozenBitmap != null) {
        return frozenBitmap
    }
    return previewView?.bitmap
}
