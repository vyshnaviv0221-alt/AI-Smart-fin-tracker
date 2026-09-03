data class ExpenseMLModel(
    val vocabulary: Map<String, Int>,
    val idf: DoubleArray,
    val classes: List<String>,
    val coefficients: Array<DoubleArray>,
    val intercept: DoubleArray
)