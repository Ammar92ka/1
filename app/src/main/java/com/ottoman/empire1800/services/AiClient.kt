package com.ottoman.empire1800.services

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** يرسل الطلب للمزود المختار، ويكتشف أفضل النماذج المتاحة تلقائيا ويجرب بدائل عند الضغط أو البتر. */
class AiClient(
    val provider: AiProvider,
    val apiKey: String,
    val models: List<String>,
    val search: Boolean = false,
    private val autoDiscover: Boolean = false,
    private val discoverFn: (suspend () -> List<String>)? = null,
) {
    init {
        require(models.isNotEmpty())
    }

    suspend fun generateJson(prompt: String): String = generateJsonWithModel(prompt).first

    suspend fun generateText(prompt: String): String = generateTextWithModel(prompt).first

    suspend fun generateJsonWithModel(
        prompt: String,
        thinkingLevel: String = "low",
        excludeModels: Set<String> = emptySet(),
    ): Pair<String, String> =
        run(prompt, json = true, search = search, thinkingLevel = thinkingLevel, excludeModels = excludeModels)

    suspend fun generateTextWithModel(prompt: String): Pair<String, String> =
        run(prompt, json = false, search = false, thinkingLevel = "low", excludeModels = emptySet())

    private suspend fun resolveCandidateModels(excludeModels: Set<String>): List<String> {
        val base = if (!autoDiscover) {
            models
        } else {
            val cacheKey = "${provider.id}:${sha256Short(apiKey.trim())}"
            val now = System.currentTimeMillis()
            val cached = discoveryCache[cacheKey]
            val discovered = if (cached != null && cached.expiresAt > now) {
                cached.models
            } else {
                val fresh = discoverFn?.invoke() ?: provider.discoverModels(apiKey)
                if (fresh.isNotEmpty()) {
                    discoveryCache[cacheKey] = CachedModels(fresh, now + CACHE_TTL_MS)
                }
                fresh
            }
            if (discovered.isEmpty()) {
                models
            } else {
                val merged = mutableListOf<String>()
                for (m in discovered + models) {
                    if (!merged.contains(m)) merged.add(m)
                }
                merged
            }
        }
        if (excludeModels.isEmpty()) return base
        val remaining = base.filterNot { excludeModels.contains(it) }
        return if (remaining.isNotEmpty()) remaining else base
    }

    private suspend fun run(
        prompt: String,
        json: Boolean,
        search: Boolean,
        thinkingLevel: String,
        excludeModels: Set<String>,
    ): Pair<String, String> {
        var last: AiException? = null
        var useSearch = search
        val candidates = resolveCandidateModels(excludeModels)
        for (model in candidates) {
            try {
                val text = once(prompt, model, json, useSearch, thinkingLevel)
                if (json && !isCompleteJsonObjectText(text)) {
                    last = AiException(
                        message = "صيغة JSON غير صالحة أو مبتورة.",
                        retryable = true,
                        status = 502,
                        diagnostic = "النموذج: $model · الحالة: truncated_json",
                    )
                    continue
                }
                return text to model
            } catch (e: AiException) {
                if (useSearch && (e.status == 400 || e.status == 429)) {
                    useSearch = false
                    try {
                        val retryText = once(prompt, model, json, false, thinkingLevel)
                        if (json && !isCompleteJsonObjectText(retryText)) {
                            last = AiException(
                                message = "صيغة JSON غير صالحة أو مبتورة.",
                                retryable = true,
                                status = 502,
                                diagnostic = "النموذج: $model · الحالة: truncated_json",
                            )
                            continue
                        }
                        return retryText to model
                    } catch (e2: AiException) {
                        val withDiag = AiException(
                            message = e2.message,
                            retryable = e2.retryable,
                            modelProblem = e2.modelProblem,
                            status = e2.status,
                            diagnostic = e2.diagnostic ?: "النموذج: $model · الحالة: ${e2.status ?: "error"}",
                        )
                        last = withDiag
                        if (!withDiag.retryable && !withDiag.modelProblem) throw withDiag
                        continue
                    }
                }
                val withDiag = AiException(
                    message = e.message,
                    retryable = e.retryable,
                    modelProblem = e.modelProblem,
                    status = e.status,
                    diagnostic = e.diagnostic ?: "النموذج: $model · الحالة: ${e.status ?: "error"}",
                )
                last = withDiag
                if (!withDiag.retryable && !withDiag.modelProblem) throw withDiag
            }
        }
        throw last ?: AiException("تعذر الاتصال بالذكاء الاصطناعي.")
    }

    private suspend fun once(
        prompt: String,
        model: String,
        json: Boolean,
        useSearch: Boolean,
        thinkingLevel: String,
    ): String =
        provider.generate(
            prompt = prompt,
            apiKey = apiKey,
            model = model,
            json = json,
            search = useSearch,
            thinkingLevel = thinkingLevel,
        )

    private data class CachedModels(val models: List<String>, val expiresAt: Long)

    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
        private val discoveryCache = ConcurrentHashMap<String, CachedModels>()

        fun clearDiscoveryCache() {
            discoveryCache.clear()
        }

        private fun sha256Short(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

        fun fromSettings(s: AiSettings): AiClient {
            val provider = aiProviderById(s.provider)
            val custom = s.model.trim()
            val models = mutableListOf<String>()
            if (custom.isNotEmpty()) models.add(custom)
            for (m in listOf(provider.defaultModel) + provider.fallbackModels) {
                if (!models.contains(m)) models.add(m)
            }
            return AiClient(
                provider = provider,
                apiKey = s.apiKey,
                models = models,
                search = s.useSearch && provider.supportsSearch,
                autoDiscover = custom.isEmpty(),
            )
        }
    }
}
