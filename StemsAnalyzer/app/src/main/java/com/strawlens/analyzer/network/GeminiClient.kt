package com.strawlens.analyzer.network

import com.strawlens.analyzer.data.MixtureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiApiException(message: String) : Exception(message)

/**
 * Talks directly to Google's Generative Language REST API
 * (https://ai.google.dev/api/generate-content) — no SDK needed.
 *
 * The prompt below is adapted from the original web app's mixture-analysis
 * prompt (services/geminiService.ts), narrowed from an open category list
 * down to exactly two buckets: stems/straw vs. the actual product/grain.
 */
object GeminiClient {

    private const val ENDPOINT_TEMPLATE =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    suspend fun analyzeStemsVsProduct(
        apiKey: String,
        model: String,
        imageBase64Jpeg: String,
        language: String // "en" or "ar"
    ): MixtureResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(
                if (language == "ar") "الرجاء إدخال مفتاح Gemini API من شاشة الإعدادات."
                else "Please add your Gemini API key in Settings first."
            )
        }

        val prompt = buildPrompt(language)
        val requestBody = buildRequestBody(prompt, imageBase64Jpeg)

        val urlStr = ENDPOINT_TEMPLATE.format(model, apiKey)
        val connection = URL(urlStr).openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

        try {
            val bodyBytes = requestBody.toString().toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Length", bodyBytes.size.toString())
            (connection.outputStream as OutputStream).use { it.write(bodyBytes) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (responseCode !in 200..299) {
                throw GeminiApiException(friendlyErrorMessage(responseCode, responseText, language))
            }

            return@withContext parseResult(responseText, language)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(language: String): String {
        return if (language == "ar") {
            """
            أنت خبير متخصص ودقيق جداً في فرز المحاصيل والمخلفات الزراعية.
            مهمتك: تحليل الصورة المرفقة بدقة متناهية وتحديد نسبتين فقط:
            1. "القش/السيقان" (Stems/Straw): المادة الليفية الخفيفة عديمة القيمة التجارية (سيقان، قش، تبن، حطام نباتي).
            2. "المنتج" (Product): المادة الأساسية المرغوبة (الحبوب أو الثمار أو المحصول الفعلي).

            منهجية التحليل:
            - قسّم الصورة ذهنياً إلى شبكة (10x10) وافحص كل مربع لتحديد المكون المسيطر فيه.
            - احسب "الكتلة المرئية" (Visual Mass) وليس فقط المساحة السطحية: القش يغطي مساحة كبيرة لكنه خفيف وقليل الكثافة، بينما المنتج أكثر كثافة حتى لو مساحته أصغر.
            - إذا كان القش يغطي جزءاً من المنتج، احسب النسبة الفعلية للمنتج الظاهر والمخفي جزئياً بأفضل تقدير ممكن.
            - يجب أن يكون مجموع النسبتين قريباً جداً من 100%.

            أجب فقط بتنسيق JSON يطابق المخطط المطلوب تماماً، بدون أي نص إضافي:
            - stems_percentage: النسبة المئوية للقش/السيقان (رقم عشري بين 0 و100).
            - product_percentage: النسبة المئوية للمنتج (رقم عشري بين 0 و100).
            - notes: جملة قصيرة جداً (اختيارية) توضح أي ملاحظة مهمة، مثل عدم وضوح الصورة.
            """.trimIndent()
        } else {
            """
            You are a meticulous expert in sorting and grading agricultural crops and residue.
            Your task: analyze the attached photo of raw material with extreme care and determine exactly two percentages:
            1. "Stems/Straw": the light, fibrous, commercially low-value material (stems, straw, chaff, plant debris).
            2. "Product": the actual desired crop (grain, seed, fruit, or produce itself).

            Analysis methodology:
            - Mentally divide the image into a 10x10 grid and inspect each cell to identify the dominant material.
            - Estimate visual MASS, not just surface area: stems/straw often cover a large area but are light and low-density, while the product is denser even when it covers less visible area.
            - If stems partially cover or hide product underneath, estimate the true product percentage as best you can, not just what's visible on top.
            - The two percentages should sum to very close to 100%.

            Respond ONLY with JSON matching the required schema, no extra text:
            - stems_percentage: percentage of stems/straw (a decimal number between 0 and 100).
            - product_percentage: percentage of product (a decimal number between 0 and 100).
            - notes: a very short optional note, e.g. if the photo is unclear or lighting is poor.
            """.trimIndent()
        }
    }

    private fun buildRequestBody(prompt: String, imageBase64Jpeg: String): JSONObject {
        val inlineData = JSONObject().apply {
            put("mimeType", "image/jpeg")
            put("data", imageBase64Jpeg)
        }
        val imagePart = JSONObject().apply { put("inlineData", inlineData) }
        val textPart = JSONObject().apply { put("text", prompt) }

        val parts = JSONArray().apply {
            put(imagePart)
            put(textPart)
        }
        val content = JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        }
        val contents = JSONArray().apply { put(content) }

        val schemaProperties = JSONObject().apply {
            put("stems_percentage", JSONObject().apply { put("type", "NUMBER") })
            put("product_percentage", JSONObject().apply { put("type", "NUMBER") })
            put("notes", JSONObject().apply { put("type", "STRING") })
        }
        val responseSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", schemaProperties)
            put("required", JSONArray().apply {
                put("stems_percentage")
                put("product_percentage")
            })
        }
        val generationConfig = JSONObject().apply {
            put("temperature", 0)
            put("responseMimeType", "application/json")
            put("responseSchema", responseSchema)
        }

        return JSONObject().apply {
            put("contents", contents)
            put("generationConfig", generationConfig)
        }
    }

    private fun parseResult(responseText: String, language: String): MixtureResult {
        try {
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
                ?: throw GeminiApiException(noResultMessage(language))
            if (candidates.length() == 0) throw GeminiApiException(noResultMessage(language))

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")
                ?: throw GeminiApiException(noResultMessage(language))

            val json = JSONObject(text.trim())
            val stems = json.optDouble("stems_percentage", Double.NaN)
            val product = json.optDouble("product_percentage", Double.NaN)
            val notes = json.optString("notes", "").ifBlank { null }

            if (stems.isNaN() || product.isNaN()) {
                throw GeminiApiException(noResultMessage(language))
            }

            return MixtureResult(
                stemsPercentage = stems,
                productPercentage = product,
                notes = notes
            )
        } catch (e: GeminiApiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiApiException(noResultMessage(language))
        }
    }

    private fun noResultMessage(language: String) =
        if (language == "ar") "تعذر قراءة نتيجة التحليل. حاول مرة أخرى بصورة أوضح."
        else "Couldn't read a result from the model. Try again with a clearer photo."

    private fun friendlyErrorMessage(code: Int, rawBody: String, language: String): String {
        val lower = rawBody.lowercase()
        return when {
            code == 400 && lower.contains("api key not valid") ->
                if (language == "ar") "مفتاح API غير صحيح. تحقق منه في الإعدادات." else "Your API key looks invalid. Check it in Settings."
            code == 403 ->
                if (language == "ar") "تم رفض الطلب (403). تحقق من صلاحية مفتاح API." else "Request was refused (403). Check your API key's permissions."
            code == 404 ->
                if (language == "ar") "النموذج المحدد غير متوفر. جرّب تغييره من الإعدادات." else "The selected model isn't available. Try changing it in Settings."
            code == 429 || lower.contains("resource_exhausted") || lower.contains("quota") ->
                if (language == "ar") "تم تجاوز حد الاستخدام (Quota). انتظر قليلاً ثم حاول مرة أخرى." else "You've hit the API rate/quota limit. Wait a bit and try again."
            code in 500..599 ->
                if (language == "ar") "خدمة Gemini غير متاحة مؤقتاً. حاول مرة أخرى." else "Gemini's servers had an issue. Please try again."
            else ->
                if (language == "ar") "فشل الطلب (رمز $code)." else "Request failed (code $code)."
        }
    }
}
