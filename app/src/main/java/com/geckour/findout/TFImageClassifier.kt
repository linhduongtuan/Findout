package com.geckour.findout

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.IOException
import java.util.*

class TFImageClassifier(
        assetManager: AssetManager,
        modelFileName: String,
        labelFileName: String,
        private val inputSize: Long,
        private val imageMean: Long,
        private val imageStd: Float,
        private val inputName: String,
        private val outputName: String
) {
    private val inferenceInterface: TensorFlowInferenceInterface
    private val labels = Vector<String>()
    private val outputNames: Array<String>
    private val intValues: IntArray
    private val floatValues: FloatArray
    private val outputs: FloatArray

    init {
        try {
            assetManager.open(labelFileName.split("file:///android_asset/").last())
                    .bufferedReader()
                    .useLines {
                it.forEach {
                    labels.add(it)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Cannot read label file.", e)
        }

        inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFileName)
        val numClasses = inferenceInterface.graphOperation(outputName)
                .output<IntArray>(0)
                .shape()
                .size(1)
                .toInt()

        outputNames = arrayOf(outputName)
        intValues = IntArray((inputSize * inputSize).toInt())
        floatValues = FloatArray((inputSize * inputSize).toInt() * 3)
        outputs = FloatArray(numClasses)
    }

    fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        intValues.apply { bitmap.getPixels(this, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                .forEachIndexed { index, value ->
                    floatValues[index * 3] = (Color.red(value) - imageMean) / imageStd
                    floatValues[index * 3 + 1] = (Color.blue(value) - imageMean) / imageStd
                    floatValues[index * 3 + 2] = (Color.green(value) - imageMean) / imageStd
                }

        inferenceInterface.feed(inputName, floatValues, 1L, inputSize, inputSize, 3)
        inferenceInterface.run(outputNames, false)
        inferenceInterface.fetch(outputName, outputs)

        return outputs.mapIndexed { i, f ->
            Recognition(i.toString(), if (labels.size > i) labels[i] else "Unknown", f, null)
        }.sortedByDescending { it.confidence }
    }

    data class Recognition(
            val id: String,
            val title: String,
            val confidence: Float,
            var location: RectF?
    )
}