package com.ottoman.empire1800.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AiException(
    override val message: String,
    val retryable: Boolean = false,
    val modelProblem: Boolean = false,
    val status: Int? = null,
    val diagnostic: String? = null,
) : Exception(message)

interface AiProvider {
    val id: String
    val name: String
    val defaultModel: String
    val fallbackModels: List<String>
    val supportsSearch: Boolean

    suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean = false,
        maxTokens: Int = 2048,
        search: Boolean = false,
        thinkingLevel: String = "low",
    ): String

    suspend fun discoverModels(apiKey: String): List<String> = emptyList()
}

const val GEMINI_ROUND_MAX_TOKENS: Int = 16384
const val GROQ_ROUND_MAX_TOKENS: Int = 5000

val aiProviders: List<AiProvider> = listOf(GeminiProvider(), GroqProvider())

fun aiProviderById(id: String): AiProvider =
    aiProviders.firstOrNull { it.id == id } ?: aiProviders.first()

private val GEMINI_PRIORITY = listOf(
    "gemini-4-hydrogen",
    "gemini-3.6-flash",
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
)

private val GROQ_PRIORITY = listOf(
    "openai/gpt-oss-120b",
    "llama-3.3-70b-versatile",
    "openai/gpt-oss-20b",
    "llama-3.1-8b-instant",
)

private fun isUsableGeminiTextModel(id: String): Boolean {
    val lower = id.lowercase()
    if (!lower.startsWith("gemini-")) return false
    if (Regex("^gemini-(1\\.|2\\.0)").containsMatchIn(lower)) return false
    val blocked = listOf(
        "tts", "image", "imagen", "veo", "lyria", "embedding",
        "transcribe", "live", "audio", "aqa", "bisect",
        "robotics", "computer-use", "deep-research", "decision",
        "banana", "nano-banana", "gemma", "learnlm",
    )
    return blocked.none { lower.contains(it) }
}

private fun isUsableGroqTextModel(id: String): Boolean {
    val lower = id.lowercase()
    if (lower.isEmpty()) return false
    val blocked = listOf("whisper", "guard", "playai", "orpheus", "tts", "safeguard", "prompt-guard")
    return blocked.none { lower.contains(it) }
}

private fun geminiModelScore(id: String): Int {
    val lower = id.lowercase()
    val idx = GEMINI_PRIORITY.indexOf(lower)
    if (idx >= 0) return 1000 - idx * 50

    var score = when {
        lower.contains("hydrogen") -> 500
        lower.contains("flash") -> 400
        else -> 100
    }
    score += when {
        lower.contains("gemini-4") -> 80
        lower.contains("gemini-3.8") || lower.contains("gemini-3.6") -> 60
        lower.contains("gemini-3.5") || lower.contains("gemini-3.1") -> 50
        lower.contains("gemini-3") -> 45
        lower.contains("gemini-2.5") -> 40
        else -> 0
    }
    if (lower.contains("lite")) score -= 10
    if (lower.contains("preview") || lower.contains("exp")) score -= 5
    return score
}

private fun groqModelScore(id: String): Int {
    val lower = id.lowercase()
    val idx = GROQ_PRIORITY.indexOfFirst { it.equals(lower, ignoreCase = true) }
    if (idx >= 0) return 1000 - idx * 50

    var score = 100
    score += when {
        lower.contains("gpt-oss-120b") -> 300
        lower.contains("llama-3.3-70b") -> 260
        lower.contains("gpt-oss-20b") -> 220
        lower.contains("llama-3.1-8b") -> 180
        lower.contains("qwen") || lower.contains("llama") -> 120
        else -> 0
    }
    return score
}

