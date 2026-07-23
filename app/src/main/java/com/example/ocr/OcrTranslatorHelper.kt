package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.BuildConfig
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class TranslationResult(
    val originalText: String,
    val detectedLanguageCode: String,
    val detectedLanguageName: String,
    val translatedText: String,
    val isAlreadyItalian: Boolean = false,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

class OcrTranslatorHelper(private val context: Context) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun processImage(bitmap: Bitmap): TranslationResult = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Step 1: OCR Text Extraction
            val visionText = recognizeText(inputImage)
            val extractedText = visionText.trim()

            if (extractedText.isEmpty()) {
                return@withContext TranslationResult(
                    originalText = "",
                    detectedLanguageCode = "unknown",
                    detectedLanguageName = "Sconosciuta",
                    translatedText = "",
                    isSuccess = false,
                    errorMessage = "Nessun testo rilevato nell'immagine. Prova ad avvicinarti o ad aumentare lo zoom."
                )
            }

            // Step 2: Language Identification
            val langCode = identifyLanguage(extractedText)
            val langName = getLanguageDisplayName(langCode)

            // If text is already in Italian, return early without extra translation steps
            if (langCode == "it") {
                return@withContext TranslationResult(
                    originalText = extractedText,
                    detectedLanguageCode = "it",
                    detectedLanguageName = langName,
                    translatedText = extractedText,
                    isAlreadyItalian = true,
                    isSuccess = true
                )
            }

            // Step 3: Translate into Italian (ML Kit or Gemini/Google API fallback)
            val translated = translateToItalian(extractedText, langCode)

            TranslationResult(
                originalText = extractedText,
                detectedLanguageCode = langCode,
                detectedLanguageName = langName,
                translatedText = translated,
                isAlreadyItalian = false,
                isSuccess = true
            )
        } catch (e: Exception) {
            TranslationResult(
                originalText = "",
                detectedLanguageCode = "error",
                detectedLanguageName = "Errore",
                translatedText = "",
                isSuccess = false,
                errorMessage = "Errore durante l'elaborazione: ${e.localizedMessage ?: "Errore sconosciuto"}"
            )
        }
    }

    private suspend fun recognizeText(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (continuation.isActive) {
                    continuation.resume(visionText.text)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resume("")
                }
            }
    }

    private suspend fun identifyLanguage(text: String): String = suspendCancellableCoroutine { continuation ->
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (continuation.isActive) {
                    if (languageCode == "und") {
                        continuation.resume("en") // Default to English if undetermined
                    } else {
                        continuation.resume(languageCode)
                    }
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume("en")
                }
            }
    }

    private suspend fun translateToItalian(sourceText: String, sourceLangCode: String): String {
        // Try Google Translate REST Endpoint first (instantaneous)
        val googleResult = translateWithGoogleTranslateRest(sourceText, sourceLangCode)
        if (!googleResult.isNullOrBlank()) {
            return googleResult
        }

        // Try ML Kit translate
        val mlKitResult = translateWithMlKit(sourceText, sourceLangCode)
        if (!mlKitResult.isNullOrBlank()) {
            return mlKitResult
        }

        // Fallback to Gemini REST API
        val geminiResult = translateWithGeminiApi(sourceText, sourceLangCode)
        if (!geminiResult.isNullOrBlank()) {
            return geminiResult
        }

        return sourceText
    }

    private suspend fun translateWithMlKit(sourceText: String, sourceLangCode: String): String? {
        val mlKitLang = TranslateLanguage.fromLanguageTag(sourceLangCode) ?: return null
        val targetLang = TranslateLanguage.ITALIAN

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlKitLang)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)

        return try {
            // Ensure model is downloaded or fast-translated
            suspendCancellableCoroutine { continuation ->
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator.translate(sourceText)
                            .addOnSuccessListener { translatedText ->
                                if (continuation.isActive) continuation.resume(translatedText)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            null
        } finally {
            translator.close()
        }
    }

    private fun translateWithGeminiApi(sourceText: String, sourceLangCode: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return null

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val prompt = "Traduci il seguente testo dall'originale (lingua: $sourceLangCode) in un italiano naturale e chiaro. Restituisci ESCLUSIVAMENTE la traduzione in italiano, senza note o spiegazioni aggiuntive.\n\nTesto:\n$sourceText"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return null
                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text")?.trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun translateWithGoogleTranslateRest(sourceText: String, sourceLangCode: String): String? {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLangCode&tl=it&dt=t&q=${java.net.URLEncoder.encode(sourceText, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return null
                val jsonArray = JSONArray(bodyStr)
                val sentences = jsonArray.optJSONArray(0)
                if (sentences != null) {
                    val sb = StringBuilder()
                    for (i in 0 until sentences.length()) {
                        val sentence = sentences.optJSONArray(i)
                        if (sentence != null) {
                            sb.append(sentence.optString(0))
                        }
                    }
                    return sb.toString().trim()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun getLanguageDisplayName(code: String): String {
        return when (code.lowercase()) {
            "en" -> "Inglese 🇬🇧"
            "es" -> "Spagnolo 🇪🇸"
            "fr" -> "Francese 🇫🇷"
            "de" -> "Tedesco 🇩🇪"
            "it" -> "Italiano 🇮🇹"
            "pt" -> "Portoghese 🇵🇹"
            "ru" -> "Russo 🇷🇺"
            "ja" -> "Giapponese 🇯🇵"
            "zh" -> "Cinese 🇨🇳"
            "ar" -> "Arabo 🇸🇦"
            "nl" -> "Olandese 🇳🇱"
            "pl" -> "Polacco 🇵🇱"
            "ro" -> "Rumeno 🇷🇴"
            "el" -> "Greco 🇬🇷"
            "tr" -> "Turco 🇹🇷"
            else -> try {
                val loc = Locale(code)
                val display = loc.getDisplayLanguage(Locale.ITALIAN)
                if (display.isNotBlank()) display.replaceFirstChar { it.uppercase() } else code.uppercase()
            } catch (e: Exception) {
                code.uppercase()
            }
        }
    }
}
