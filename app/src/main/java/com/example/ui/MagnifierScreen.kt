package com.example.ui

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraPreview
import com.example.camera.ColorFilterMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnifierScreen(
    viewModel: MagnifierViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview Feed
        CameraPreview(
            zoomRatio = uiState.zoomRatio,
            isTorchOn = uiState.isTorchOn,
            isFrozen = uiState.isFrozen,
            frozenBitmap = uiState.frozenBitmap,
            colorFilterMode = uiState.colorFilterMode,
            onMaxZoomRatioDetermined = { maxZoom ->
                viewModel.updateMaxZoom(maxZoom)
            },
            onZoomRatioChanged = { ratio ->
                viewModel.setZoom(ratio)
            },
            onBitmapCaptured = {},
            onPreviewViewCreated = { pView ->
                previewViewRef = pView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Overlay Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Branding Pill
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lente ${String.format("%.1fx", uiState.zoomRatio)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Action Buttons Row (Torch, Freeze Frame, Color Filter)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Flashlight / Torch
                    IconButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (uiState.isTorchOn) Color(0xFFFFB703) else Color(0xFF1E293B).copy(alpha = 0.8f),
                                CircleShape
                            )
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torcia",
                            tint = if (uiState.isTorchOn) Color(0xFF0F172A) else Color.White
                        )
                    }

                    // Freeze Frame Button
                    IconButton(
                        onClick = {
                            viewModel.toggleFreeze {
                                previewViewRef?.bitmap
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (uiState.isFrozen) Color(0xFF38BDF8) else Color(0xFF1E293B).copy(alpha = 0.8f),
                                CircleShape
                            )
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = "Fermo Immagine",
                            tint = if (uiState.isFrozen) Color(0xFF0F172A) else Color.White
                        )
                    }

                    // Contrast Filter Dropdown Toggle
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = !showFilterMenu },
                            modifier = Modifier
                                .background(
                                    if (uiState.colorFilterMode != ColorFilterMode.NORMAL) Color(0xFFA855F7) else Color(0xFF1E293B).copy(alpha = 0.8f),
                                    CircleShape
                                )
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = "Filtro Contrasto",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            ColorFilterMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.displayName,
                                            color = if (uiState.colorFilterMode == mode) Color(0xFFFFB703) else Color.White,
                                            fontWeight = if (uiState.colorFilterMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setColorFilterMode(mode)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Freeze Frame Floating Badge Indicator
        if (uiState.isFrozen) {
            Surface(
                color = Color(0xFF0284C7),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Text(
                    text = "❄️ FERMO IMMAGINE ATTIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom HUD Bar (Zoom Controls + Translate Button)
        if (!uiState.showResultSheet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quick Zoom Preset Chips (1x, 2x, 4x, 8x, 10x)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.8f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(1.0f, 2.0f, 4.0f, 8.0f, 10.0f)
                            .filter { it <= uiState.maxZoomRatio }

                        presets.forEach { preset ->
                            val isSelected = (uiState.zoomRatio - preset).let { kotlin.math.abs(it) < 0.2f }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(0xFFFFB703) else Color.Transparent
                                    )
                                    .clickable { viewModel.setZoom(preset) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${preset.toInt()}x",
                                    color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fine Zoom Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = uiState.zoomRatio,
                            onValueChange = { viewModel.setZoom(it) },
                            valueRange = 1.0f..uiState.maxZoomRatio,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFB703),
                                activeTrackColor = Color(0xFFFFB703),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${String.format("%.1f", uiState.zoomRatio)}x",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main OCR & Translation Floating Action Button
                    Button(
                        onClick = {
                            val capturedBmp = uiState.frozenBitmap ?: previewViewRef?.bitmap
                            if (capturedBmp != null) {
                                viewModel.processOcrAndTranslate(capturedBmp)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Inquadra un testo con la fotocamera per iniziare.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB703)),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Riconosci e Traduci",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "RICONOSCI E TRADUCI IN ITALIANO",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Sliding Translation Result Sheet
        AnimatedVisibility(
            visible = uiState.showResultSheet,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ResultOverlayPanel(
                isProcessing = uiState.isProcessingOcr,
                result = uiState.ocrResult,
                textSizeSp = uiState.resultTextSizeSp,
                isSpeaking = uiState.isSpeaking,
                isSpeakingOriginal = uiState.isSpeakingOriginal,
                onClose = { viewModel.closeResultSheet() },
                onSpeakTranslation = { viewModel.speakTranslation() },
                onSpeakOriginal = { viewModel.speakOriginalText() },
                onStopSpeech = { viewModel.stopSpeech() },
                onAdjustTextSize = { delta -> viewModel.adjustResultTextSize(delta) },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}