fun rankDiscoveredModels(providerId: String, rawIds: List<String>): List<String> {
    val unique = rawIds
        .map { it.removePrefix("models/").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return if (providerId == "gemini") {
        unique.filter(::isUsableGeminiTextModel).sortedByDescending(::geminiModelScore)
    } else {
        unique.filter(::isUsableGroqTextModel).sortedByDescending(::groqModelScore)
    }
}

fun isCompleteJsonObjectText(text: String): Boolean {
    val cleaned = text.trim()
        .removePrefix("\uFEFF")
        .replace(Regex("^```[a-zA-Z0-9_-]*\\s*"), "")
        .replace(Regex("\\s*```$"), "")
        .trim()
    val start = cleaned.indexOf('{')
    val last = cleaned.lastIndexOf('}')
    if (start < 0 || last <= start) return false

    fun validSlice(slice: String): Boolean = try {
        JSONObject(slice)
        true
    } catch (_: Throwable) {
        false
    }

    if (validSlice(cleaned.substring(start, last + 1))) return true

    var depth = 0
    var inString = false
    var escape = false
    for (i in start until cleaned.length) {
        val ch = cleaned[i]
        if (escape) {
            escape = false
            continue
        }
        if (ch == '\\' && inString) {
            escape = true
            continue
        }
        if (ch == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        if (ch == '{') depth += 1
        else if (ch == '}') {
            depth -= 1
            if (depth == 0) {
                return validSlice(cleaned.substring(start, i + 1))
            }
        }
    }
    return false
}

private fun httpError(status: Int): AiException = when {
    status == 400 -> AiException(
        "الطلب مرفوض. تحقق من المفتاح ومن اسم النموذج في الإعدادات.",
        status = 400,
    )
    status == 401 || status == 403 -> AiException(
        "المفتاح غير صالح أو لا يملك صلاحية ($status).",
        status = status,
    )
    status == 404 -> AiException(
        "النموذج غير موجود. غيّر اسم النموذج من الإعدادات.",
        modelProblem = true,
        status = 404,
    )
    status == 429 -> AiException(
        "تجاوزت حد الاستخدام أو الحصة (429). انتظر قليلا ثم حاول.",
        retryable = true,
        status = 429,
    )
    status == 503 || status >= 500 -> AiException(
        "خدمة المزود غير متاحة حاليا (رمز $status).",
        retryable = true,
        status = status,
    )
    else -> AiException("فشل الطلب (رمز $status).", status = status)
}

private fun timeoutMsFor(json: Boolean): Int =
    if (json) 45_000 else 25_000

private suspend fun getJson(
    urlString: String,
    headers: Map<String, String>,
    timeoutMs: Int = 10_000,
): Pair<Int, String> = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val url = URL(urlString)
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        status to text
    } catch (_: Throwable) {
        0 to ""
    } finally {
        conn?.disconnect()
    }
}

private suspend fun postJson(
    urlString: String,
    headers: Map<String, String>,
    body: JSONObject,
    timeoutMs: Int,
): Pair<Int, String> = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val url = URL(urlString)
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.outputStream.use { out ->
            out.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        status to text
    } catch (_: SocketTimeoutException) {
        throw AiException("انتهت مهلة الاتصال. حاول مرة أخرى.", retryable = true)
    } catch (_: IOException) {
        throw AiException("تعذر الاتصال بالخدمة. تحقق من الإنترنت.", retryable = true)
    } finally {
        conn?.disconnect()
    }
}

fun parseGeminiText(body: String): String {
    val data = JSONObject(body)
    val candidates = data.optJSONArray("candidates")
    if (candidates != null && candidates.length() > 0) {
        val first = candidates.optJSONObject(0)
        val content = first?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts != null) {
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                val t = p?.optString("text", "") ?: ""
                sb.append(t)
            }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) return text
        }
    }
    throw AiException("لم يرجع النموذج نصا. قد يكون المحتوى محجوبا.")
}

fun parseChatCompletionText(body: String): String {
    val data = JSONObject(body)
    val choices = data.optJSONArray("choices")
    if (choices != null && choices.length() > 0) {
        val first = choices.optJSONObject(0)
        val message = first?.optJSONObject("message")
        val content = message?.opt("content") as? String
        if (!content.isNullOrBlank()) return content.trim()
    }
    throw AiException("لم يرجع النموذج نصا.")
}

class GeminiProvider : AiProvider {
    override val id: String = "gemini"
    override val name: String = "Gemini"
    override val defaultModel: String = "gemini-4-hydrogen"
    override val fallbackModels: List<String> = listOf(
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash",
    )
    override val supportsSearch: Boolean = true

