package com.example.aismartexpensetracker

import android.content.Context
import org.json.JSONObject
import java.io.InputStream


class ExpenseClassifier(private val context: Context) {

    private lateinit var vocabulary: Map<String, Int>
    private lateinit var idf: DoubleArray
    private lateinit var classes: List<String>
    private lateinit var coefficients: Array<DoubleArray>
    private lateinit var intercept: DoubleArray

    init {
        loadModel()
    }

    private fun loadModel() {
        val inputStream: InputStream =
            context.assets.open("expense_category_model.json")

        val jsonText = inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(jsonText)

        // Load vocabulary
        val vocabularyJson = json.getJSONObject("vocabulary")
        val vocabMap = HashMap<String, Int>()
        val vocabKeys = vocabularyJson.keys()

        while (vocabKeys.hasNext()) {
            val word = vocabKeys.next()
            vocabMap[word] = vocabularyJson.getInt(word)
        }

        vocabulary = vocabMap

        // Load IDF
        val idfJson = json.getJSONArray("idf")
        idf = DoubleArray(idfJson.length())

        for (i in 0 until idfJson.length()) {
            idf[i] = idfJson.getDouble(i)
        }

        // Load classes
        val classesJson = json.getJSONArray("classes")
        classes = List(classesJson.length()) { i ->
            classesJson.getString(i)
        }

        // Load coefficients
        val coefficientsJson = json.getJSONArray("coefficients")

        coefficients = Array(coefficientsJson.length()) { i ->
            val row = coefficientsJson.getJSONArray(i)

            DoubleArray(row.length()) { j ->
                row.getDouble(j)
            }
        }

        // Load intercept
        val interceptJson = json.getJSONArray("intercept")

        intercept = DoubleArray(interceptJson.length()) { i ->
            interceptJson.getDouble(i)
        }

        inputStream.close()
    }

    fun predict(merchantText: String): String {

        val tfidfVector = createTfidfVector(merchantText)

        var bestClassIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY

        for (classIndex in classes.indices) {

            var score = intercept[classIndex]

            for (featureIndex in tfidfVector.indices) {
                score +=
                    coefficients[classIndex][featureIndex] *
                            tfidfVector[featureIndex]
            }

            if (score > bestScore) {
                bestScore = score
                bestClassIndex = classIndex
            }
        }

        return classes[bestClassIndex]
    }

    private fun createTfidfVector(text: String): DoubleArray {

        val vector = DoubleArray(vocabulary.size)

        // Same basic text processing used during training
        val words = text
            .lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotEmpty() }

        // Count words
        val termFrequency = mutableMapOf<String, Int>()

        for (word in words) {
            if (vocabulary.containsKey(word)) {
                termFrequency[word] =
                    (termFrequency[word] ?: 0) + 1
            }
        }

        // Create sublinear TF-IDF
        for ((word, count) in termFrequency) {

            val index = vocabulary[word] ?: continue

            val sublinearTf = 1.0 + kotlin.math.ln(count.toDouble())

            vector[index] = sublinearTf * idf[index]
        }

        // L2 normalization
        var sumSquares = 0.0

        for (value in vector) {
            sumSquares += value * value
        }

        val norm = sqrt(sumSquares)

        if (norm > 0.0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }
}