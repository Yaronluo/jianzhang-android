package com.jianzhang.app.domain.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Runs the bundled ML Kit Chinese recognizer entirely against an image on this device. */
object LocalReceiptOcr {
    suspend fun recognize(context: Context, imageUri: Uri): String {
        val image = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result.text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
                .addOnCompleteListener { recognizer.close() }
        }
    }
}