    override suspend fun discoverModels(apiKey: String): List<String> {
        val key = apiKey.trim()
        if (key.isEmpty()) return emptyList()
        val (status, respBody) = getJson(
            urlString = "https://generativelanguage.googleapis.com/v1beta/models",
            headers = mapOf("x-goog-api-key" to key),
        )
        if (status == 401 || status == 403) throw httpError(status)
        if (status !in 200..299 || respBody.isEmpty()) return emptyList()
        return try {
            val data = JSONObject(respBody)
            val arr = data.optJSONArray("models") ?: return emptyList()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val methods = m.optJSONArray("supportedGenerationMethods")
                var supportsGenerate = methods == null
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                }
                if (supportsGenerate) {
                    val name = m.optString("name", "")
                    if (name.isNotEmpty()) names.add(name)
                }
            }
            rankDiscoveredModels("gemini", names)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
        thinkingLevel: String,
    ): String {
        if (apiKey.trim().isEmpty()) {
            throw AiException("لا يوجد مفتاح. أضفه من الإعدادات.")
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val isGen3Or4 = model.startsWith("gemini-3") || model.startsWith("gemini-4")
        val resolvedThinking = if (thinkingLevel.equals("medium", ignoreCase = true)) "medium" else "low"

        fun buildBody(includeThinking: Boolean): JSONObject = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            if (search) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            put(
                "generationConfig",
                JSONObject().apply {
                    put("maxOutputTokens", if (json) GEMINI_ROUND_MAX_TOKENS else maxTokens)
                    if (json && !search) put("responseMimeType", "application/json")
                    if (json && !model.startsWith("gemini-4")) put("temperature", 0.9)
                    if (includeThinking && isGen3Or4) {
                        put("thinkingConfig", JSONObject().put("thinkingLevel", resolvedThinking))
                    }
                },
            )
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-goog-api-key" to apiKey.trim(),
        )
        val timeout = timeoutMsFor(json)

        var (status, respBody) = postJson(
            urlString = url,
            headers = headers,
            body = buildBody(isGen3Or4),
            timeoutMs = timeout,
        )
        if (status == 400 && isGen3Or4) {
            val retry = postJson(
                urlString = url,
                headers = headers,
                body = buildBody(false),
                timeoutMs = timeout,
            )
            status = retry.first
            respBody = retry.second
        }
        if (status != 200) throw httpError(status)
        return parseGeminiText(respBody)
    }
}

class GroqProvider : AiProvider {
    override val id: String = "groq"
    override val name: String = "Groq"
    override val defaultModel: String = "openai/gpt-oss-120b"
    override val fallbackModels: List<String> = listOf(
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "llama-3.1-8b-instant",
    )
    override val supportsSearch: Boolean = false

    override suspend fun discoverModels(apiKey: String): List<String> {
        val key = apiKey.trim()
        if (key.isEmpty()) return emptyList()
        val (status, respBody) = getJson(
            urlString = "https://api.groq.com/openai/v1/models",
            headers = mapOf("Authorization" to "Bearer $key"),
        )
        if (status == 401 || status == 403) throw httpError(status)
        if (status !in 200..299 || respBody.isEmpty()) return emptyList()
        return try {
            val data = JSONObject(respBody)
            val arr = data.optJSONArray("data") ?: return emptyList()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                if (m.optBoolean("active", true)) {
                    val id = m.optString("id", "")
                    if (id.isNotEmpty()) names.add(id)
                }
            }
            rankDiscoveredModels("groq", names)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
        thinkingLevel: String,
    ): String {
        if (apiKey.trim().isEmpty()) {
            throw AiException("لا يوجد مفتاح. أضفه من الإعدادات.")
        }
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
            put("max_tokens", if (json) GROQ_ROUND_MAX_TOKENS else maxTokens)
            if (json) put("response_format", JSONObject().put("type", "json_object"))
        }
        val (status, respBody) = postJson(
            urlString = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${apiKey.trim()}",
            ),
            body = body,
            timeoutMs = timeoutMsFor(json),
        )
        if (status != 200) throw httpError(status)
        return parseChatCompletionText(respBody)
    }
}
