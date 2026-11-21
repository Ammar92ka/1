package com.ottoman.empire1800.services

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.PlanFormatException
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.RoundPlan
import kotlinx.coroutines.delay

typealias AiCall = suspend (String) -> String
typealias AiDetailedCall = suspend (prompt: String, thinkingLevel: String, excludeModels: Set<String>) -> Pair<String, String>

/** يطلب من الذكاء الاصطناعي جولة، ويتحقق منها ويعايرها، وينتقل فورا لنموذج أسرع عند الخطأ الشكلي مع الإبقاء على التفكير المنخفض. */
class RoundGenerator(
    private val call: AiCall? = null,
    private val maxAttempts: Int = 4,
    private val maxFormatFailures: Int = 3,
    private val wait: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val onAttempt: ((attempt: Int, problem: String?) -> Unit)? = null,
    private val callDetailed: AiDetailedCall? = null,
) {
    suspend fun generate(s: GameState): RoundPlan {
        var problem: String? = null
        var lastAi: AiException? = null
        var formatFailures = 0
        val thinkingLevel = "low"
        val excludedModels = mutableSetOf<String>()
        var lastModel = ""

        for (attempt in 1..maxAttempts) {
            onAttempt?.invoke(attempt, problem)
            val prompt = buildRoundPrompt(s, problem = problem)
            var usedModel = ""
            try {
                val text = if (callDetailed != null) {
                    val (t, m) = callDetailed.invoke(prompt, thinkingLevel, excludedModels)
                    usedModel = m
                    if (m.isNotEmpty()) lastModel = m
                    t
                } else {
                    call?.invoke(prompt) ?: ""
                }
                val raw = RoundParser.parse(text)
                return Calibrator.calibrate(raw, s)
            } catch (e: PlanFormatException) {
                problem = e.message
                lastAi = null
                formatFailures += 1
                if (formatFailures >= maxFormatFailures) break
                if (usedModel.isNotEmpty()) excludedModels.add(usedModel)
            } catch (e: AiException) {
                if (!e.retryable) throw e
                lastAi = e
                if (attempt < maxAttempts) wait(1500L * attempt)
            }
        }
        if (lastAi != null) throw lastAi
        val diag = if (lastModel.isNotEmpty()) "النموذج: $lastModel · الحالة: invalid_round_json" else null
        throw AiException(
            message = "صيغة JSON غير صالحة أو مبتورة (${problem ?: "بيانات الجولة ناقصة"}).",
            diagnostic = diag,
        )
    }
}
