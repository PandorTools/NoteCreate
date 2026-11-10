package com.pandortools.notecreate.ink

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.pandortools.notecreate.data.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Russian handwriting recognition. The language model is downloaded once and recognition runs on device. */
object HandwritingRecognition {
    suspend fun recognize(strokes: List<Stroke>): String? = withContext(Dispatchers.IO) {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("ru")
            ?: return@withContext null
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(model).await()) {
            manager.download(model, DownloadConditions.Builder().build()).await()
        }
        val inkBuilder = Ink.builder()
        var pointTime = System.currentTimeMillis()
        strokes.filter { it.size >= 2 }.forEach { source ->
            val stroke = Ink.Stroke.builder()
            for (i in 0 until source.size) {
                stroke.addPoint(Ink.Point.create(source.x(i), source.y(i), pointTime++))
            }
            inkBuilder.addStroke(stroke.build())
        }
        val ink = inkBuilder.build()
        if (ink.strokes.isEmpty()) return@withContext null
        val recognizer: DigitalInkRecognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build(),
        )
        try {
            recognizer.recognize(ink).await().candidates.firstOrNull()?.text
        } finally {
            recognizer.close()
        }
    }
}
