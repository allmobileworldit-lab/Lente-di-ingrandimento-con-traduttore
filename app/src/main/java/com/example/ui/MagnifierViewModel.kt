package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.ColorFilterMode
import com.example.ocr.OcrTranslatorHelper
import com.example.ocr.TranslationResult
import com.example.tts.TtsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MagnifierUiState(
    val zoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 10.0f,
    val isTorchOn: Boolean = false,
    val isFrozen: Boolean = false,
    val frozenBitmap: Bitmap? = null,
    val colorFilterMode: ColorFilterMode = ColorFilterMode.NORMAL,
    val isProcessingOcr: Boolean = false,
    val ocrResult: TranslationResult? = null,
    val showResultSheet: Boolean = false,
    val resultTextSizeSp: Float = 18f,
    val isSpeaking: Boolean = false,
    val isSpeakingOriginal: Boolean = false
)

class MagnifierViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MagnifierUiState())
    val uiState: StateFlow<MagnifierUiState> = _uiState.asStateFlow()

    private val ocrHelper = OcrTranslatorHelper(application.applicationContext)
    private val ttsHelper = TtsHelper(application.applicationContext)

    fun updateMaxZoom(maxZoom: Float) {
        _uiState.update { it.copy(maxZoomRatio = maxZoom.coerceAtLeast(1.0f)) }
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(1.0f, _uiState.value.maxZoomRatio)
        _uiState.update { it.copy(zoomRatio = clamped) }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun setTorch(enabled: Boolean) {
        _uiState.update { it.copy(isTorchOn = enabled) }
    }

    fun toggleFreeze(currentBitmapSupplier: () -> Bitmap?) {
        val currentState = _uiState.value
        if (currentState.isFrozen) {
            // Unfreeze
            _uiState.update { it.copy(isFrozen = false, frozenBitmap = null) }
        } else {
            // Freeze frame
            val bmp = currentBitmapSupplier()
            if (bmp != null) {
                _uiState.update { it.copy(isFrozen = true, frozenBitmap = bmp) }
            }
        }
    }

    fun setColorFilterMode(mode: ColorFilterMode) {
        _uiState.update { it.copy(colorFilterMode = mode) }
    }

    fun processOcrAndTranslate(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingOcr = true,
                    showResultSheet = true
                )
            }

            val result = ocrHelper.processImage(bitmap)

            _uiState.update {
                it.copy(
                    isProcessingOcr = false,
                    ocrResult = result
                )
            }
        }
    }

    fun closeResultSheet() {
        stopSpeech()
        _uiState.update { it.copy(showResultSheet = false) }
    }

    fun speakTranslation() {
        val text = _uiState.value.ocrResult?.translatedText
        if (!text.isNullOrBlank()) {
            ttsHelper.speak(text, "it")
            _uiState.update { it.copy(isSpeaking = true, isSpeakingOriginal = false) }
        }
    }

    fun speakOriginalText() {
        val text = _uiState.value.ocrResult?.originalText
        val langCode = _uiState.value.ocrResult?.detectedLanguageCode ?: "en"
        if (!text.isNullOrBlank()) {
            ttsHelper.speak(text, langCode)
            _uiState.update { it.copy(isSpeaking = true, isSpeakingOriginal = true) }
        }
    }

    fun stopSpeech() {
        ttsHelper.stop()
        _uiState.update { it.copy(isSpeaking = false, isSpeakingOriginal = false) }
    }

    fun adjustResultTextSize(delta: Float) {
        _uiState.update {
            it.copy(resultTextSizeSp = (it.resultTextSizeSp + delta).coerceIn(12f, 36f))
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}
