package com.craznail.flashnote.process

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocalOcr {
    private val client by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            client.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text?.trim().orEmpty()
                    if (cont.isActive) cont.resume(text.ifBlank { null })
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
}
