package com.strawlens.analyzer.data

/**
 * Result returned by Gemini for a single analyzed photo.
 * stemsPercentage + productPercentage should be close to 100,
 * but we don't force it — we show exactly what the model returned.
 */
data class MixtureResult(
    val stemsPercentage: Double,
    val productPercentage: Double,
    val notes: String?
)
