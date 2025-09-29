package com.picfinder.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class OCRService {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun extractTextFromImage(imagePath: String): String {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w("OCRService", "Image file does not exist: $imagePath")
                return ""
            }
            
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.w("OCRService", "Could not decode image: $imagePath")
                return ""
            }
            
            extractTextFromBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("OCRService", "Error extracting text from image: $imagePath", e)
            ""
        }
    }
    
    suspend fun extractTextFromUri(context: android.content.Context, uri: Uri): String {
        return try {
            val inputImage = InputImage.fromFilePath(context, uri)
            extractTextFromInputImage(inputImage)
        } catch (e: Exception) {
            Log.e("OCRService", "Error extracting text from URI: $uri", e)
            ""
        }
    }
    
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            extractTextFromInputImage(inputImage)
        } catch (e: Exception) {
            Log.e("OCRService", "Error extracting text from bitmap", e)
            ""
        }
    }
    
    private suspend fun extractTextFromInputImage(inputImage: InputImage): String {
        return suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    Log.d("OCRService", "Extracted text: ${extractedText.take(100)}...")
                    continuation.resume(extractedText)
                }
                .addOnFailureListener { e ->
                    Log.e("OCRService", "Text recognition failed", e)
                    continuation.resume("")
                }
        }
    }
    
    fun close() {
        textRecognizer.close()
    }
    
    companion object {
        private const val TAG = "OCRService"
        
        // Supported image extensions
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")
        
        fun isImageFile(fileName: String): Boolean {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return extension in SUPPORTED_EXTENSIONS
        }
    }
}